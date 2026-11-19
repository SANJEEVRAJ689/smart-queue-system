package com.smartqueue.model;

/**
 * Entity representing a department/service desk (e.g. Admission Office "AD", Clinic "CL").
 */
public class ServiceEntity {
    private int id;
    private String name;
    private String prefix; // CHAR(2), e.g. "AD", "CL", "LB"
    private int defaultAvgMinutes;
    private boolean active;
    private String description;

    public ServiceEntity() {
        this.defaultAvgMinutes = 5;
        this.active = true;
        this.description = "";
    }

    public ServiceEntity(int id, String name, String prefix, int defaultAvgMinutes, boolean active) {
        this.id = id;
        this.name = name;
        this.prefix = prefix != null ? prefix.toUpperCase().trim() : "";
        this.defaultAvgMinutes = defaultAvgMinutes;
        this.active = active;
        this.description = "";
    }

    public ServiceEntity(int id, String name, String prefix, String description, int defaultAvgMinutes, boolean active) {
        this.id = id;
        this.name = name;
        this.prefix = prefix != null ? prefix.toUpperCase().trim() : "";
        this.description = description != null ? description : "";
        this.defaultAvgMinutes = defaultAvgMinutes;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix != null ? prefix.toUpperCase().trim() : "";
    }

    public String getCodePrefix() {
        return prefix;
    }

    public void setCodePrefix(String codePrefix) {
        this.prefix = codePrefix != null ? codePrefix.toUpperCase().trim() : "";
    }

    public int getDefaultAvgMinutes() {
        return defaultAvgMinutes;
    }

    public void setDefaultAvgMinutes(int defaultAvgMinutes) {
        this.defaultAvgMinutes = defaultAvgMinutes;
    }

    public int getDefaultAvgTimeMins() {
        return defaultAvgMinutes;
    }

    public void setDefaultAvgTimeMins(int mins) {
        this.defaultAvgMinutes = mins;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return name + " [" + prefix + "]";
    }
}
