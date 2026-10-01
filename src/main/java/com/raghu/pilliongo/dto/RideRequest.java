package com.raghu.pilliongo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class RideRequest {
    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Destination is required")
    private String destination;

    @NotBlank(message = "Ride type is required")
    private String rideType;        // INSTANT or PLANNED

    // only for PLANNED rides
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime scheduledTime;

    private String description;

    // Required only when a DRIVER is offering a ride (offerPlannedRide /
    // offerInstantRide) — which of their own Vehicle rows to use for this
    // specific ride. Left null for rider-created requests (createRide),
    // which don't involve a vehicle yet.
    private Long vehicleId;

    // DRIVER planned offers only — how many passengers this trip can take.
    // Bikes are capped at 1 in RideService regardless of what's sent.
    @Min(value = 1, message = "Seats must be at least 1")
    @Max(value = 6, message = "Seats cannot be more than 6")
    private Integer seats;
}
