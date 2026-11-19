package com.smartqueue.model;

/**
 * Entity representing a physical counter desk.
 */
public class Counter {
    private int id;
    private String name;
    private int serviceId;
    private Integer staffId;
    private CounterStatus status;
    private Integer currentTokenId;

    // Display helpers for UI bindings
    private String serviceName;
    private String staffName;
    private String currentTokenNo;

    public Counter() {
        this.status = CounterStatus.CLOSED;
    }

    public Counter(int id, String name, int serviceId, Integer staffId, CounterStatus status, Integer currentTokenId) {
        this.id = id;
        this.name = name;
        this.serviceId = serviceId;
        this.staffId = staffId;
        this.status = status;
        this.currentTokenId = currentTokenId;
    }

    // Convenience constructor for int counterNumber
    public Counter(int id, int counterNumber, int serviceId, Integer staffId, String statusStr) {
        this.id = id;
        this.name = "Counter " + counterNumber;
        this.serviceId = serviceId;
        this.staffId = staffId;
        this.status = CounterStatus.fromString(statusStr);
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

    public int getCounterNumber() {
        if (name != null) {
            String[] tokens = name.split("[^0-9]+");
            for (String t : tokens) {
                if (!t.isEmpty()) {
                    try {
                        return Integer.parseInt(t);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return id;
    }

    public void setCounterNumber(int counterNumber) {
        this.name = "Counter " + counterNumber;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public Integer getStaffId() {
        return staffId;
    }

    public void setStaffId(Integer staffId) {
        this.staffId = staffId;
    }

    public Integer getStaffUserId() {
        return staffId;
    }

    public void setStaffUserId(Integer staffId) {
        this.staffId = staffId;
    }

    public CounterStatus getStatus() {
        return status;
    }

    public void setStatus(CounterStatus status) {
        this.status = status;
    }

    public void setStatus(String statusStr) {
        this.status = CounterStatus.fromString(statusStr);
    }

    public Integer getCurrentTokenId() {
        return currentTokenId;
    }

    public void setCurrentTokenId(Integer currentTokenId) {
        this.currentTokenId = currentTokenId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public String getCurrentTokenNo() {
        return currentTokenNo;
    }

    public void setCurrentTokenNo(String currentTokenNo) {
        this.currentTokenNo = currentTokenNo;
    }

    public boolean isOpen() {
        return this.status == CounterStatus.OPEN;
    }

    public boolean isBusy() {
        return this.status == CounterStatus.BUSY;
    }

    @Override
    public String toString() {
        return name + (serviceName != null ? " (" + serviceName + ")" : "");
    }
}
