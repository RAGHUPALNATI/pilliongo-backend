package com.raghu.pilliongo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;


@Entity
// Indexes for the lookups that run constantly (dashboards refreshing, the
// expiry timer): open rides by status/type, the planned board by date, and
// a multi-seat offer's bookings. Hibernate creates them at startup.
@Table(name="rides", indexes = {
        @Index(name = "idx_rides_status_type_created", columnList = "status, ride_type, created_at"),
        @Index(name = "idx_rides_type_status_date", columnList = "ride_type, status, scheduled_date"),
        @Index(name = "idx_rides_parent_offer", columnList = "parent_offer_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // @Data generates toString() for this class. Excluding these two
    // relationship fields keeps a logged/printed Ride from pulling in the
    // full User/DriverProfile object graph (and is what would actually
    // guard against infinite recursion if those classes ever gain a
    // back-reference to Ride).
    @ToString.Exclude
    @ManyToOne
    @JoinColumn(name="rider_id", nullable=true)
    private User rider;

    @ToString.Exclude
    @ManyToOne
    @JoinColumn(name="driver_id")
    private DriverProfile driver;


    @Column(nullable=false)
    private String pickupLocation;
    @Column(nullable=false)
    private String destination;
    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private RideType rideType;
    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private RideStatus status;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    @Column(length=250)
    private String description;
    private Double fare;

    // Snapshot of the specific Vehicle the driver picked when they offered
    // this ride (see VehicleController/VehicleService) — NOT a live
    // reference. Storing a copy of the type/model/plate here, rather than
    // always reading DriverProfile's single vehicle fields, is what
    // actually makes "select one vehicle per ride" mean something: a
    // driver with two vehicles can offer one ride on the bike and another
    // on the car, and ride history keeps showing the right one even if
    // they later edit or delete that vehicle from their account.
    private String vehicleType;
    private String vehicleModel;
    private String vehiclePlate;

    // ---- Multi-seat carpooling ----
    // On a DRIVER-posted offer (rider == null): seatsTotal is how many
    // passengers the driver can take, seatsBooked how many are already
    // taken. On a BOOKING row (rider != null): seatCount is how many seats
    // that rider holds. A booking created from a multi-seat offer points
    // back at it through parentOfferId; the offer itself stays REQUESTED
    // (open) until every seat is gone, then drops off the public board.
    // All nullable so rows saved before this feature still load: null
    // seatsTotal/seatCount mean 1, null seatsBooked means 0.
    private Integer seatsTotal;
    private Integer seatsBooked;
    private Integer seatCount;
    private Long parentOfferId;

    public int effectiveSeatsTotal() { return seatsTotal == null || seatsTotal < 1 ? 1 : seatsTotal; }
    public int effectiveSeatsBooked() { return seatsBooked == null ? 0 : seatsBooked; }
    public int effectiveSeatCount() { return seatCount == null || seatCount < 1 ? 1 : seatCount; }
    public int seatsRemaining() { return Math.max(0, effectiveSeatsTotal() - effectiveSeatsBooked()); }

    // Cash-settlement confirmation — either party on the ride can mark it
    // once money's actually changed hands in person. Real in-app payments
    // are a later phase; this is just a "we settled up" checkbox so it
    // doesn't get lost/forgotten.
    @Builder.Default
    private boolean paid = false;
    private LocalDateTime paidAt;

    @Column(updatable=false)
    private LocalDateTime createdAt;
    @Column(nullable=false)
    private LocalDateTime updatedAt;

    // Optimistic locking: Hibernate checks this column on every UPDATE and
    // throws ObjectOptimisticLockingFailureException if it changed since the
    // row was read. This is what actually stops two drivers who both loaded
    // the same REQUESTED ride from both successfully accepting it — the
    // second save fails instead of silently overwriting the first.
    @Version
    private Long version;

    @PrePersist

    protected  void  onCreate(){
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }


    @PreUpdate
    protected  void  onUpdate(){
        updatedAt = LocalDateTime.now();
    }
    public enum RideType{
        INSTANT,PLANNED
    }
    public enum RideStatus{
        REQUESTED,ACCEPTED,STARTED,COMPLETED,CANCELLED,EXPIRED
    }


}
