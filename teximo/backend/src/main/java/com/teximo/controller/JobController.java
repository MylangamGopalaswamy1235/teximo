package com.teximo.controller;

import com.teximo.model.Role;
import com.teximo.model.User;
import com.teximo.repo.UserRepository;
import com.teximo.security.RequireRole;
import com.teximo.service.JSearchService;
import com.teximo.service.SkillMatcher;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/jobs")
@RequireRole(Role.STUDENT)
public class JobController {
    private final JSearchService jsearch;
    private final UserRepository users;

    public JobController(JSearchService jsearch, UserRepository users) {
        this.jsearch = jsearch; this.users = users;
    }

    /** Search/filter. Each result carries a match score and skill gaps for this student. */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestAttribute("userId") Long id,
                                            @RequestParam(defaultValue = "") String q,
                                            @RequestParam(defaultValue = "") String location,
                                            @RequestParam(defaultValue = "") String type,
                                            @RequestParam(defaultValue = "1") int page) {
        User u = users.findById(id).orElseThrow();
        List<Map<String, Object>> jobs = jsearch.search(q, location, type, page);
        enrich(jobs, studentSkills(u));
        return jobs;
    }

    /** Recommendations based on the student's skills (profile + resume), ranked by match. */
    @GetMapping("/recommend")
    public List<Map<String, Object>> recommend(@RequestAttribute("userId") Long id,
                                               @RequestParam(defaultValue = "") String type) {
        User u = users.findById(id).orElseThrow();
        Set<String> skills = studentSkills(u);
        if (skills.isEmpty())
            throw new IllegalArgumentException("Add skills to your profile or upload a resume to get recommendations.");
        String query = String.join(" ", skills.stream().limit(3).toList()) + (u.getBranch() == null ? "" : " " + u.getBranch());
        List<Map<String, Object>> jobs = jsearch.search(query.trim(), "", type, 1);
        enrich(jobs, skills);
        jobs.sort((a, b) -> Integer.compare((int) b.get("matchScore"), (int) a.get("matchScore")));
        return jobs;
    }

    private Set<String> studentSkills(User u) {
        Set<String> s = SkillMatcher.parse(u.getSkills());
        s.addAll(SkillMatcher.extract(u.getResumeText()));
        return s;
    }

    private void enrich(List<Map<String, Object>> jobs, Set<String> have) {
        for (Map<String, Object> j : jobs) {
            String text = j.get("title") + " " + j.get("description");
            Set<String> need = SkillMatcher.extract(text);
            List<String> missing = new ArrayList<>();
            for (String n : need) if (!have.contains(n)) missing.add(n);
            j.put("matchScore", SkillMatcher.score(have, need));
            j.put("missingSkills", missing);
            j.put("suggestion", missing.isEmpty()
                    ? "Your skills cover this role well. Tailor your resume to its keywords."
                    : "Consider learning: " + String.join(", ", missing.stream().limit(5).toList()) + ".");
        }
    }
}
