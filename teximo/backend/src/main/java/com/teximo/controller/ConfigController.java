package com.teximo.controller;

import com.teximo.model.Role;
import com.teximo.security.RequireRole;
import com.teximo.service.AiService;
import com.teximo.service.JSearchService;
import com.teximo.service.SecretStore;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-only API / database configuration.
 * IMPORTANT: GET returns only "is it set?" booleans. The real secret values are never sent back to the browser.
 */
@RestController
@RequestMapping("/api/admin/config")
@RequireRole(Role.ADMIN)
public class ConfigController {
    private final SecretStore secrets;
    private final AiService ai;
    private final JSearchService jsearch;

    public ConfigController(SecretStore secrets, AiService ai, JSearchService jsearch) {
        this.secrets = secrets; this.ai = ai; this.jsearch = jsearch;
    }

    @GetMapping
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean jobSearchKeySet = secrets.isSet(SecretStore.RAPIDAPI_JOB_SEARCH_KEY)
                || secrets.isSet(SecretStore.RAPIDAPI_KEY);
        m.put("rapidApiKeySet", jobSearchKeySet);
        m.put("rapidApiJobSearchKeySet", jobSearchKeySet);
        m.put("rapidApiJobSalaryKeySet", secrets.isSet(SecretStore.RAPIDAPI_JOB_SALARY_KEY));
        m.put("rapidApiCompanyJobSearchKeySet", secrets.isSet(SecretStore.RAPIDAPI_COMPANY_JOB_SEARCH_KEY));
        m.put("rapidApiJobDetailsKeySet", secrets.isSet(SecretStore.RAPIDAPI_JOB_DETAILS_KEY));
        m.put("rapidApiJobSearchUrl", orEmpty(secrets.get(SecretStore.RAPIDAPI_JOB_SEARCH_URL)));
        m.put("rapidApiJobDetailsUrl", orEmpty(secrets.get(SecretStore.RAPIDAPI_JOB_DETAILS_URL)));
        m.put("rapidApiJobSalaryUrl", orEmpty(secrets.get(SecretStore.RAPIDAPI_JOB_SALARY_URL)));
        m.put("rapidApiCompanyJobSalaryUrl", orEmpty(secrets.get(SecretStore.RAPIDAPI_COMPANY_JOB_SALARY_URL)));
        m.put("openRouterKeySet", secrets.isSet(SecretStore.OPENROUTER_KEY));
        m.put("openRouterModel", orEmpty(secrets.get(SecretStore.OPENROUTER_MODEL)));   // not a secret
        m.put("dbUrlSet", secrets.isSet(SecretStore.DB_URL));
        m.put("dbUsernameSet", secrets.isSet(SecretStore.DB_USER));
        m.put("dbPasswordSet", secrets.isSet(SecretStore.DB_PASS));
        return m;
    }

    /** Blank fields keep the stored value, so admins never need to see or re-type an existing secret. */
    @PutMapping
    public Map<String, Object> save(@RequestBody Map<String, String> b) {
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put(SecretStore.RAPIDAPI_KEY, b.get("rapidApiKey"));
        updates.put(SecretStore.RAPIDAPI_JOB_SEARCH_KEY, b.get("rapidApiJobSearchKey"));
        updates.put(SecretStore.RAPIDAPI_JOB_SALARY_KEY, b.get("rapidApiJobSalaryKey"));
        updates.put(SecretStore.RAPIDAPI_COMPANY_JOB_SEARCH_KEY, b.get("rapidApiCompanyJobSearchKey"));
        updates.put(SecretStore.RAPIDAPI_JOB_DETAILS_KEY, b.get("rapidApiJobDetailsKey"));
        updates.put(SecretStore.RAPIDAPI_JOB_SEARCH_URL, normalizeIfProvided(b.get("rapidApiJobSearchUrl")));
        updates.put(SecretStore.RAPIDAPI_JOB_DETAILS_URL, normalizeIfProvided(b.get("rapidApiJobDetailsUrl")));
        updates.put(SecretStore.RAPIDAPI_JOB_SALARY_URL, normalizeIfProvided(b.get("rapidApiJobSalaryUrl")));
        updates.put(SecretStore.RAPIDAPI_COMPANY_JOB_SALARY_URL, normalizeIfProvided(b.get("rapidApiCompanyJobSalaryUrl")));
        updates.put(SecretStore.OPENROUTER_KEY, b.get("openRouterKey"));
        updates.put(SecretStore.OPENROUTER_MODEL, b.get("openRouterModel"));
        updates.put(SecretStore.DB_URL, b.get("dbUrl"));
        updates.put(SecretStore.DB_USER, b.get("dbUsername"));
        updates.put(SecretStore.DB_PASS, b.get("dbPassword"));
        secrets.putAll(updates);
        Map<String, Object> out = status();
        out.put("message", "Saved securely. Database changes apply after the backend restarts.");
        return out;
    }

    private static String normalizeRapidApiUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equalsIgnoreCase("rapidapi.com") || host.toLowerCase().endsWith(".rapidapi.com"))
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getPort() != -1 && uri.getPort() != 443
                    || uri.getPath() == null || uri.getPath().isBlank()) {
                throw new IllegalArgumentException("Enter the HTTPS RapidAPI request URL only (not a cURL command or API-key header).");
            }
            return new URI("https", null, host, -1, uri.getPath(), null, null).toString();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Enter a valid HTTPS RapidAPI request URL only (not a cURL command or API key).");
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Enter the HTTPS RapidAPI request URL")) throw e;
            throw new IllegalArgumentException("Enter a valid HTTPS RapidAPI endpoint URL only (not a cURL command or API key).");
        }
    }

    private static String normalizeIfProvided(String value) {
        return value == null || value.isBlank() ? value : normalizeRapidApiUrl(value.trim());
    }

    @PostMapping("/test/{target}")
    public Map<String, Object> test(@PathVariable String target, @RequestBody(required = false) Map<String, String> b) {
        String msg = switch (target) {
            case "rapidapi" -> jsearch.test();
            case "openrouter" -> ai.test();
            case "database" -> testDatabase();
            default -> throw new IllegalArgumentException("Unknown test.");
        };
        return Map.of("ok", true, "message", msg);
    }

    private String testDatabase() {
        String url = secrets.get(SecretStore.DB_URL), user = secrets.get(SecretStore.DB_USER), pass = secrets.get(SecretStore.DB_PASS);
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Save the MySQL URL first.");
        try (Connection c = DriverManager.getConnection(url, user, pass == null ? "" : pass)) {
            return "MySQL connection works (" + c.getMetaData().getDatabaseProductName() + ").";
        } catch (Exception e) {
            // do not echo the driver message: it can contain host/user details
            throw new IllegalArgumentException("Could not connect to MySQL with the saved details.");
        }
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }
}
