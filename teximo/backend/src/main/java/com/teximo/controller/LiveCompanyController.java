package com.teximo.controller;

import com.teximo.model.Role;
import com.teximo.security.RequireRole;
import com.teximo.service.JSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/admin/live-companies")
@RequireRole(Role.ADMIN)
public class LiveCompanyController {
    private final JSearchService jobs;

    public LiveCompanyController(JSearchService jobs) {
        this.jobs = jobs;
    }

    @GetMapping
    public List<Map<String, String>> list(@RequestParam(defaultValue = "software developer") String query,
                                          @RequestParam(defaultValue = "") String location) {
        List<Map<String, Object>> results = jobs.search(query, location, "", 1);
        Map<String, Map<String, String>> companies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> result : results) {
            String name = Objects.toString(result.get("company"), "").trim();
            if (name.isBlank()) continue;
            Map<String, String> company = companies.computeIfAbsent(name, ignored -> {
                Map<String, String> newCompany = new LinkedHashMap<>();
                newCompany.put("name", name);
                newCompany.put("industry", "");
                newCompany.put("location", "");
                newCompany.put("website", "");
                return newCompany;
            });
            mergeIfPresent(company, "industry", result.get("companyIndustry"));
            mergeIfPresent(company, "location", result.get("location"));
            mergeIfPresent(company, "website", result.get("companyWebsite"));
        }
        return new ArrayList<>(companies.values());
    }

    private static void mergeIfPresent(Map<String, String> company, String field, Object value) {
        String text = Objects.toString(value, "").trim();
        if (company.get(field).isBlank() && !text.isBlank()) company.put(field, text);
    }
}
