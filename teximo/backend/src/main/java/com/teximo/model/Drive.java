package com.teximo.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** A college placement drive with its eligibility criteria. */
@Entity
public class Drive {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long companyId;
    private String companyName;
    private String title;
    private String type = "PLACEMENT";            // PLACEMENT | INTERNSHIP
    @Column(length = 2000) private String description;
    private String ctc;
    private LocalDate driveDate;
    private LocalDate lastDateToApply;

    // eligibility criteria
    private Double minCgpa = 0.0;
    private Integer maxBacklogs = 0;
    private String allowedBranches;               // comma separated, empty = all
    private Integer graduationYear;               // null = any
    @Column(length = 1000) private String requiredSkills; // comma separated

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { companyId = v; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String v) { companyName = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public String getType() { return type; }
    public void setType(String v) { type = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getCtc() { return ctc; }
    public void setCtc(String v) { ctc = v; }
    public LocalDate getDriveDate() { return driveDate; }
    public void setDriveDate(LocalDate v) { driveDate = v; }
    public LocalDate getLastDateToApply() { return lastDateToApply; }
    public void setLastDateToApply(LocalDate v) { lastDateToApply = v; }
    public Double getMinCgpa() { return minCgpa; }
    public void setMinCgpa(Double v) { minCgpa = v; }
    public Integer getMaxBacklogs() { return maxBacklogs; }
    public void setMaxBacklogs(Integer v) { maxBacklogs = v; }
    public String getAllowedBranches() { return allowedBranches; }
    public void setAllowedBranches(String v) { allowedBranches = v; }
    public Integer getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Integer v) { graduationYear = v; }
    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String v) { requiredSkills = v; }
}
