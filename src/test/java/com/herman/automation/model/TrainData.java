package com.herman.automation.model;

import java.time.LocalDateTime;

public final class TrainData {

    private final Long id;
    private final String trainCode;
    private final String trainName;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public TrainData(String trainCode, String trainName) {
        this(null, trainCode, trainName, null, null, null);
    }

    public TrainData(
            Long id,
            String trainCode,
            String trainName,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.trainCode = trainCode;
        this.trainName = trainName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTrainCode() {
        return trainCode;
    }

    public String getTrainName() {
        return trainName;
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