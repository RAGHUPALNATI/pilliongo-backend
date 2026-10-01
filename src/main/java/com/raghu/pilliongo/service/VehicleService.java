package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.DriverProfile;
import com.raghu.pilliongo.model.User;
import com.raghu.pilliongo.model.Vehicle;
import com.raghu.pilliongo.repository.DriverProfileRepository;
import com.raghu.pilliongo.repository.UserRepository;
import com.raghu.pilliongo.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;

    private DriverProfile getDriver(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return driverProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Driver profile not found"));
    }

    @PreAuthorize("hasRole('DRIVER')")
    public List<Vehicle> getMyVehicles(String email) {
        return vehicleRepository.findByDriver(getDriver(email));
    }

    @PreAuthorize("hasRole('DRIVER')")
    public Vehicle addVehicle(String email, String vehicleType, String vehicleModel, String vehiclePlate) {
        if (vehicleType == null || vehicleType.isBlank()) {
            throw new RuntimeException("Vehicle type is required");
        }
        if (vehicleModel == null || vehicleModel.isBlank()) {
            throw new RuntimeException("Vehicle model is required");
        }
        if (vehiclePlate == null || vehiclePlate.isBlank()) {
            throw new RuntimeException("Vehicle plate is required");
        }

        String normalizedType = vehicleType.trim();

        DriverProfile driver = getDriver(email);
        List<Vehicle> existing = vehicleRepository.findByDriver(driver);

        // At most 2 vehicles per driver, and never two of the same type —
        // one Bike and one Car, nothing more.
        if (existing.size() >= 2) {
            throw new RuntimeException("You can have at most 2 vehicles on your account (one Bike and one Car). Remove one before adding another.");
        }
        boolean alreadyHasType = existing.stream()
                .anyMatch(v -> v.getVehicleType().equalsIgnoreCase(normalizedType));
        if (alreadyHasType) {
            throw new RuntimeException("You already have a " + normalizedType + " on file. You can only add one Bike and one Car.");
        }
        boolean alreadyHasPlate = existing.stream()
                .anyMatch(v -> v.getVehiclePlate().equalsIgnoreCase(vehiclePlate.trim()));
        if (alreadyHasPlate) {
            throw new RuntimeException("You already have a vehicle with that plate number");
        }

        Vehicle vehicle = Vehicle.builder()
                .driver(driver)
                .vehicleType(normalizedType)
                .vehicleModel(vehicleModel.trim())
                .vehiclePlate(vehiclePlate.trim().toUpperCase())
                // Your very first vehicle is automatically the primary one —
                // there's nothing else it could be.
                .primaryVehicle(existing.isEmpty())
                .build();

        Vehicle saved = vehicleRepository.save(vehicle);
        if (saved.isPrimaryVehicle()) {
            syncPrimaryToDriverProfile(driver, saved);
        }
        return saved;
    }

    // A driver must always have at least one vehicle on file (every ride
    // offer needs one to select), so the last remaining vehicle can't be
    // deleted — only replaced by adding a new one first.
    @PreAuthorize("hasRole('DRIVER')")
    public String deleteVehicle(String email, Long id) {
        DriverProfile driver = getDriver(email);
        Vehicle vehicle = vehicleRepository.findByIdAndDriver(id, driver)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        List<Vehicle> all = vehicleRepository.findByDriver(driver);
        if (all.size() <= 1) {
            throw new RuntimeException("You must keep at least one vehicle on your account. Add another one before removing this.");
        }

        boolean wasPrimary = vehicle.isPrimaryVehicle();
        vehicleRepository.delete(vehicle);

        if (wasPrimary) {
            // Promote whichever vehicle is left as the new primary so the
            // driver never ends up with none — pick the earliest-added one.
            all.stream()
                    .filter(v -> !v.getId().equals(id))
                    .min((a, b) -> a.getId().compareTo(b.getId()))
                    .ifPresent(next -> {
                        next.setPrimaryVehicle(true);
                        vehicleRepository.save(next);
                        syncPrimaryToDriverProfile(driver, next);
                    });
        }

        return "Vehicle removed";
    }

    // Lets a driver switch which of their (at most 2) vehicles is the
    // primary one — shown on their dashboard header and used as the
    // DriverProfile-level fallback vehicle wherever a ride doesn't carry
    // its own vehicle snapshot.
    @PreAuthorize("hasRole('DRIVER')")
    public Vehicle setPrimaryVehicle(String email, Long id) {
        DriverProfile driver = getDriver(email);
        Vehicle target = vehicleRepository.findByIdAndDriver(id, driver)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        List<Vehicle> all = vehicleRepository.findByDriver(driver);
        for (Vehicle v : all) {
            boolean shouldBePrimary = v.getId().equals(id);
            if (v.isPrimaryVehicle() != shouldBePrimary) {
                v.setPrimaryVehicle(shouldBePrimary);
                vehicleRepository.save(v);
            }
        }

        syncPrimaryToDriverProfile(driver, target);
        return target;
    }

    // DriverProfile keeps its own vehicleType/Model/Plate fields as the
    // "primary vehicle" fallback that pre-dates the multi-vehicle feature
    // (dashboard header, ride responses that fall back when a ride has no
    // vehicle snapshot of its own). Whenever primary changes, mirror it
    // here so those old call sites keep showing the right vehicle without
    // needing to know the Vehicle table exists.
    private void syncPrimaryToDriverProfile(DriverProfile driver, Vehicle primary) {
        driver.setVehicleType(primary.getVehicleType());
        driver.setVehicleModel(primary.getVehicleModel());
        driver.setVehiclePlate(primary.getVehiclePlate());
        driverProfileRepository.save(driver);
    }
}
