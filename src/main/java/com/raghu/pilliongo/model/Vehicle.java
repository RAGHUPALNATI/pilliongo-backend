package com.raghu.pilliongo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// A driver can now own more than one vehicle (bike + car, two bikes, etc).
// DriverProfile keeps its own single vehicleType/vehicleModel/vehiclePlate
// fields too — those stay as the driver's "primary" vehicle shown on their
// dashboard header — but every ride they OFFER (instant or planned) now
// requires picking one specific Vehicle from this table, snapshotted onto
// the Ride itself at offer time (see Ride.vehicleType/vehicleModel/
// vehiclePlate). That's what actually enforces "select one vehicle per
// ride" instead of always silently using whatever's on the profile.
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverProfile driver;

    @Column(nullable = false)
    private String vehicleType; // Bike or Car

    @Column(nullable = false)
    private String vehicleModel;

    @Column(nullable = false)
    private String vehiclePlate;

    // Which of this driver's (at most 2) vehicles is shown on their
    // dashboard header and mirrored onto DriverProfile.vehicleType/Model/
    // Plate. Exactly one vehicle is primary at a time — VehicleService
    // enforces that on add/set-primary/delete. Left nullable at the DB
    // level (no default-less NOT NULL ALTER on a populated table); the
    // Java field stays a primitive boolean because SchemaPatchRunner
    // normalizes any NULL rows to false on boot, so it's never actually
    // read as NULL.
    @Builder.Default
    private boolean primaryVehicle = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
