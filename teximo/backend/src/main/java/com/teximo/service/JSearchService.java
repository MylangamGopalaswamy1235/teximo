package com.teximo.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Reads job listings from RapidAPI JSearch. Read-only: Teximo never applies to jobs or posts anything
 * to LinkedIn, Naukri or other sites. Students open the original posting themselves.
 */
@Service
public class JSearchService {
    private static final String HOST = "jsearch.p.rapidapi.com";
    private static final String DEFAULT_SEARCH_URL = "https://" + HOST + "/search";
    private static final String DEFAULT_COUNTRY = "IN";
    private final SecretStore secrets;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public JSearchService(SecretStore secrets) { this.secrets = secrets; }

    public List<Map<String, Object>> search(String query, String location, String type, int page) {
        String key = secrets.get(SecretStore.RAPIDAPI_JOB_SEARCH_KEY);
        if (key == null || key.isBlank()) key = secrets.get(SecretStore.RAPIDAPI_KEY);
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Job search is not configured yet. Ask your placement admin to add the RapidAPI key.");
        String q = (query == null || query.isBlank() ? "software developer" : query)
                + (location == null || location.isBlank() ? "" : " in " + location);
        String endpoint = secrets.get(SecretStore.RAPIDAPI_JOB_SEARCH_URL);
        if (endpoint == null || endpoint.isBlank()) endpoint = DEFAULT_SEARCH_URL;
        URI endpointUri;
        try {
            endpointUri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The saved JSearch endpoint URL is invalid. Update it in Admin → API & Database.");
        }
        String host = endpointUri.getHost();
        if (!"https".equalsIgnoreCase(endpointUri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("rapidapi.com") || host.toLowerCase(Locale.ROOT).endsWith(".rapidapi.com"))
                || endpointUri.getRawQuery() != null || endpointUri.getFragment() != null) {
            throw new IllegalArgumentException("The saved JSearch endpoint must be an HTTPS RapidAPI URL without query parameters.");
        }
        StringBuilder url = new StringBuilder(endpoint).append("?num_pages=1&page=").append(Math.max(page, 1))
                .append("&query=").append(URLEncoder.encode(q, StandardCharsets.UTF_8))
                .append("&country=").append(DEFAULT_COUNTRY);
        if ("INTERNSHIP".equalsIgnoreCase(type)) url.append("&employment_types=INTERN");
        else if ("FULLTIME".equalsIgnoreCase(type)) url.append("&employment_types=FULLTIME");

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .header("X-RapidAPI-Key", key)
                    .header("X-RapidAPI-Host", host)
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401 || res.statusCode() == 403)
                throw new IllegalArgumentException("RapidAPI rejected the key or the JSearch subscription is missing.");
            if (res.statusCode() == 429)
                throw new IllegalArgumentException("Job search limit reached for now. Please try later.");
            if (res.statusCode() == 404)
                throw new IllegalArgumentException("RapidAPI returned 404 for JSearch. Confirm the JSearch API subscription is active and its endpoint is still /search.");
            if (res.statusCode() >= 400)
                throw new IllegalArgumentException("Job search returned an error (" + res.statusCode() + ").");
            JsonNode response = mapper.readTree(res.body());
            JsonNode data = response.path("data");
            JsonNode jobs = data.isArray() ? data : data.path("jobs");
            if (!jobs.isArray())
                throw new IllegalArgumentException("JSearch returned an unexpected response format. Check the saved endpoint URL in Admin → API & Database.");
            return normalize(jobs);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not reach the job search service.");
        }
    }

    private List<Map<String, Object>> normalize(JsonNode data) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();   // prevents duplicate listings
        for (JsonNode j : data) {
            String title = j.path("job_title").asString("");
            String employer = j.path("employer_name").asString("");
            String city = j.path("job_city").asString("");
            String dedupe = (title + "|" + employer + "|" + city).toLowerCase().replaceAll("\\s+", " ");
            if (title.isBlank() || !seen.add(dedupe)) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.path("job_id").asString(dedupe));
            m.put("title", title);
            m.put("company", employer);
            m.put("companyWebsite", j.path("employer_website").asString(""));
            m.put("companyIndustry", j.path("employer_industry").asString(""));
            m.put("logo", j.path("employer_logo").asString(null));
            String state = j.path("job_state").asString("");
            String country = j.path("job_country").asString("");
            if ("IN".equalsIgnoreCase(country)) country = "India";
            else if (country.isBlank() && "IN".equalsIgnoreCase(state)) {
                state = "";
                country = "India";
            }
            m.put("location", String.join(", ", nonBlank(city, state, country)));
            m.put("type", j.path("job_employment_type").asString(""));
            m.put("remote", j.path("job_is_remote").asBoolean(false));
            String desc = j.path("job_description").asString("");
            m.put("description", desc.length() > 3000 ? desc.substring(0, 3000) + "…" : desc);
            m.put("source", j.path("job_publisher").asString(""));
            m.put("applyLink", j.path("job_apply_link").asString(""));   // opened by the student, in a new tab
            m.put("postedAt", j.path("job_posted_at_datetime_utc").asString(""));
            List<String> req = new ArrayList<>();
            j.path("job_highlights").path("Qualifications").forEach(n -> req.add(n.asString()));
            m.put("qualifications", req);
            out.add(m);
        }
        return out;
    }

    private static List<String> nonBlank(String... parts) {
        List<String> l = new ArrayList<>();
        for (String p : parts) if (p != null && !p.isBlank()) l.add(p);
        return l;
    }

    /** Used by the admin "Test connection" button. */
    public String test() {
        List<Map<String, Object>> r = search("developer", "", "", 1);
        return "RapidAPI JSearch works (" + r.size() + " sample results).";
    }
}
