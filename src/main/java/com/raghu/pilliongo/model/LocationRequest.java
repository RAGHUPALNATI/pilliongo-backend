package com.raghu.pilliongo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// A rider/driver asking the developer for a brand-new fixed-fare route that
// doesn't exist in the app yet. Once approved, it becomes a real
// RouteLocation (and both place names become selectable everywhere via
// LocationService.ensureKnown) automatically — no separate manual step.
@Entity
@Table(name = "location_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String fromLocation;

    @Column(nullable = false)
    private String toLocation;

    @Column(nullable = false)
    private Double suggestedFare;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum RequestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
