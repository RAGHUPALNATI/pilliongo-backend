package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.LocationRequest;
import com.raghu.pilliongo.model.RouteLocation;
import com.raghu.pilliongo.model.User;
import com.raghu.pilliongo.repository.LocationRequestRepository;
import com.raghu.pilliongo.repository.RouteLocationRepository;
import com.raghu.pilliongo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LocationRequestService {

    private final LocationRequestRepository locationRequestRepository;
    private final RouteLocationRepository routeLocationRepository;
    private final UserRepository userRepository;
    private final LocationService locationService;
    private final NotificationService notificationService;

    private User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public LocationRequest submitRequest(String email, Map<String, Object> body) {
        User user = getCurrentUser(email);

        String fromLocation = body.get("fromLocation") != null ? body.get("fromLocation").toString().trim() : null;
        String toLocation = body.get("toLocation") != null ? body.get("toLocation").toString().trim() : null;
        Object fareObj = body.get("suggestedFare");
        String notes = body.get("notes") != null ? body.get("notes").toString().trim() : null;

        if (fromLocation == null || fromLocation.isBlank()) {
            throw new RuntimeException("From location is required");
        }
        if (toLocation == null || toLocation.isBlank()) {
            throw new RuntimeException("To location is required");
        }
        if (fromLocation.equalsIgnoreCase(toLocation)) {
            throw new RuntimeException("From and to locations cannot be the same");
        }
        if (fareObj == null) {
            throw new RuntimeException("A suggested fare is required");
        }

        Double suggestedFare;
        try {
            suggestedFare = Double.valueOf(fareObj.toString());
        } catch (NumberFormatException e) {
            throw new RuntimeException("Suggested fare must be a number");
        }
        if (suggestedFare <= 0) {
            throw new RuntimeException("Suggested fare must be greater than 0");
        }

        LocationRequest saved = locationRequestRepository.save(
                LocationRequest.builder()
                        .user(user)
                        .fromLocation(fromLocation)
                        .toLocation(toLocation)
                        .suggestedFare(suggestedFare)
                        .notes(notes)
                        .status(LocationRequest.RequestStatus.PENDING)
                        .build()
        );

        return saved;
    }

    public List<Map<String, Object>> getMyRequests(String email) {
        User user = getCurrentUser(email);
        return locationRequestRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toMap)
                .toList();
    }

    // ADMIN
    public List<Map<String, Object>> getAllRequests() {
        return locationRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toMap)
                .toList();
    }

    public Map<String, Object> approveRequest(Long id, Map<String, Object> body) {
        LocationRequest req = locationRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location request not found"));

        if (req.getStatus() != LocationRequest.RequestStatus.PENDING) {
            throw new RuntimeException("This request has already been decided");
        }

        // Admin may adjust the fare before approving; otherwise the
        // rider/driver's suggested fare is used as-is.
        Double finalFare = req.getSuggestedFare();
        if (body != null && body.get("fare") != null) {
            try {
                finalFare = Double.valueOf(body.get("fare").toString());
            } catch (NumberFormatException e) {
                throw new RuntimeException("Fare must be a number");
            }
        }

        // Turn it into a real, selectable fixed-fare route. If a route for
        // this EXACT (destination, origin) pair already exists, refresh it
        // instead of failing — the approval should always succeed once
        // decided. A different origin for the same destination name is a
        // distinct route (e.g. this place may already be fixed-priced from
        // the campus hub, and this request fixes it from somewhere else),
        // so that becomes its own new row rather than overwriting the
        // existing one.
        RouteLocation route = routeLocationRepository.findAllByName(req.getToLocation()).stream()
                .filter(rl -> req.getFromLocation() != null
                        && req.getFromLocation().equalsIgnoreCase(rl.getFromLocation() == null ? "" : rl.getFromLocation()))
                .findFirst()
                .orElseGet(() -> RouteLocation.builder().name(req.getToLocation()).build());
        route.setFromLocation(req.getFromLocation());
        route.setFare(finalFare);
        routeLocationRepository.save(route);

        locationService.ensureKnown(req.getFromLocation());
        locationService.ensureKnown(req.getToLocation());

        req.setStatus(LocationRequest.RequestStatus.APPROVED);
        req.setDecidedAt(LocalDateTime.now());
        LocationRequest saved = locationRequestRepository.save(req);

        notificationService.notify(
                req.getUser(),
                "LOCATION_APPROVED",
                "Your route request \"" + req.getFromLocation() + " → " + req.getToLocation() + "\" was approved and is now bookable.",
                null
        );

        return toMap(saved);
    }

    public Map<String, Object> rejectRequest(Long id, String reason) {
        LocationRequest req = locationRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location request not found"));

        if (req.getStatus() != LocationRequest.RequestStatus.PENDING) {
            throw new RuntimeException("This request has already been decided");
        }

        req.setStatus(LocationRequest.RequestStatus.REJECTED);
        req.setRejectionReason(reason != null && !reason.isBlank() ? reason.trim() : "Not approved by admin");
        req.setDecidedAt(LocalDateTime.now());
        LocationRequest saved = locationRequestRepository.save(req);

        notificationService.notify(
                req.getUser(),
                "LOCATION_REJECTED",
                "Your route request \"" + req.getFromLocation() + " → " + req.getToLocation() + "\" was not approved.",
                null
        );

        return toMap(saved);
    }

    private Map<String, Object> toMap(LocationRequest req) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", req.getId());
        map.put("userName", req.getUser() != null ? req.getUser().getFullName() : null);
        map.put("userEmail", req.getUser() != null ? req.getUser().getEmail() : null);
        map.put("fromLocation", req.getFromLocation());
        map.put("toLocation", req.getToLocation());
        map.put("suggestedFare", req.getSuggestedFare());
        map.put("notes", req.getNotes());
        map.put("status", req.getStatus());
        map.put("rejectionReason", req.getRejectionReason());
        map.put("createdAt", req.getCreatedAt());
        map.put("decidedAt", req.getDecidedAt());
        return map;
    }
}
