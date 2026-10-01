package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Covered by the default "anyRequest().authenticated()" rule in
// SecurityConfig — every page that needs this list (dashboards, plan-ride,
// admin) is already behind a logged-in session, so no extra security
// matcher is needed here.
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public ResponseEntity<List<String>> getAllLocations() {
        return ResponseEntity.ok(locationService.getAllLocationNames());
    }
}
