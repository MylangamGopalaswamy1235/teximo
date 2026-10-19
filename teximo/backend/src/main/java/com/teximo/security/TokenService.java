package com.teximo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** Minimal HMAC-SHA256 signed token: base64url(userId.role.expiryEpoch).signature */
@Service
public class TokenService {
    private final byte[] secret;
    private static final long TTL_SECONDS = 8 * 3600;

    public TokenService(@Value("${teximo.token-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(Long userId, String role) {
        long exp = System.currentTimeMillis() / 1000 + TTL_SECONDS;
        String payload = userId + "." + role + "." + exp;
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + sign(body);
    }

    /** Returns {userId, role} or null if invalid/expired. */
    public String[] verify(String token) {
        try {
            int dot = token.lastIndexOf('.');
            if (dot < 0) return null;
            String body = token.substring(0, dot);
            String sig = token.substring(dot + 1);
            if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) return null;
            String[] parts = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8).split("\\.");
            if (parts.length != 3 || Long.parseLong(parts[2]) < System.currentTimeMillis() / 1000) return null;
            return new String[]{parts[0], parts[1]};
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
