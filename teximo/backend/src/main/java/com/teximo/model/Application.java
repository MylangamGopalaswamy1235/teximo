package com.teximo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A student's application to a college placement drive (internal only, never sent to external sites). */
@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(columnNames = {"studentId", "driveId"}))
public class Application {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long studentId;
    private String studentName;
    private Long driveId;
    private String driveTitle;
    private String companyName;
    private String status = "APPLIED";   // APPLIED | SHORTLISTED | INTERVIEW | SELECTED | REJECTED
    private LocalDateTime appliedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long v) { studentId = v; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String v) { studentName = v; }
    public Long getDriveId() { return driveId; }
    public void setDriveId(Long v) { driveId = v; }
    public String getDriveTitle() { return driveTitle; }
    public void setDriveTitle(String v) { driveTitle = v; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String v) { companyName = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
}
