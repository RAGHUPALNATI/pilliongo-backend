package com.raghu.pilliongo.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "route_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NOT globally unique — the same place can have several fixed-fare rows,
    // one per distinct "from" (e.g. "Butani Colony" priced separately from
    // LPU Main Gate and from Meheru), so the actual unique identity of a row
    // is the (name, fromLocation) PAIR, enforced in AdminService rather than
    // here. A legacy single-column unique index from before this existed is
    // dropped on boot by SchemaPatchRunner (ddl-auto=update never removes a
    // constraint on its own).
    @Column(nullable = false)
    private String name;

    // Which pickup this fixed fare applies from. Left nullable (rather than
    // NOT NULL) so adding this column never breaks on a database that
    // already has rows — SchemaPatchRunner backfills any existing NULL rows
    // to the campus hub. RideService.calculateFare() checks this both ways
    // (name-as-destination-from-here, and name-as-pickup-going-there).
    private String fromLocation;

    @Column(nullable = false)
    private Double fare;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
