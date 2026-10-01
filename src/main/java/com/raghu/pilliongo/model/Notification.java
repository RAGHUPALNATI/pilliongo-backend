package com.raghu.pilliongo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// In-app notification center — deliberately only written at real events
// (a ride's status actually changing, a support reply, a location request
// being decided), never on a poll or a page view. That keeps row count
// proportional to actual activity instead of growing with how often
// someone has the app open, which is what actually matters for keeping
// this cheap to store long-term.
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who this notification is for.
    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    // Short machine-readable category (RIDE_ACCEPTED, RIDE_STARTED,
    // RIDE_COMPLETED, RIDE_CANCELLED, SUPPORT_REPLY, LOCATION_APPROVED,
    // LOCATION_REJECTED, SOS_ALERT) — lets the frontend pick an icon
    // without parsing the message text.
    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 300)
    private String message;

    // Optional deep-link target — set for ride-related notifications so the
    // frontend can route straight to /ride/{id} on click.
    private Long relatedRideId;

    // Mapped to "is_read" — "read" is a reserved word in MySQL and breaks
    // generated INSERT/UPDATE statements if used as a raw column name.
    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
