package com.teximo.controller;

import com.teximo.model.Role;
import com.teximo.model.User;
import com.teximo.repo.UserRepository;
import com.teximo.security.RequireRole;
import com.teximo.service.AiService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
@RequireRole(Role.STUDENT)
public class ChatController {
    private static final String SYSTEM = """
            You are Teximo's friendly personal assistant. Help college students with career guidance, \
            placements, resumes, interview preparation, aptitude and coding practice, and job-search questions. \
            Be concise, practical and encouraging. If asked about something unrelated to careers or studies, politely steer back. \
            Never claim to apply to jobs or post anything on LinkedIn, Naukri or other sites for the student; \
            the student always applies by themselves.""";

    private final AiService ai;
    private final UserRepository users;

    public ChatController(AiService ai, UserRepository users) { this.ai = ai; this.users = users; }

    @PostMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(@RequestAttribute("userId") Long id, @RequestBody Map<String, Object> body) {
        User u = users.findById(id).orElseThrow();
        List<Map<String, String>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", SYSTEM + "\nStudent: " + u.getName()
                + (u.getBranch() == null ? "" : ", branch " + u.getBranch())
                + (u.getSkills() == null || u.getSkills().isBlank() ? "" : ", skills: " + u.getSkills())));

        Object history = body.get("history");
        if (history instanceof List<?> list) {
            int from = Math.max(0, list.size() - 10);                       // keep the last 10 turns only
            for (Object o : list.subList(from, list.size())) {
                Map<String, Object> m = (Map<String, Object>) o;
                String role = "assistant".equals(m.get("role")) ? "assistant" : "user";
                msgs.add(Map.of("role", role, "content", String.valueOf(m.get("content"))));
            }
        }
        String message = String.valueOf(body.getOrDefault("message", "")).trim();
        if (message.isBlank()) throw new IllegalArgumentException("Please type a message.");
        if (message.length() > 2000) throw new IllegalArgumentException("Message is too long.");
        msgs.add(Map.of("role", "user", "content", message));
        return Map.of("reply", ai.chat(msgs));
    }
}
