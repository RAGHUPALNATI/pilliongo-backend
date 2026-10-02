package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.service.SosService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// RIDER/DRIVER-facing SOS trigger. Admin-side viewing/resolving lives on
// AdminController (GET /api/admin/sos, PUT /api/admin/sos/{id}/resolve) to
// keep every admin-only endpoint under the one /api/admin prefix.
@RestController
@RequestMapping("/api/sos")
@RequiredArgsConstructor
public class SosController {

    private final SosService sosService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> triggerSos(
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth) {
        Long rideId = null;
        Double lat = null;
        Double lng = null;
        String message = null;
        if (body != null) {
            if (body.get("rideId") != null) rideId = Long.valueOf(body.get("rideId").toString());
            if (body.get("lat") != null) lat = Double.valueOf(body.get("lat").toString());
            if (body.get("lng") != null) lng = Double.valueOf(body.get("lng").toString());
            if (body.get("message") != null) message = body.get("message").toString();
        }
        return ResponseEntity.ok(sosService.triggerSos(auth.getName(), rideId, lat, lng, message));
    }
}
