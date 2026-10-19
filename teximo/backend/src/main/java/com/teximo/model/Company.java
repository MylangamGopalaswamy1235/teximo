package com.teximo.model;

import jakarta.persistence.*;

@Entity
public class Company {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String industry;
    private String website;
    private String location;
    @Column(length = 2000) private String description;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getIndustry() { return industry; }
    public void setIndustry(String v) { industry = v; }
    public String getWebsite() { return website; }
    public void setWebsite(String v) { website = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { location = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
}
