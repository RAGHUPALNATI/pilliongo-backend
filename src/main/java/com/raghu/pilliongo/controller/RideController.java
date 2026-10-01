package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.dto.RideRequest;
import com.raghu.pilliongo.dto.RideResponse;
import com.raghu.pilliongo.service.RideService;
import com.raghu.pilliongo.util.FareCalculator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class RideController {

    private final RideService rideService;

    // RIDER — create ride
    @PostMapping
    public ResponseEntity<RideResponse> createRide(
            @Valid @RequestBody RideRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.createRide(request, auth.getName()));
    }

    // DRIVER — publish planned route offer
    @PostMapping("/offer")
    public ResponseEntity<RideResponse> offerPlannedRide(
            @Valid @RequestBody RideRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.offerPlannedRide(request, auth.getName()));
    }

    // DRIVER — publish an instant "driving right now" ride offer
    @PostMapping("/offer-instant")
    public ResponseEntity<RideResponse> offerInstantRide(
            @Valid @RequestBody RideRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.offerInstantRide(request, auth.getName()));
    }

    // RIDER — browse live instant offers posted by drivers
    @GetMapping("/instant-offers")
    public ResponseEntity<List<RideResponse>> getInstantOffers() {
        return ResponseEntity.ok(rideService.getAvailableInstantOffers());
    }

    // RIDER — book a seat on a driver's offer (planned or instant)
    @PutMapping("/{id}/book")
    public ResponseEntity<RideResponse> bookDriverOffer(
            @PathVariable Long id,
            @RequestParam(required = false) Integer seats,
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.bookDriverOffer(id, seats, auth.getName()));
    }

    // DRIVER — available rides
    @GetMapping("/available")
    public ResponseEntity<List<RideResponse>> getAvailableRides(
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.getAvailableRides(auth.getName()));
    }

    // PUBLIC — planned rides bulletin board
    @GetMapping("/planned")
    public ResponseEntity<List<RideResponse>> getPlannedRides() {
        return ResponseEntity.ok(rideService.getPlannedRides());
    }

    // Live fare estimate for a pickup/destination pair, computed the exact
    // same way a real ride would be (same fixed-fare zone overrides, same
    // distance formula) — just without creating a Ride row. Lets the
    // request forms show a price the moment both locations are picked,
    // before the rider commits to posting anything.
    @GetMapping("/fare-estimate")
    public ResponseEntity<Map<String, Object>> getFareEstimate(
            @RequestParam String from,
            @RequestParam String to) {
        double fare = rideService.calculateFare(from, to);
        double distanceKm = FareCalculator.getDistance(from, to);
        return ResponseEntity.ok(Map.of(
                "fare", fare,
                "distanceKm", distanceKm
        ));
    }

    // DRIVER — accept ride
    @PutMapping("/{id}/accept")
    public ResponseEntity<RideResponse> acceptRide(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.acceptRide(id, auth.getName()));
    }

    // DRIVER — start ride
    @PutMapping("/{id}/start")
    public ResponseEntity<RideResponse> startRide(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.startRide(id, auth.getName()));
    }

    // DRIVER — complete ride
    @PutMapping("/{id}/complete")
    public ResponseEntity<RideResponse> completeRide(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.completeRide(id, auth.getName()));
    }

    // RIDER — live location ping, the reverse direction of DriverController
    // PUT /driver/location. Body: {"lat": .., "lng": ..}. Only accepted
    // while this ride is ACCEPTED/STARTED and the caller is its rider.
    @PutMapping("/{id}/rider-location")
    public ResponseEntity<Map<String, Object>> updateRiderLocation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Double lat = body.get("lat") != null ? Double.valueOf(body.get("lat").toString()) : null;
        Double lng = body.get("lng") != null ? Double.valueOf(body.get("lng").toString()) : null;
        return ResponseEntity.ok(rideService.updateRiderLocation(id, lat, lng, auth.getName()));
    }

    // RIDER or DRIVER — confirm the cash fare has been settled in person
    @PutMapping("/{id}/mark-paid")
    public ResponseEntity<RideResponse> markAsPaid(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(rideService.markAsPaid(id, auth.getName()));
    }

    // RIDER or DRIVER — cancel ride
    @DeleteMapping("/{id}")
    public ResponseEntity<String> cancelRide(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.cancelRide(id, auth.getName()));
    }

    // RIDER or DRIVER — history
    @GetMapping("/history")
    public ResponseEntity<List<RideResponse>> getHistory(
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.getRideHistory(auth.getName()));
    }

    // get single ride
    @GetMapping("/{id}")
    public ResponseEntity<RideResponse> getRide(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(
                rideService.getRideById(id, auth.getName()));
    }
}