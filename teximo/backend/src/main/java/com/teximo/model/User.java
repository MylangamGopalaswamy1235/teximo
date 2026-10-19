package com.teximo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/** One table for both students and admins; student-only fields are simply empty for admins. */
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role = Role.STUDENT;

    // student profile
    private String phone;
    private String college;
    private String degree;
    private String branch;
    private Integer graduationYear;
    private Double cgpa;
    private Integer backlogs = 0;
    @Column(length = 1000) private String skills;      // comma separated
    @Column(length = 2000) private String education;
    @Column(length = 1000) private String linkedinUrl;
    @Lob @Column(columnDefinition = "LONGTEXT") @JsonIgnore
    private String resumeText;                          // extracted from last uploaded PDF
    private String placementStatus = "NOT_PLACED";

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { passwordHash = v; }
    public Role getRole() { return role; }
    public void setRole(Role v) { role = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public String getCollege() { return college; }
    public void setCollege(String v) { college = v; }
    public String getDegree() { return degree; }
    public void setDegree(String v) { degree = v; }
    public String getBranch() { return branch; }
    public void setBranch(String v) { branch = v; }
    public Integer getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Integer v) { graduationYear = v; }
    public Double getCgpa() { return cgpa; }
    public void setCgpa(Double v) { cgpa = v; }
    public Integer getBacklogs() { return backlogs; }
    public void setBacklogs(Integer v) { backlogs = v; }
    public String getSkills() { return skills; }
    public void setSkills(String v) { skills = v; }
    public String getEducation() { return education; }
    public void setEducation(String v) { education = v; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String v) { linkedinUrl = v; }
    public String getResumeText() { return resumeText; }
    public void setResumeText(String v) { resumeText = v; }
    public String getPlacementStatus() { return placementStatus; }
    public void setPlacementStatus(String v) { placementStatus = v; }
}
