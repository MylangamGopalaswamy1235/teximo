package com.teximo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

/**
 * Stores RapidAPI / OpenRouter keys and MySQL credentials encrypted (AES-256-GCM) on disk.
 * Values are only ever read by the backend. No method here is exposed to the frontend with the real value.
 */
@Service
public class SecretStore {
    public static final String RAPIDAPI_KEY = "rapidapi.key";
    public static final String RAPIDAPI_JOB_SEARCH_KEY = "rapidapi.job_search.key";
    public static final String RAPIDAPI_JOB_SALARY_KEY = "rapidapi.job_salary.key";
    public static final String RAPIDAPI_COMPANY_JOB_SEARCH_KEY = "rapidapi.company_job_search.key";
    public static final String RAPIDAPI_JOB_DETAILS_KEY = "rapidapi.job_details.key";
    public static final String RAPIDAPI_JOB_SEARCH_URL = "rapidapi.job_search.url";
    public static final String RAPIDAPI_JOB_DETAILS_URL = "rapidapi.job_details.url";
    public static final String RAPIDAPI_JOB_SALARY_URL = "rapidapi.job_salary.url";
    public static final String RAPIDAPI_COMPANY_JOB_SALARY_URL = "rapidapi.company_job_salary.url";
    public static final String OPENROUTER_KEY = "openrouter.key";
    public static final String OPENROUTER_MODEL = "openrouter.model";
    public static final String DB_URL = "db.url";
    public static final String DB_USER = "db.username";
    public static final String DB_PASS = "db.password";

    private final Path file;
    private final String masterKey;
    private final Map<String, String> values = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public SecretStore(@Value("${teximo.secrets-file}") String path,
                       @Value("${teximo.master-key}") String masterKey) {
        this.file = Paths.get(path);
        this.masterKey = masterKey;
        load();
    }

    public synchronized String get(String key) {
        String v = values.get(key);
        if (v != null && !v.isBlank()) return v;
        // fall back to environment variables (e.g. RAPIDAPI_KEY, OPENROUTER_KEY)
        return System.getenv(key.toUpperCase().replace('.', '_'));
    }

    public synchronized boolean isSet(String key) {
        String v = get(key);
        return v != null && !v.isBlank();
    }

    /** Blank values are ignored so a masked field left empty keeps the existing secret. */
    public synchronized void putAll(Map<String, String> updates) {
        updates.forEach((k, v) -> { if (v != null && !v.isBlank()) values.put(k, v.trim()); });
        persist();
    }

    // ---------- encryption ----------
    private void persist() {
        try {
            StringBuilder sb = new StringBuilder();
            values.forEach((k, v) -> sb.append(k).append('=')
                    .append(Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8))).append('\n'));
            byte[] salt = new byte[16]; random.nextBytes(salt);
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[16 + 12 + enc.length];
            System.arraycopy(salt, 0, out, 0, 16);
            System.arraycopy(iv, 0, out, 16, 12);
            System.arraycopy(enc, 0, out, 28, enc.length);
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.write(file, out);
            try { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")); } catch (Exception ignored) {}
        } catch (Exception e) {
            throw new IllegalStateException("Could not save secrets", e);
        }
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            byte[] in = Files.readAllBytes(file);
            byte[] salt = Arrays.copyOfRange(in, 0, 16);
            byte[] iv = Arrays.copyOfRange(in, 16, 28);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, deriveKey(salt), new GCMParameterSpec(128, iv));
            String text = new String(c.doFinal(in, 28, in.length - 28), StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                int i = line.indexOf('=');
                if (i > 0) values.put(line.substring(0, i),
                        new String(Base64.getDecoder().decode(line.substring(i + 1)), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            System.err.println("Secrets file could not be decrypted (wrong TEXIMO_MASTER_KEY?). Starting empty.");
        }
    }

    private SecretKeySpec deriveKey(byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = f.generateSecret(new PBEKeySpec(masterKey.toCharArray(), salt, 120_000, 256)).getEncoded();
        return new SecretKeySpec(key, "AES");
    }
}
