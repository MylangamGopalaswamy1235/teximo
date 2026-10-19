package com.teximo.controller;

import com.teximo.model.*;
import com.teximo.repo.*;
import com.teximo.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequireRole(Role.ADMIN)
public class AdminController {
    private final UserRepository users;
    private final CompanyRepository companies;
    private final DriveRepository drives;
    private final ApplicationRepository applications;
    private final InterviewRepository interviews;
    private final ComplaintRepository complaints;
    private final NotificationRepository notifications;

    public AdminController(UserRepository users, CompanyRepository companies, DriveRepository drives,
                           ApplicationRepository applications, InterviewRepository interviews,
                           ComplaintRepository complaints, NotificationRepository notifications) {
        this.users = users; this.companies = companies; this.drives = drives; this.applications = applications;
        this.interviews = interviews; this.complaints = complaints; this.notifications = notifications;
    }

    // ---------- students ----------
    @GetMapping("/students")
    public List<User> students() { return users.findByRole(Role.STUDENT); }

    @PutMapping("/students/{id}/status")
    public User setPlacementStatus(@PathVariable Long id, @RequestBody Map<String, String> b) {
        User u = users.findById(id).orElseThrow();
        String status = AuthController.str(b, "status");
        if (!List.of("NOT_PLACED", "IN_PROCESS", "PLACED").contains(status)) throw new IllegalArgumentException("Invalid status.");
        u.setPlacementStatus(status);
        return users.save(u);
    }

    @DeleteMapping("/students/{id}")
    public Map<String, Object> deleteStudent(@PathVariable Long id) {
        User u = users.findById(id).orElseThrow();
        if (u.getRole() != Role.STUDENT) throw new IllegalArgumentException("Only student records can be removed here.");
        users.delete(u);
        return Map.of("ok", true);
    }

    // ---------- companies ----------
    @GetMapping("/companies")
    public List<Company> companies() { return companies.findAll(); }

    @PostMapping("/companies")
    public Company addCompany(@RequestBody Company c) {
        if (c.getName() == null || c.getName().isBlank()) throw new IllegalArgumentException("Company name is required.");
        return companies.save(c);
    }

    @PutMapping("/companies/{id}")
    public Company updateCompany(@PathVariable Long id, @RequestBody Company in) {
        Company c = companies.findById(id).orElseThrow();
        c.setName(in.getName()); c.setIndustry(in.getIndustry()); c.setWebsite(in.getWebsite());
        c.setLocation(in.getLocation()); c.setDescription(in.getDescription());
        return companies.save(c);
    }

    @DeleteMapping("/companies/{id}")
    public Map<String, Object> deleteCompany(@PathVariable Long id) {
        companies.deleteById(id);
        return Map.of("ok", true);
    }

    // ---------- drives ----------
    @GetMapping("/drives")
    public List<Drive> drives() { return drives.findAll(); }

