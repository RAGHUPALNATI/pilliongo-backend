package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.DriverProfile;
import com.raghu.pilliongo.model.Ride;
import com.raghu.pilliongo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface RideRepository extends JpaRepository<Ride,Long> {
    List<Ride> findByRider(User rider);
    List<Ride> findByDriver(DriverProfile driver);
    List<Ride> findByStatus(Ride.RideStatus status);
    List<Ride> findByRideType(Ride.RideType rideType);
    List<Ride> findByParentOfferId(Long parentOfferId);

    // Targeted queries for the screens that refresh every few seconds.
    // Each one is backed by an index on Ride (see @Table there), so the
    // database reads only the matching rows instead of the whole table.
    List<Ride> findByStatusAndRideTypeAndCreatedAtBefore(Ride.RideStatus status, Ride.RideType rideType, LocalDateTime cutoff);
    List<Ride> findByStatusAndDriverIsNull(Ride.RideStatus status);
    List<Ride> findByRideTypeAndStatusAndDriverIsNotNullAndRiderIsNull(Ride.RideType rideType, Ride.RideStatus status);
    List<Ride> findByRideTypeAndStatusAndScheduledDateGreaterThanEqual(Ride.RideType rideType, Ride.RideStatus status, LocalDate date);

}
