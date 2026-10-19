package com.teximo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Interview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long driveId;
    private String driveTitle;
    private Long studentId;
    private String studentName;
    private LocalDateTime scheduledAt;
    private String mode = "ONLINE";      // ONLINE | ONSITE
    private String venueOrLink;
    private String round = "Technical";

    public Long getId() { return id; }
    public Long getDriveId() { return driveId; }
    public void setDriveId(Long v) { driveId = v; }
    public String getDriveTitle() { return driveTitle; }
    public void setDriveTitle(String v) { driveTitle = v; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long v) { studentId = v; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String v) { studentName = v; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime v) { scheduledAt = v; }
    public String getMode() { return mode; }
    public void setMode(String v) { mode = v; }
    public String getVenueOrLink() { return venueOrLink; }
    public void setVenueOrLink(String v) { venueOrLink = v; }
    public String getRound() { return round; }
    public void setRound(String v) { round = v; }
}
