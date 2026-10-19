package com.teximo.controller;

import com.teximo.model.Role;
import com.teximo.model.User;
import com.teximo.repo.UserRepository;
import com.teximo.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public AuthController(UserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users; this.encoder = encoder; this.tokens = tokens;
    }

    /** Students register themselves. Admin accounts are never created through this endpoint. */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String name = str(body, "name"), email = str(body, "email").toLowerCase(), pw = str(body, "password");
        if (name.isBlank()) throw new IllegalArgumentException("Please enter your name.");
        if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("Please enter a valid email.");
        if (pw.length() < 8) throw new IllegalArgumentException("Password must be at least 8 characters.");
        if (users.existsByEmail(email)) throw new IllegalArgumentException("This email is already registered.");

        User u = new User();
        u.setName(name); u.setEmail(email); u.setPasswordHash(encoder.encode(pw)); u.setRole(Role.STUDENT);
        u.setCollege(str(body, "college")); u.setBranch(str(body, "branch"));
        users.save(u);
        return session(u);
    }

    /** "portal" is student or admin; the account's real role must match the portal being used. */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String email = str(body, "email").toLowerCase(), pw = str(body, "password");
        String portal = str(body, "portal").toUpperCase();
        User u = users.findByEmail(email).orElse(null);
        if (u == null || !encoder.matches(pw, u.getPasswordHash()))
            throw new IllegalArgumentException("Incorrect email or password.");
        if (!portal.isBlank() && !u.getRole().name().equals(portal))
            throw new IllegalArgumentException(u.getRole() == Role.ADMIN
                    ? "This is an admin account. Please use Admin Login."
                    : "This is a student account. Please use Student Login.");
        return session(u);
    }

    private Map<String, Object> session(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", tokens.issue(u.getId(), u.getRole().name()));
        m.put("role", u.getRole().name());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        return m;
    }

    static String str(Map<String, String> m, String k) {
        String v = m.get(k);
        return v == null ? "" : v.trim();
    }
}
