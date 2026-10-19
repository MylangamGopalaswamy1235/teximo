package com.teximo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long studentId;
    private String title;
    @Column(length = 1000) private String message;
    @Column(name = "is_read") private boolean read = false;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}
    public Notification(Long studentId, String title, String message) {
        this.studentId = studentId; this.title = title; this.message = message;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public void setRead(boolean v) { read = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
