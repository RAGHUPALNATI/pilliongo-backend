package com.raghu.pilliongo.model;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
@Entity
@Table(name="driver_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class DriverProfile {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne
    @JoinColumn(name="user_id", nullable=false)
    private User user;


    // Left nullable: RegisterRequest doesn't currently force these with
    // @NotBlank (a driver could theoretically register without full vehicle
    // details), so making the DB column nullable=false here would turn a
    // clean 400 into a raw database constraint-violation error instead.
    private String vehicleType;
    private String vehicleModel;
    private String vehiclePlate;

    @Builder.Default
    @Column(nullable=false)
    private boolean available=false;

    @Builder.Default
    @Column(nullable=false)
    private Double totalEarnings=0.0;

    // Live position for the "where's my driver" map on the rider's ride
    // details page. Deliberately just the CURRENT position, overwritten on
    // every update rather than logged/appended — storage stays flat
    // regardless of ride count or ride duration, unlike a location-history
    // table would. Nullable: most drivers most of the time have no active
    // ride and no reason to have ever sent one.
    private Double currentLat;
    private Double currentLng;
    private LocalDateTime locationUpdatedAt;

}
