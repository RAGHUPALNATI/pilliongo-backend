package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.model.LocationRequest;
import com.raghu.pilliongo.service.LocationRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/location-requests")
@RequiredArgsConstructor
public class LocationRequestController {

    private final LocationRequestService locationRequestService;

    @PostMapping
    public ResponseEntity<LocationRequest> submitRequest(
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        return ResponseEntity.ok(locationRequestService.submitRequest(auth.getName(), body));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Map<String, Object>>> getMyRequests(Authentication auth) {
        return ResponseEntity.ok(locationRequestService.getMyRequests(auth.getName()));
    }
}
