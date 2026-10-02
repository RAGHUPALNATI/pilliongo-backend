package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.model.KnownLocation;
import com.raghu.pilliongo.model.RouteLocation;
import com.raghu.pilliongo.service.AdminService;
import com.raghu.pilliongo.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final LocationService locationService;

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(
            @PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserById(id));
    }

    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<String> suspendUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.suspendUser(id));
    }

    @PutMapping("/users/{id}/activate")
    public ResponseEntity<String> activateUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.activateUser(id));
    }

    // Hard delete — wipes the account and every ride/vehicle/support
    // message/location request tied to it. Irreversible.
    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.deleteUser(id));
    }

    @GetMapping("/rides")
    public ResponseEntity<List<Map<String, Object>>> getAllRides() {
        return ResponseEntity.ok(adminService.getAllRides());
    }

    // ADMIN — force a ride's status directly (bypasses the rider/driver
    // ownership check that the normal accept/start/complete/cancel
    // endpoints enforce). Body: {"status": "COMPLETED"} or {"status": "CANCELLED"}.
    @PutMapping("/rides/{id}/status")
    public ResponseEntity<String> overrideRideStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return ResponseEntity.ok(adminService.overrideRideStatus(id, body.get("status"), auth.getName()));
    }

    @GetMapping("/destinations")
    public ResponseEntity<List<RouteLocation>> getDestinations() {
        return ResponseEntity.ok(adminService.getDestinations());
    }

    @PostMapping("/destinations")
    public ResponseEntity<RouteLocation> addDestination(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.addDestination(body));
    }

    @PutMapping("/destinations/{id}")
    public ResponseEntity<RouteLocation> updateDestination(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateDestination(id, body));
    }

    @DeleteMapping("/destinations/{id}")
    public ResponseEntity<String> deleteDestination(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.deleteDestination(id));
    }

    // KNOWN LOCATIONS — the master list of selectable place names (every
    // pickup/destination dropdown reads from this), separate from the
    // fare-rule table above. Renaming/deleting here is what actually makes
    // a typo or a junk test entry disappear from the app everywhere.
    @GetMapping("/known-locations")
    public ResponseEntity<List<KnownLocation>> getKnownLocations() {
        return ResponseEntity.ok(adminService.getKnownLocations());
    }

    @PutMapping("/known-locations/{id}")
    public ResponseEntity<KnownLocation> renameKnownLocation(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminService.renameKnownLocation(id, body.get("name")));
    }

    // Add a new place (any city). Body: {name, latitude?, longitude?}.
    // Without coordinates it's auto-located on OpenStreetMap if possible.
    @PostMapping("/known-locations")
    public ResponseEntity<KnownLocation> addKnownLocation(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(locationService.addKnown(
                (String) body.get("name"), toDouble(body.get("latitude")), toDouble(body.get("longitude"))));
    }

    // Set (or clear, with nulls) a place's map position by hand.
    @PutMapping("/known-locations/{id}/coordinates")
    public ResponseEntity<KnownLocation> setKnownLocationCoordinates(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(locationService.setCoordinates(
                id, toDouble(body.get("latitude")), toDouble(body.get("longitude"))));
    }

    @PostMapping("/known-locations/{id}/geocode")
    public ResponseEntity<KnownLocation> geocodeKnownLocation(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.geocode(id));
    }

    @PostMapping("/known-locations/geocode-missing")
    public ResponseEntity<Map<String, Object>> geocodeMissingLocations() {
        return ResponseEntity.ok(locationService.geocodeMissing());
    }

    private static Double toDouble(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return Double.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Coordinates must be numbers, e.g. 31.2536");
        }
    }

    @DeleteMapping("/known-locations/{id}")
    public ResponseEntity<String> deleteKnownLocation(@PathVariable Long id) {
        adminService.deleteKnownLocation(id);
        return ResponseEntity.ok("Location deleted.");
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        return ResponseEntity.ok(adminService.getAnalytics());
    }

    // DRIVER EARNINGS BREAKDOWN — total platform earnings already exists in
    // getAnalytics() (totalFareVolume); this is the per-driver split of it.
    @GetMapping("/driver-earnings")
    public ResponseEntity<List<Map<String, Object>>> getDriverEarnings() {
        return ResponseEntity.ok(adminService.getDriverEarnings());
    }

    // SUPPORT INBOX — view, reply to, and delete in-app help messages.
    @GetMapping("/support")
    public ResponseEntity<List<Map<String, Object>>> getAllSupportMessages() {
        return ResponseEntity.ok(adminService.getAllSupportMessages());
    }

    @PutMapping("/support/{id}/reply")
    public ResponseEntity<Map<String, Object>> replyToSupportMessage(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminService.replyToSupportMessage(id, body.get("reply")));
    }

    @DeleteMapping("/support/{id}")
    public ResponseEntity<String> deleteSupportMessage(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.deleteSupportMessage(id));
    }

    // LOCATION REQUESTS — users ask for a brand-new fixed-fare route; admin
    // approves (creates the real route + makes it selectable everywhere) or
    // rejects.
    @GetMapping("/location-requests")
    public ResponseEntity<List<Map<String, Object>>> getLocationRequests() {
        return ResponseEntity.ok(adminService.getLocationRequests());
    }

    @PutMapping("/location-requests/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveLocationRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(adminService.approveLocationRequest(id, body));
    }

    @PutMapping("/location-requests/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectLocationRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(adminService.rejectLocationRequest(id, reason));
    }

    // SOS ALERTS — riders/drivers trigger these via POST /api/sos; admin
    // views and resolves them here.
    @GetMapping("/sos")
    public ResponseEntity<List<Map<String, Object>>> getSosAlerts() {
        return ResponseEntity.ok(adminService.getSosAlerts());
    }

    @PutMapping("/sos/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveSosAlert(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.resolveSosAlert(id));
    }
}