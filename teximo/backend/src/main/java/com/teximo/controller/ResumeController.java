package com.teximo.controller;

import com.teximo.model.Role;
import com.teximo.model.User;
import com.teximo.repo.UserRepository;
import com.teximo.security.RequireRole;
import com.teximo.service.AiService;
import com.teximo.service.SkillMatcher;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

@RestController
@RequestMapping("/api/resume")
@RequireRole(Role.STUDENT)
public class ResumeController {
    private final UserRepository users;
    private final AiService ai;

    public ResumeController(UserRepository users, AiService ai) { this.users = users; this.ai = ai; }

    /** Upload a PDF (up to 500 MB), extract its text, store it on the profile and analyse it. */
    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    public Map<String, Object> analyze(@RequestAttribute("userId") Long id,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "targetRole", defaultValue = "") String targetRole) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String name = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (file.isEmpty() || !name.endsWith(".pdf")) throw new IllegalArgumentException("Please upload a PDF file.");

        String text;
        try (InputStream in = file.getInputStream(); PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            text = new PDFTextStripper().getText(doc).trim();
        } catch (Exception e) {
            throw new IllegalArgumentException("This PDF could not be read. Is it password protected?");
        }
        if (text.length() < 50)
            throw new IllegalArgumentException("No readable text found. Scanned/image-only PDFs are not supported.");

        User u = users.findById(id).orElseThrow();
        u.setResumeText(text.length() > 60000 ? text.substring(0, 60000) : text);
        users.save(u);
        return report(u, text, targetRole);
    }

    /** Re-analyse the resume already stored on the student's profile. */
    @PostMapping("/reanalyze")
    public Map<String, Object> reanalyze(@RequestAttribute("userId") Long id, @RequestBody Map<String, String> b) {
        User u = users.findById(id).orElseThrow();
        if (u.getResumeText() == null || u.getResumeText().isBlank())
            throw new IllegalArgumentException("Upload a resume PDF first.");
        return report(u, u.getResumeText(), AuthController.str(b, "targetRole"));
    }

    /** AI-written resume from the student's profile. The student copies/edits it; nothing is uploaded anywhere. */
    @PostMapping("/build")
    public Map<String, Object> build(@RequestAttribute("userId") Long id, @RequestBody Map<String, String> b) {
        User u = users.findById(id).orElseThrow();
        String prompt = "Write a clean, ATS-friendly, one-page resume in plain text with sections: Summary, Education, Skills, "
                + "Projects, Experience, Achievements. Use only the facts given; where information is missing write a short "
                + "[placeholder] instead of inventing details.\n\n"
                + "Name: " + u.getName() + "\nEmail: " + u.getEmail() + "\nPhone: " + nz(u.getPhone())
                + "\nCollege: " + nz(u.getCollege()) + "\nDegree: " + nz(u.getDegree()) + " " + nz(u.getBranch())
                + "\nGraduation year: " + nz(u.getGraduationYear()) + "\nCGPA: " + nz(u.getCgpa())
                + "\nEducation notes: " + nz(u.getEducation()) + "\nSkills: " + nz(u.getSkills())
                + "\nTarget role: " + AuthController.str(b, "targetRole")
                + "\nExtra details from student: " + AuthController.str(b, "extra");
        String resume = ai.chat("You are an expert resume writer for college students and freshers.", prompt);
        return Map.of("resume", resume);
    }

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@RequestBody Map<String, String> body) throws Exception {
        String resume = AuthController.str(body, "resume");
        if (resume.isBlank()) throw new IllegalArgumentException("Generate a resume before downloading it.");
        if (resume.length() > 60000) throw new IllegalArgumentException("The resume is too long to export.");

        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.COURIER);
            List<String> lines = wrapPdfText(resume, font, 10, 495);
            for (int offset = 0; offset < lines.size(); offset += 52) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 10);
                    content.newLineAtOffset(50, 790);
                    int end = Math.min(offset + 52, lines.size());
                    for (int i = offset; i < end; i++) {
                        content.showText(lines.get(i));
                        content.newLineAtOffset(0, -14);
                    }
                    content.endText();
                }
            }
            document.save(output);
            pdf = output.toByteArray();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MediaType.APPLICATION_PDF_VALUE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"teximo-resume.pdf\"")
                .body(pdf);
    }

    private static List<String> wrapPdfText(String text, PDType1Font font, float fontSize, float maxWidth) throws Exception {
        List<String> lines = new ArrayList<>();
        for (String sourceLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : sourceLine.split("(?<=\\s)|(?=\\s)")) {
                String safeWord = pdfSafe(word, font);
                String candidate = line + safeWord;
                if (font.getStringWidth(candidate) / 1000 * fontSize > maxWidth && !line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (font.getStringWidth(safeWord) / 1000 * fontSize > maxWidth) {
                    for (char c : safeWord.toCharArray()) {
                        String next = line.toString() + c;
                        if (font.getStringWidth(next) / 1000 * fontSize > maxWidth && !line.isEmpty()) {
                            lines.add(line.toString());
                            line.setLength(0);
                        }
                        line.append(c);
                    }
                } else {
                    line.append(safeWord);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static String pdfSafe(String value, PDType1Font font) {
        StringBuilder safe = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            try {
                font.encode(character);
                safe.append(character);
            } catch (IllegalArgumentException | java.io.IOException e) {
                safe.append('?');
            }
        });
        return safe.toString();
    }

    // ---------- analysis ----------
    private Map<String, Object> report(User u, String text, String targetRole) {
        Map<String, Object> out = new LinkedHashMap<>();
        String lower = text.toLowerCase();

        // 1) Rule-based ATS checks (work without any API key)
        List<String> good = new ArrayList<>(), fix = new ArrayList<>();
        int score = 40;
        check(lower.contains("@"), "Email address present", "Add a professional email address", good, fix);
        check(lower.matches("(?s).*\\+?\\d[\\d\\s-]{8,}.*"), "Phone number present", "Add a phone number", good, fix);
        for (String section : List.of("education", "skills", "projects", "experience")) {
            check(lower.contains(section), "'" + section + "' section found",
                    "Add a clear '" + section + "' section heading", good, fix);
        }
        int words = text.split("\\s+").length;
        check(words >= 150 && words <= 900, "Length looks right (" + words + " words)",
                words < 150 ? "Resume looks too short (" + words + " words)" : "Resume is long (" + words + " words); aim for one page", good, fix);
        check(lower.matches("(?s).*\\d+\\s?%.*|(?s).*\\b\\d{2,}\\b.*"), "Contains numbers/metrics",
                "Add measurable results (e.g. 'reduced load time by 30%')", good, fix);
        check(lower.contains("github") || lower.contains("linkedin") || lower.contains("portfolio"), "Profile/portfolio links found",
                "Add GitHub / LinkedIn / portfolio links", good, fix);
        score += good.size() * 7;
        score = Math.min(score, 98);

        // 2) Skills found / missing for the target role
        Set<String> found = SkillMatcher.extract(text);
        found.addAll(SkillMatcher.parse(u.getSkills()));
        out.put("atsScore", score);
        out.put("strengths", good);
        out.put("improvements", fix);
        out.put("skillsFound", found);

        if (!targetRole.isBlank()) {
            Set<String> roleSkills = SkillMatcher.extract(targetRole);
            List<String> missing = new ArrayList<>();
            for (String s : roleSkills) if (!found.contains(s)) missing.add(s);
            out.put("missingForTargetRole", missing);
        }

        // 3) AI feedback (optional: only if OpenRouter key is configured)
        if (ai.isConfigured()) {
            try {
                String feedback = ai.chat("You are a strict but kind recruiter reviewing a fresher's resume.",
                        "Target role: " + (targetRole.isBlank() ? "general software/IT fresher" : targetRole)
                                + "\n\nGive: 1) three strengths, 2) five specific improvements, 3) missing skills for the role, "
                                + "4) two rewritten bullet points that are stronger. Keep it concise.\n\nRESUME:\n"
                                + (text.length() > 8000 ? text.substring(0, 8000) : text));
                out.put("aiFeedback", feedback);
            } catch (IllegalArgumentException e) {
                out.put("aiFeedback", null);
                out.put("aiNote", e.getMessage());
            }
        } else {
            out.put("aiNote", "AI feedback is off until an OpenRouter key is added in Settings. The checks above still work.");
        }
        return out;
    }

    private static void check(boolean ok, String good, String fix, List<String> g, List<String> f) {
        if (ok) g.add(good); else f.add(fix);
    }
    private static String nz(Object o) { return o == null ? "" : o.toString(); }
}
