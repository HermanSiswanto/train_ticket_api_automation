package com.herman.automation.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ScheduleData {
    private final Long id;
    private final Long trainId;
    private final Long originStationId;
    private final Long destinationStationId;
    private final LocalDateTime departureTime;
    private final LocalDateTime arrivalTime;
    private final BigDecimal price;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;


    public ScheduleData(Long trainId, Long originStationId, Long destinationStationId, LocalDateTime departureTime, LocalDateTime arrivalTime, BigDecimal price){
        this(null, trainId, originStationId, destinationStationId, departureTime, arrivalTime, price, null, null, null);

    }

    public ScheduleData(
        Long id,
        Long trainId,
        Long originStationId,
        Long destinationStationId,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        BigDecimal price,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    )   
    {   this.id =id;
        this.trainId = trainId;
        this.originStationId = originStationId;
        this.destinationStationId = destinationStationId;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTrainId() {
        return trainId;
    }

    public Long getOriginStationId(){
        return originStationId;
    }

    public Long getDestinationStationId(){
        return destinationStationId;
    }
    
    public LocalDateTime getDepartureTime(){
        return departureTime;
    }

    public LocalDateTime getArrivalTime(){
        return arrivalTime;
    }

    public BigDecimal getPrice(){
        return price;
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
