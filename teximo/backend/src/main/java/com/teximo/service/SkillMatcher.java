package com.teximo.service;

import java.util.*;
import java.util.regex.Pattern;

/** Simple keyword-based skill matching used for recommendations, eligibility and skill-gap hints. */
public final class SkillMatcher {
    private SkillMatcher() {}

    /** Common skills used to detect gaps in job descriptions. */
    public static final List<String> KNOWN_SKILLS = List.of(
            "java", "spring boot", "python", "javascript", "typescript", "react", "angular", "node.js", "html", "css",
            "sql", "mysql", "mongodb", "postgresql", "git", "docker", "kubernetes", "aws", "azure", "rest api",
            "microservices", "c++", "c#", "data structures", "algorithms", "machine learning", "deep learning",
            "pandas", "numpy", "tensorflow", "excel", "power bi", "tableau", "linux", "devops", "ci/cd",
            "figma", "communication", "problem solving", "testing", "selenium", "django", "flask", "php", "kotlin", "flutter");

    public static Set<String> parse(String commaSeparated) {
        Set<String> s = new LinkedHashSet<>();
        if (commaSeparated == null) return s;
        for (String p : commaSeparated.split("[,;\\n]")) if (!p.isBlank()) s.add(p.trim().toLowerCase());
        return s;
    }

    public static boolean contains(String text, String skill) {
        if (text == null || text.isBlank()) return false;
        return Pattern.compile("(?<![a-z0-9+#.])" + Pattern.quote(skill.toLowerCase()) + "(?![a-z0-9+#])")
                .matcher(text.toLowerCase()).find();
    }

    /** Skills (from KNOWN_SKILLS) mentioned in the text. */
    public static Set<String> extract(String text) {
        Set<String> found = new LinkedHashSet<>();
        for (String k : KNOWN_SKILLS) if (contains(text, k)) found.add(k);
        return found;
    }

    /** Score 0-100: share of the job's skills the student already has. */
    public static int score(Set<String> studentSkills, Set<String> jobSkills) {
        if (jobSkills.isEmpty()) return 50;
        long hit = jobSkills.stream().filter(studentSkills::contains).count();
        return (int) Math.round(100.0 * hit / jobSkills.size());
    }
}
