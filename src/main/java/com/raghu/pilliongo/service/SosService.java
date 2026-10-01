package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.Ride;
import com.raghu.pilliongo.model.SosAlert;
import com.raghu.pilliongo.model.User;
import com.raghu.pilliongo.repository.RideRepository;
import com.raghu.pilliongo.repository.SosAlertRepository;
import com.raghu.pilliongo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SosService {

    private final SosAlertRepository sosAlertRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // RIDER or DRIVER — press the SOS button. Deliberately doesn't require
    // an active ride (someone might press it right after a ride ends, or
    // before one officially starts) but links the ride when one is given so
    // the admin has full context.
    public Map<String, Object> triggerSos(String email, Long rideId, Double lat, Double lng, String message) {
        User user = getCurrentUser(email);
        Ride ride = rideId != null ? rideRepository.findById(rideId).orElse(null) : null;

        SosAlert saved = sosAlertRepository.save(
                SosAlert.builder()
                        .ride(ride)
                        .user(user)
                        .lat(lat)
                        .lng(lng)
                        .message(message)
                        .status(SosAlert.SosStatus.OPEN)
                        .build()
        );

        log.warn("SOS ALERT from {} (ride #{}) at [{}, {}]", email, rideId, lat, lng);

        String routeInfo = ride != null ? (" on ride " + ride.getPickupLocation() + " → " + ride.getDestination()) : "";
        notificationService.notifyAllAdmins(
                "SOS_ALERT",
                user.getFullName() + " pressed SOS" + routeInfo + ". Check Admin Portal → SOS Alerts immediately.",
                rideId
        );

        return toMap(saved);
    }

    // ADMIN
    public List<Map<String, Object>> getAllAlerts() {
        return sosAlertRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toMap)
                .toList();
    }

    public Map<String, Object> resolveAlert(Long id) {
        SosAlert alert = sosAlertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SOS alert not found"));
        alert.setStatus(SosAlert.SosStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        return toMap(sosAlertRepository.save(alert));
    }

    private Map<String, Object> toMap(SosAlert a) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", a.getId());
        map.put("userName", a.getUser() != null ? a.getUser().getFullName() : null);
        map.put("userPhone", a.getUser() != null ? a.getUser().getPhone() : null);
        map.put("userRole", a.getUser() != null ? a.getUser().getRole().name() : null);
        map.put("rideId", a.getRide() != null ? a.getRide().getId() : null);
        map.put("pickupLocation", a.getRide() != null ? a.getRide().getPickupLocation() : null);
        map.put("destination", a.getRide() != null ? a.getRide().getDestination() : null);
        map.put("lat", a.getLat());
        map.put("lng", a.getLng());
        map.put("message", a.getMessage());
        map.put("status", a.getStatus().name());
        map.put("createdAt", a.getCreatedAt());
        map.put("resolvedAt", a.getResolvedAt());
        return map;
    }
}
