package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.DriverProfile;
import com.raghu.pilliongo.model.User;
import com.raghu.pilliongo.repository.DriverProfileRepository;
import com.raghu.pilliongo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;

    private DriverProfile getDriver(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return driverProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Driver profile not found"));
    }

    @PreAuthorize("hasRole('DRIVER')")
    public Map<String, Object> toggleAvailability(String email, Boolean available) {
        DriverProfile profile = getDriver(email);
        boolean newStatus = available != null ? available : !profile.isAvailable();
        profile.setAvailable(newStatus);
        driverProfileRepository.save(profile);

        Map<String, Object> map = new HashMap<>();
        map.put("available", profile.isAvailable());
        map.put("message", "Availability updated to " + profile.isAvailable());
        return map;
    }

    // Called by the driver's own browser (Geolocation API) while they have
    // an active ride — overwrites the same two columns every time, so this
    // never grows the database no matter how often it's called.
    @PreAuthorize("hasRole('DRIVER')")
    public Map<String, Object> updateLocation(String email, Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new RuntimeException("lat and lng are required");
        }
        DriverProfile profile = getDriver(email);
        profile.setCurrentLat(lat);
        profile.setCurrentLng(lng);
        profile.setLocationUpdatedAt(LocalDateTime.now());
        driverProfileRepository.save(profile);

        Map<String, Object> map = new HashMap<>();
        map.put("currentLat", profile.getCurrentLat());
        map.put("currentLng", profile.getCurrentLng());
        map.put("locationUpdatedAt", profile.getLocationUpdatedAt());
        return map;
    }

    @PreAuthorize("hasRole('DRIVER')")
    public Map<String, Object> getEarnings(String email) {
        DriverProfile profile = getDriver(email);
        Map<String, Object> map = new HashMap<>();
        map.put("totalEarnings", profile.getTotalEarnings());
        map.put("available", profile.isAvailable());
        map.put("vehicleType", profile.getVehicleType());
        map.put("vehicleModel", profile.getVehicleModel());
        map.put("vehiclePlate", profile.getVehiclePlate());
        return map;
    }
}
