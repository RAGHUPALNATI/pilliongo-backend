package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.service.DriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PutMapping("/availability")
    public ResponseEntity<Map<String, Object>> toggleAvailability(
            @RequestBody(required = false) Map<String, Boolean> body,
            Authentication auth) {
        Boolean available = body != null ? body.get("available") : null;
        return ResponseEntity.ok(driverService.toggleAvailability(auth.getName(), available));
    }

    @GetMapping("/earnings")
    public ResponseEntity<Map<String, Object>> getEarnings(Authentication auth) {
        return ResponseEntity.ok(driverService.getEarnings(auth.getName()));
    }

    // Live location ping — the driver's browser calls this every ~10-15s
    // while they have an active ride. Body: {"lat": .., "lng": ..}
    @PutMapping("/location")
    public ResponseEntity<Map<String, Object>> updateLocation(
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Double lat = body.get("lat") != null ? Double.valueOf(body.get("lat").toString()) : null;
        Double lng = body.get("lng") != null ? Double.valueOf(body.get("lng").toString()) : null;
        return ResponseEntity.ok(driverService.updateLocation(auth.getName(), lat, lng));
    }
}
