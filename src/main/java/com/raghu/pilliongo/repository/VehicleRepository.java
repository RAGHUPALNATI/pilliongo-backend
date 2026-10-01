package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.DriverProfile;
import com.raghu.pilliongo.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByDriver(DriverProfile driver);
    Optional<Vehicle> findByIdAndDriver(Long id, DriverProfile driver);
}
