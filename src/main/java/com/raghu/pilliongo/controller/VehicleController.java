package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.model.Vehicle;
import com.raghu.pilliongo.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Nested under /api/driver/** on purpose — SecurityConfig already locks
// that whole prefix to hasRole("DRIVER"), so this reuses that rule instead
// of needing its own security matcher.
@RestController
@RequestMapping("/api/driver/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public ResponseEntity<List<Vehicle>> getMyVehicles(Authentication auth) {
        return ResponseEntity.ok(vehicleService.getMyVehicles(auth.getName()));
    }

    @PostMapping
    public ResponseEntity<Vehicle> addVehicle(@RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(vehicleService.addVehicle(
                auth.getName(),
                body.get("vehicleType"),
                body.get("vehicleModel"),
                body.get("vehiclePlate")
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteVehicle(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(vehicleService.deleteVehicle(auth.getName(), id));
    }

    @PutMapping("/{id}/primary")
    public ResponseEntity<Vehicle> setPrimaryVehicle(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(vehicleService.setPrimaryVehicle(auth.getName(), id));
    }
}
