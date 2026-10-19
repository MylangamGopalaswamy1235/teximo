package com.teximo.controller;

import com.teximo.model.*;
import com.teximo.repo.*;
import com.teximo.security.RequireRole;
import com.teximo.service.SkillMatcher;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/student")
@RequireRole(Role.STUDENT)
public class StudentController {
    private final UserRepository users;
    private final DriveRepository drives;
    private final ApplicationRepository applications;
    private final InterviewRepository interviews;
    private final NotificationRepository notifications;
    private final ComplaintRepository complaints;

    public StudentController(UserRepository users, DriveRepository drives, ApplicationRepository applications,
                             InterviewRepository interviews, NotificationRepository notifications,
                             ComplaintRepository complaints) {
        this.users = users; this.drives = drives; this.applications = applications;
        this.interviews = interviews; this.notifications = notifications; this.complaints = complaints;
    }

    // ---------- profile ----------
    @GetMapping("/profile")
    public User profile(@RequestAttribute("userId") Long id) {
        return users.findById(id).orElseThrow();
    }

    @PutMapping("/profile")
    public User updateProfile(@RequestAttribute("userId") Long id, @RequestBody Map<String, Object> b) {
        User u = users.findById(id).orElseThrow();
        if (b.containsKey("name")) u.setName(s(b, "name"));
        u.setPhone(s(b, "phone"));
        u.setCollege(s(b, "college"));
        u.setDegree(s(b, "degree"));
        u.setBranch(s(b, "branch"));
        u.setEducation(s(b, "education"));
        u.setSkills(s(b, "skills"));
        u.setLinkedinUrl(s(b, "linkedinUrl"));
        u.setGraduationYear(intOrNull(b.get("graduationYear")));
        u.setCgpa(dblOrNull(b.get("cgpa")));
        Integer backlogs = intOrNull(b.get("backlogs"));
        u.setBacklogs(backlogs == null ? 0 : backlogs);
        if (u.getCgpa() != null && (u.getCgpa() < 0 || u.getCgpa() > 10))
            throw new IllegalArgumentException("CGPA must be between 0 and 10.");
        return users.save(u);
    }

    // ---------- drives & eligibility ----------
    @GetMapping("/drives")
    public List<Map<String, Object>> drives(@RequestAttribute("userId") Long id) {
        User u = users.findById(id).orElseThrow();
        Set<Long> applied = new HashSet<>();
        applications.findByStudentIdOrderByAppliedAtDesc(id).forEach(a -> applied.add(a.getDriveId()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Drive d : drives.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("drive", d);
            List<String> reasons = ineligibleReasons(u, d);
            m.put("eligible", reasons.isEmpty());
            m.put("reasons", reasons);
            m.put("applied", applied.contains(d.getId()));
            Set<String> need = SkillMatcher.parse(d.getRequiredSkills());
            Set<String> have = SkillMatcher.parse(u.getSkills());
            List<String> missing = new ArrayList<>();
            for (String n : need) if (!have.contains(n)) missing.add(n);
            m.put("missingSkills", missing);
            out.add(m);
        }
        return out;
    }

    /** Applies to an internal college drive only. Nothing is ever sent to outside job sites. */
    @PostMapping("/drives/{driveId}/apply")
    public Application apply(@RequestAttribute("userId") Long id, @PathVariable Long driveId) {
        User u = users.findById(id).orElseThrow();
        Drive d = drives.findById(driveId).orElseThrow(() -> new IllegalArgumentException("Drive not found."));
        if (d.getLastDateToApply() != null && d.getLastDateToApply().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("The last date to apply has passed.");
        List<String> reasons = ineligibleReasons(u, d);
        if (!reasons.isEmpty()) throw new IllegalArgumentException("Not eligible: " + String.join("; ", reasons));
        if (applications.existsByStudentIdAndDriveId(id, driveId))
            throw new IllegalArgumentException("You have already applied to this drive.");
        Application a = new Application();
        a.setStudentId(id); a.setStudentName(u.getName());
        a.setDriveId(driveId); a.setDriveTitle(d.getTitle()); a.setCompanyName(d.getCompanyName());
        return applications.save(a);
    }

    @GetMapping("/applications")
    public List<Application> myApplications(@RequestAttribute("userId") Long id) {
        return applications.findByStudentIdOrderByAppliedAtDesc(id);
    }

    @GetMapping("/interviews")
    public List<Interview> myInterviews(@RequestAttribute("userId") Long id) {
        return interviews.findByStudentIdOrderByScheduledAtAsc(id);
    }

    // ---------- notifications ----------
    @GetMapping("/notifications")
    public List<Notification> myNotifications(@RequestAttribute("userId") Long id) {
        return notifications.findByStudentIdOrderByCreatedAtDesc(id);
    }

    @PostMapping("/notifications/read")
    public Map<String, Object> markRead(@RequestAttribute("userId") Long id) {
        List<Notification> list = notifications.findByStudentIdOrderByCreatedAtDesc(id);
        list.forEach(n -> n.setRead(true));
        notifications.saveAll(list);
        return Map.of("ok", true);
    }

    // ---------- complaints ----------
    @GetMapping("/complaints")
    public List<Complaint> myComplaints(@RequestAttribute("userId") Long id) {
        return complaints.findByStudentIdOrderByCreatedAtDesc(id);
    }

    @PostMapping("/complaints")
    public Complaint submit(@RequestAttribute("userId") Long id, @RequestBody Map<String, String> b) {
        String subject = AuthController.str(b, "subject"), message = AuthController.str(b, "message");
        if (subject.isBlank() || message.isBlank()) throw new IllegalArgumentException("Please fill in the subject and message.");
        User u = users.findById(id).orElseThrow();
        Complaint c = new Complaint();
        c.setStudentId(id); c.setStudentName(u.getName()); c.setSubject(subject); c.setMessage(message);
        return complaints.save(c);
    }

    // ---------- helpers ----------
    static List<String> ineligibleReasons(User u, Drive d) {
        List<String> r = new ArrayList<>();
        if (d.getMinCgpa() != null && d.getMinCgpa() > 0 && (u.getCgpa() == null || u.getCgpa() < d.getMinCgpa()))
            r.add("Minimum CGPA " + d.getMinCgpa());
        if (d.getMaxBacklogs() != null && u.getBacklogs() != null && u.getBacklogs() > d.getMaxBacklogs())
            r.add("Maximum " + d.getMaxBacklogs() + " backlog(s) allowed");
        if (d.getAllowedBranches() != null && !d.getAllowedBranches().isBlank()) {
            Set<String> allowed = SkillMatcher.parse(d.getAllowedBranches());
            if (u.getBranch() == null || !allowed.contains(u.getBranch().trim().toLowerCase()))
                r.add("Open to branches: " + d.getAllowedBranches());
        }
        if (d.getGraduationYear() != null && !d.getGraduationYear().equals(u.getGraduationYear()))
            r.add("Graduation year " + d.getGraduationYear() + " only");
        return r;
    }

    private static String s(Map<String, Object> b, String k) {
        Object v = b.get(k);
        return v == null ? "" : v.toString().trim();
    }
    private static Integer intOrNull(Object o) {
        try { return o == null || o.toString().isBlank() ? null : Integer.valueOf(o.toString().trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Please enter a valid number."); }
    }
    private static Double dblOrNull(Object o) {
        try { return o == null || o.toString().isBlank() ? null : Double.valueOf(o.toString().trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Please enter a valid CGPA."); }
    }
}