    @PostMapping("/drives")
    public Drive addDrive(@RequestBody Drive d) {
        Company c = companies.findById(d.getCompanyId() == null ? -1L : d.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Please choose a company."));
        if (d.getTitle() == null || d.getTitle().isBlank()) throw new IllegalArgumentException("Drive title is required.");
        d.setCompanyName(c.getName());
        Drive saved = drives.save(d);
        // tell eligible students
        for (User s : users.findByRole(Role.STUDENT)) {
            if (StudentController.ineligibleReasons(s, saved).isEmpty())
                notifications.save(new Notification(s.getId(), "New drive: " + saved.getCompanyName(),
                        saved.getTitle() + (saved.getLastDateToApply() == null ? "" : " — apply by " + saved.getLastDateToApply())));
        }
        return saved;
    }

    @PutMapping("/drives/{id}")
    public Drive updateDrive(@PathVariable Long id, @RequestBody Drive in) {
        Drive d = drives.findById(id).orElseThrow();
        d.setTitle(in.getTitle()); d.setType(in.getType()); d.setDescription(in.getDescription()); d.setCtc(in.getCtc());
        d.setDriveDate(in.getDriveDate()); d.setLastDateToApply(in.getLastDateToApply());
        d.setMinCgpa(in.getMinCgpa()); d.setMaxBacklogs(in.getMaxBacklogs()); d.setAllowedBranches(in.getAllowedBranches());
        d.setGraduationYear(in.getGraduationYear()); d.setRequiredSkills(in.getRequiredSkills());
        return drives.save(d);
    }

    @DeleteMapping("/drives/{id}")
    public Map<String, Object> deleteDrive(@PathVariable Long id) {
        drives.deleteById(id);
        return Map.of("ok", true);
    }

    // ---------- applications ----------
    @GetMapping("/applications")
    public List<Application> applications(@RequestParam(required = false) Long driveId) {
        return driveId == null ? applications.findAll() : applications.findByDriveId(driveId);
    }

    @PutMapping("/applications/{id}/status")
    public Application setApplicationStatus(@PathVariable Long id, @RequestBody Map<String, String> b) {
        Application a = applications.findById(id).orElseThrow();
        String status = AuthController.str(b, "status");
        if (!List.of("APPLIED", "SHORTLISTED", "INTERVIEW", "SELECTED", "REJECTED").contains(status))
            throw new IllegalArgumentException("Invalid status.");
        boolean wasShortlisted = "SHORTLISTED".equals(a.getStatus());
        a.setStatus(status);
        applications.save(a);
        if ("SHORTLISTED".equals(status) && !wasShortlisted
                && !interviews.existsByStudentIdAndDriveId(a.getStudentId(), a.getDriveId())) {
            Interview interview = new Interview();
            interview.setDriveId(a.getDriveId());
            interview.setDriveTitle(a.getCompanyName() + " — " + a.getDriveTitle());
            interview.setStudentId(a.getStudentId());
            interview.setStudentName(a.getStudentName());
            interview.setScheduledAt(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(7).atTime(10, 0));
            interview.setRound("Round 1");
            interview.setMode("ONLINE");
            interview.setVenueOrLink("");
            interviews.save(interview);
            notifications.save(new Notification(a.getStudentId(), "Interview scheduled",
                    interview.getRound() + " round for " + a.getCompanyName() + " on "
                            + interview.getScheduledAt().toString().replace('T', ' ')
                            + " (" + interview.getMode() + ")."));
        }
        notifications.save(new Notification(a.getStudentId(), "Application update",
                "Your application for " + a.getDriveTitle() + " at " + a.getCompanyName() + " is now " + status + "."));
        if ("SELECTED".equals(status)) {
            users.findById(a.getStudentId()).ifPresent(u -> { u.setPlacementStatus("PLACED"); users.save(u); });
        } else if ("SHORTLISTED".equals(status) || "INTERVIEW".equals(status)) {
            users.findById(a.getStudentId()).ifPresent(u -> {
                if ("NOT_PLACED".equals(u.getPlacementStatus())) { u.setPlacementStatus("IN_PROCESS"); users.save(u); }
            });
        }
        return a;
    }

    // ---------- interviews ----------
    @GetMapping("/interviews")
    public List<Interview> interviews() { return interviews.findAll(); }

    @PostMapping("/interviews")
    public Interview schedule(@RequestBody Map<String, String> b) {
        Long driveId = Long.valueOf(AuthController.str(b, "driveId"));
        Long studentId = Long.valueOf(AuthController.str(b, "studentId"));
        Drive d = drives.findById(driveId).orElseThrow(() -> new IllegalArgumentException("Drive not found."));
        User s = users.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found."));
        Interview i = new Interview();
        i.setDriveId(driveId); i.setDriveTitle(d.getCompanyName() + " — " + d.getTitle());
        i.setStudentId(studentId); i.setStudentName(s.getName());
        i.setScheduledAt(LocalDateTime.parse(AuthController.str(b, "scheduledAt")));
        i.setMode(AuthController.str(b, "mode").isBlank() ? "ONLINE" : AuthController.str(b, "mode"));
        i.setVenueOrLink(AuthController.str(b, "venueOrLink"));
        i.setRound(AuthController.str(b, "round").isBlank() ? "Technical" : AuthController.str(b, "round"));
        interviews.save(i);
        notifications.save(new Notification(studentId, "Interview scheduled",
                i.getRound() + " round for " + d.getCompanyName() + " on " + i.getScheduledAt().toString().replace('T', ' ')
                        + " (" + i.getMode() + ")."));
        return i;
    }

    @DeleteMapping("/interviews/{id}")
    public Map<String, Object> cancelInterview(@PathVariable Long id) {
        interviews.deleteById(id);
        return Map.of("ok", true);
    }

    // ---------- complaints ----------
    @GetMapping("/complaints")
    public List<Complaint> complaints() {
        List<Complaint> all = complaints.findAll();
        all.sort(Comparator.comparing((Complaint complaint) -> complaint.getCreatedAt()).reversed());
        return all;
    }

    @PutMapping("/complaints/{id}")
    public Complaint resolve(@PathVariable Long id, @RequestBody Map<String, String> b) {
        Complaint c = complaints.findById(id).orElseThrow();
        String status = AuthController.str(b, "status");
        if (!List.of("OPEN", "IN_REVIEW", "RESOLVED").contains(status)) throw new IllegalArgumentException("Invalid status.");
        c.setStatus(status);
        c.setAdminResponse(AuthController.str(b, "adminResponse"));
        c.touch();
        complaints.save(c);
        notifications.save(new Notification(c.getStudentId(), "Complaint " + status.toLowerCase().replace('_', ' '),
                "Your complaint \"" + c.getSubject() + "\" was updated."));
        return c;
    }

    // ---------- statistics ----------
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<User> students = users.findByRole(Role.STUDENT);
        long placed = students.stream().filter(s -> "PLACED".equals(s.getPlacementStatus())).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("students", students.size());
        m.put("placed", placed);
        m.put("inProcess", students.stream().filter(s -> "IN_PROCESS".equals(s.getPlacementStatus())).count());
        m.put("placementRate", students.isEmpty() ? 0 : Math.round(100.0 * placed / students.size()));
        m.put("companies", companies.count());
        m.put("drives", drives.count());
        m.put("applications", applications.count());
        m.put("openComplaints", complaints.findAll().stream().filter(c -> !"RESOLVED".equals(c.getStatus())).count());
        m.put("byBranch", students.stream().collect(Collectors.groupingBy(
                s -> s.getBranch() == null || s.getBranch().isBlank() ? "Unknown" : s.getBranch(),
                TreeMap::new, Collectors.counting())));
        m.put("placedByBranch", students.stream().filter(s -> "PLACED".equals(s.getPlacementStatus())).collect(Collectors.groupingBy(
                s -> s.getBranch() == null || s.getBranch().isBlank() ? "Unknown" : s.getBranch(),
                TreeMap::new, Collectors.counting())));
        m.put("applicationsByStatus", applications.findAll().stream()
                .collect(Collectors.groupingBy(application -> application.getStatus(), TreeMap::new, Collectors.counting())));
        return m;
    }
}
