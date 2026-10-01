package com.raghu.pilliongo.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerified = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Live GPS position — for RIDERS (a driver's equivalent already lives
    // on DriverProfile). Set by the rider's own browser (Geolocation API)
    // while they have an active accepted/started ride, so the driver can
    // see roughly where to find them — the reverse of the existing
    // driver-location-to-rider sharing. Overwrites the same three columns
    // every time, so this never grows storage no matter how long the
    // account exists or how many rides it takes.
    private Double currentLat;
    private Double currentLng;
    private LocalDateTime locationUpdatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

    }
}
