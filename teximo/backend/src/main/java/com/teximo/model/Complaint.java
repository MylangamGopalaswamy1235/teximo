package com.teximo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Complaint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long studentId;
    private String studentName;
    private String subject;
    @Column(length = 3000) private String message;
    private String status = "OPEN";      // OPEN | IN_REVIEW | RESOLVED
    @Column(length = 3000) private String adminResponse;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long v) { studentId = v; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String v) { studentName = v; }
    public String getSubject() { return subject; }
    public void setSubject(String v) { subject = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { message = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getAdminResponse() { return adminResponse; }
    public void setAdminResponse(String v) { adminResponse = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void touch() { updatedAt = LocalDateTime.now(); }
}
