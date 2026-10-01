package com.raghu.pilliongo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// The canonical list of place names selectable anywhere in the app (pickup
// and destination dropdowns). Separate from RouteLocation (which is a FARE
// RULE — "this specific route costs this much") because a place can be
// selectable without having a fixed fare (it just falls back to the
// distance formula). New rows get added automatically — see
// LocationService.ensureKnown() — whenever an admin adds a fare rule for a
// new place, or approves a user's location request.
@Entity
@Table(name = "known_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnownLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // Map position. Optional: when BOTH ends of a ride have one, the fare
    // uses the real distance between them (see DistanceService), so a new
    // city works without anyone hand-typing a distance table. Filled in by
    // an admin, either typed or auto-found via OpenStreetMap.
    private Double latitude;
    private Double longitude;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
