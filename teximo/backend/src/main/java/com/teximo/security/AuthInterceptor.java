package com.teximo.security;

import com.teximo.model.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Checks the Bearer token and the @RequireRole annotation on every controller call. */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokens;

    public AuthInterceptor(TokenService tokens) { this.tokens = tokens; }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) return true;

        RequireRole rule = hm.getMethodAnnotation(RequireRole.class);
        if (rule == null) rule = hm.getBeanType().getAnnotation(RequireRole.class);
        if (rule == null) return true; // public endpoint (login / register)

        String header = req.getHeader("Authorization");
        String[] claims = (header != null && header.startsWith("Bearer ")) ? tokens.verify(header.substring(7)) : null;
        if (claims == null) { deny(res, 401, "Please log in again."); return false; }
        if (Role.valueOf(claims[1]) != rule.value()) { deny(res, 403, "You do not have access to this area."); return false; }

        req.setAttribute("userId", Long.parseLong(claims[0]));
        return true;
    }

    private void deny(HttpServletResponse res, int code, String msg) throws Exception {
        res.setStatus(code);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
