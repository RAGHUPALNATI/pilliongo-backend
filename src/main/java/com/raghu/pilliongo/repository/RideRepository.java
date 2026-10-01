package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.DriverProfile;
import com.raghu.pilliongo.model.Ride;
import com.raghu.pilliongo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RideRepository extends JpaRepository<Ride,Long> {
    List<Ride> findByRider(User rider);
    List<Ride> findByDriver(DriverProfile driver);
    List<Ride> findByStatus(Ride.RideStatus status);
    List<Ride> findByRideType(Ride.RideType rideType);
    List<Ride> findByParentOfferId(Long parentOfferId);

}
