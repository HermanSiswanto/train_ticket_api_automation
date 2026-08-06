package com.herman.automation.model;

import java.time.LocalDateTime;

public final class StationData {

    private final Long id;
    private final String stationCode;
    private final String stationName;
    private final String city;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public StationData(String stationCode, String stationName, String city) {
        this(null, stationCode, stationName, city, null, null, null);
    }

    public StationData(
            Long id,
            String stationCode,
            String stationName,
            String city,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.stationCode = stationCode;
        this.stationName = stationName;
        this.city= city;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getStationCode() {
        return stationCode;
    }

    public String getStationName() {
        return stationName;
    }

    public String getCity(){
        return city;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}