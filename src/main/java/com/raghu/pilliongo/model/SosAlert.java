package com.raghu.pilliongo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// A row is written only when someone actually presses the SOS button —
// hopefully rare — so unlike a location-ping log this can never grow with
// ride volume or app usage; it grows only with real emergencies pressed.
@Entity
@Table(name = "sos_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SosAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The ride this was raised during, if any — SOS can still be raised
    // without an active ride tied to it (nullable) but is almost always
    // pressed mid-ride.
    @ManyToOne
    @JoinColumn(name = "ride_id")
    private Ride ride;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Best-effort device location at the moment SOS was pressed — optional,
    // since geolocation permission may be denied.
    private Double lat;
    private Double lng;

    @Column(length = 300)
    private String message;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private SosStatus status = SosStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum SosStatus {
        OPEN, RESOLVED
    }
}
