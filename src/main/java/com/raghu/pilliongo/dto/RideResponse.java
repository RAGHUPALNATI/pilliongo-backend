package com.raghu.pilliongo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
public class RideResponse {
    private Long id;
    private String riderName;
    private String riderPhone;
    private String driverName;
    private String driverPhone;
    private String vehicleType;
    private String vehicleModel;
    private String vehiclePlate;

    private String pickupLocation;
    private String destination;
    private String rideType;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime scheduledTime;

    private String description;
    private Double fare;

    // Multi-seat carpooling — see Ride.seatsTotal/seatsBooked/seatCount.
    private Integer seatsTotal;
    private Integer seatsBooked;
    private Integer seatsAvailable;
    private Integer seatCount;
    private Long parentOfferId;

    private boolean paid;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime paidAt;

    // Driver's live position — only populated once contacts are visible
    // (ACCEPTED/STARTED/COMPLETED), same visibility rule as phone numbers,
    // and only when the driver has actually sent a location ping.
    private Double driverLat;
    private Double driverLng;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime driverLocationUpdatedAt;

    // Rider's live position — the reverse direction, same visibility rule
    // and same "only if they've actually sent a ping" behavior.
    private Double riderLat;
    private Double riderLng;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime riderLocationUpdatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;
}