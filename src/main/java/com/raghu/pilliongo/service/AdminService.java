package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.DriverProfile;
import com.raghu.pilliongo.model.KnownLocation;
import com.raghu.pilliongo.model.Ride;
import com.raghu.pilliongo.model.Role;
import com.raghu.pilliongo.model.RouteLocation;
import com.raghu.pilliongo.model.User;
import com.raghu.pilliongo.repository.DriverProfileRepository;
import com.raghu.pilliongo.repository.LocationRequestRepository;
import com.raghu.pilliongo.repository.RideRepository;
import com.raghu.pilliongo.repository.RouteLocationRepository;
import com.raghu.pilliongo.repository.SupportMessageRepository;
import com.raghu.pilliongo.repository.UserRepository;
import com.raghu.pilliongo.repository.VehicleRepository;
import com.raghu.pilliongo.util.FareCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RideRepository rideRepository;
    private final RouteLocationRepository routeLocationRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final LocationService locationService;
    private final SupportService supportService;
    private final LocationRequestService locationRequestService;
    private final VehicleRepository vehicleRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final LocationRequestRepository locationRequestRepository;
    private final SosService sosService;

    // Distance lookup used to move here from a copy-pasted DISTANCE_MAP —
    // now delegates to the single shared FareCalculator (see util package)
    // that RideService also uses, so both stay in sync.
    public static double getRideDistance(String pickup, String destination) {
        return FareCalculator.getDistance(pickup, destination);
    }

    // get all users (never return passwords) with distanceTraveled (km)
    public List<Map<String, Object>> getAllUsers() {
        List<Ride> completedRides = rideRepository.findAll().stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.COMPLETED)
                .toList();

        return userRepository.findAll().stream().map(u -> {
            double distanceKm = completedRides.stream()
                    .filter(r -> (r.getRider() != null && r.getRider().getId().equals(u.getId()))
                            || (r.getDriver() != null && r.getDriver().getUser() != null && r.getDriver().getUser().getId().equals(u.getId())))
                    .mapToDouble(r -> getRideDistance(r.getPickupLocation(), r.getDestination()))
                    .sum();

            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("fullName", u.getFullName());
            map.put("email", u.getEmail());
            map.put("phone", u.getPhone());
            map.put("role", u.getRole().name());
            map.put("emailVerified", u.isEmailVerified());
            map.put("active", u.isActive());
            map.put("distanceTraveled", Math.round(distanceKm * 10.0) / 10.0);
            map.put("createdAt", u.getCreatedAt());

            // Table row previously never carried the driver's vehicle
            // fields at all, so the Users table's "Vehicle Details" column
            // always fell back to "Vehicle (N/A)" for every driver
            // regardless of what was actually on file.
            driverProfileRepository.findByUser(u).ifPresent(driver -> {
                map.put("vehicleType", driver.getVehicleType());
                map.put("vehicleModel", driver.getVehicleModel());
                map.put("vehiclePlate", driver.getVehiclePlate());
            });
            return map;
        }).toList();
    }

    // Full profile detail for the admin's "view user" panel — everything
    // needed to judge whether an account looks like a real, active user or
    // a throwaway/test one: contact info, verification/active state, every
    // vehicle on file (not just the primary), ride stats + a recent
    // activity trail, and how much they've used support/location requests.
    public Map<String, Object> getUserById(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> map = new HashMap<>();
        map.put("id", u.getId());
        map.put("fullName", u.getFullName());
        map.put("email", u.getEmail());
        map.put("phone", u.getPhone());
        map.put("role", u.getRole().name());
        map.put("emailVerified", u.isEmailVerified());
        map.put("active", u.isActive());
        map.put("createdAt", u.getCreatedAt());

        List<Ride> riderRides = rideRepository.findByRider(u);
        DriverProfile driverProfile = driverProfileRepository.findByUser(u).orElse(null);
        List<Ride> driverRides = driverProfile != null ? rideRepository.findByDriver(driverProfile) : List.of();

        if (driverProfile != null) {
            map.put("available", driverProfile.isAvailable());
            map.put("totalEarnings", driverProfile.getTotalEarnings() != null ? driverProfile.getTotalEarnings() : 0.0);
            map.put("vehicleType", driverProfile.getVehicleType());
            map.put("vehicleModel", driverProfile.getVehicleModel());
            map.put("vehiclePlate", driverProfile.getVehiclePlate());

            List<Map<String, Object>> vehicles = vehicleRepository.findByDriver(driverProfile).stream()
                    .map(v -> {
                        Map<String, Object> vm = new HashMap<>();
                        vm.put("id", v.getId());
                        vm.put("vehicleType", v.getVehicleType());
                        vm.put("vehicleModel", v.getVehicleModel());
                        vm.put("vehiclePlate", v.getVehiclePlate());
                        vm.put("primaryVehicle", v.isPrimaryVehicle());
                        return vm;
                    })
                    .toList();
            map.put("vehicles", vehicles);
        }

        List<Ride> allTheirRides = new java.util.ArrayList<>(riderRides);
        allTheirRides.addAll(driverRides);

        long completedCount = allTheirRides.stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.COMPLETED).count();
        long cancelledCount = allTheirRides.stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.CANCELLED || r.getStatus() == Ride.RideStatus.EXPIRED)
                .count();
        double distanceKm = allTheirRides.stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.COMPLETED)
                .mapToDouble(r -> getRideDistance(r.getPickupLocation(), r.getDestination()))
                .sum();

        map.put("totalRides", allTheirRides.size());
        map.put("completedRides", completedCount);
        map.put("cancelledRides", cancelledCount);
        map.put("distanceTraveled", Math.round(distanceKm * 10.0) / 10.0);

        // Most recent 10 rides across either role, newest first — an
        // at-a-glance activity trail without leaving this panel.
        List<Map<String, Object>> recentRides = allTheirRides.stream()
                .sorted((a, b) -> {
                    LocalDateTime ac = a.getCreatedAt();
                    LocalDateTime bc = b.getCreatedAt();
                    if (ac == null || bc == null) return 0;
                    return bc.compareTo(ac);
                })
                .limit(10)
                .map(r -> {
                    Map<String, Object> rm = new HashMap<>();
                    rm.put("id", r.getId());
                    rm.put("asRole", r.getRider() != null && r.getRider().getId().equals(u.getId()) ? "RIDER" : "DRIVER");
                    rm.put("pickupLocation", r.getPickupLocation());
                    rm.put("destination", r.getDestination());
                    rm.put("rideType", r.getRideType().name());
                    rm.put("status", r.getStatus().name());
                    rm.put("fare", r.getFare());
                    rm.put("createdAt", r.getCreatedAt());
                    return rm;
                })
                .toList();
        map.put("recentRides", recentRides);

        map.put("supportMessageCount", supportMessageRepository.findAllByUserOrderByCreatedAtDesc(u).size());
        map.put("locationRequestCount", locationRequestRepository.findAllByUserOrderByCreatedAtDesc(u).size());

        return map;
    }

    // suspend user
    public String suspendUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);
        return "User " + user.getFullName() + " suspended.";
    }

    // activate user
    public String activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        userRepository.save(user);
        return "User " + user.getFullName() + " activated.";
    }

    // get all rides
    public List<Map<String, Object>> getAllRides() {
        return rideRepository.findAll().stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("riderName", r.getRider() != null ? r.getRider().getFullName() : "No Rider");
            map.put("driverName", r.getDriver() != null && r.getDriver().getUser() != null
                    ? r.getDriver().getUser().getFullName() : "No Driver");
            map.put("pickupLocation", r.getPickupLocation());
            map.put("destination", r.getDestination());
            map.put("rideType", r.getRideType().name());
            map.put("status", r.getStatus().name());
            map.put("fare", r.getFare());
            map.put("distanceKm", getRideDistance(r.getPickupLocation(), r.getDestination()));
            map.put("createdAt", r.getCreatedAt());
            return map;
        }).toList();
    }

    // ADMIN OVERRIDE — force a ride to any status directly, bypassing the
    // rider/driver ownership check that RideService.completeRide()/
    // cancelRide() enforce. An admin is never the rider or driver on
    // someone else's ride, so calling those endpoints as admin always
    // failed with "Access denied" / "This is not your ride." — this is the
    // real admin-only path the "Force Complete"/"Force Cancel" buttons need.
    public String overrideRideStatus(Long rideId, String newStatus, String adminEmail) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        Ride.RideStatus status;
        try {
            status = Ride.RideStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RuntimeException("Invalid ride status: " + newStatus);
        }

        // Only COMPLETED and CANCELLED are meant to be force-set by an
        // admin — forcing a ride to REQUESTED/ACCEPTED/STARTED/EXPIRED
        // would put it in a state the normal ride flow never produces and
        // could corrupt fare/earnings logic downstream.
        if (status != Ride.RideStatus.COMPLETED && status != Ride.RideStatus.CANCELLED) {
            throw new RuntimeException("Admin can only force a ride to COMPLETED or CANCELLED, got: " + status);
        }

        // Who forced this change and when — admin overrides bypass the
        // normal ownership checks, so this is the audit trail for that.
        log.warn("Admin {} forced ride {} to status {} at {}", adminEmail, rideId, status, LocalDateTime.now());

        ride.setStatus(status);

        // Forcing a ride to COMPLETED should still credit the driver's
        // earnings, same as the normal completeRide() flow does.
        if (status == Ride.RideStatus.COMPLETED && ride.getDriver() != null && ride.getFare() != null) {
            DriverProfile driver = ride.getDriver();
            driver.setTotalEarnings((driver.getTotalEarnings() != null ? driver.getTotalEarnings() : 0.0) + ride.getFare());
            driverProfileRepository.save(driver);
        }

        rideRepository.save(ride);
        return "Ride #" + rideId + " status forcibly set to " + status.name();
    }

    // DESTINATIONS & FARES MANAGEMENT
    public List<RouteLocation> getDestinations() {
        return routeLocationRepository.findAll();
    }

    public RouteLocation addDestination(Map<String, Object> body) {
        String name = (String) body.get("name");
        Double fare = Double.valueOf(body.get("fare").toString());
        // Defaults to the campus hub when the caller doesn't send one, so
        // the old two-field (name, fare) request shape from before this
        // still works — but the admin UI now always sends an explicit
        // "fromLocation" the admin picked.
        String fromLocation = body.get("fromLocation") != null && !body.get("fromLocation").toString().isBlank()
                ? body.get("fromLocation").toString().trim()
                : "LPU University Main Gate";

        if (name == null || name.isBlank()) {
            throw new RuntimeException("Destination name is required");
        }
        // Unique by the (name, fromLocation) PAIR, not by name alone — the
        // same place can have several fixed fares, one per distinct origin
        // (e.g. "Butani Colony" from LPU Main Gate AND, separately, from
        // Meheru). Only reject a true duplicate of the exact same pair.
        boolean duplicatePair = routeLocationRepository.findAllByName(name.trim()).stream()
                .anyMatch(rl -> fromLocation.equalsIgnoreCase((rl.getFromLocation() == null ? "" : rl.getFromLocation()).trim()));
        if (duplicatePair) {
            throw new RuntimeException("'" + name.trim() + "' already has a fixed fare from '" + fromLocation + "'");
        }

        RouteLocation loc = RouteLocation.builder()
                .name(name.trim())
                .fromLocation(fromLocation)
                .fare(fare)
                .build();
        RouteLocation saved = routeLocationRepository.save(loc);

        // A brand-new place name should be selectable in pickup/destination
        // dropdowns immediately, not just usable as a fare rule.
        locationService.ensureKnown(name.trim());
        locationService.ensureKnown(fromLocation);

        return saved;
    }

    public RouteLocation updateDestination(Long id, Map<String, Object> body) {
        RouteLocation loc = routeLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Destination not found"));

        String newName = body.containsKey("name") && body.get("name") != null
                ? body.get("name").toString().trim()
                : loc.getName();
        String newFromLocation = body.containsKey("fromLocation") && body.get("fromLocation") != null
                && !body.get("fromLocation").toString().isBlank()
                ? body.get("fromLocation").toString().trim()
                : loc.getFromLocation();

        // Same (name, fromLocation)-pair guard as addDestination — editing
        // this row into a pair another row already owns would silently
        // create two identical fixed-fare rules for the same route.
        boolean duplicatePair = routeLocationRepository.findAllByName(newName).stream()
                .anyMatch(rl -> !rl.getId().equals(id)
                        && newFromLocation != null
                        && newFromLocation.equalsIgnoreCase((rl.getFromLocation() == null ? "" : rl.getFromLocation()).trim()));
        if (duplicatePair) {
            throw new RuntimeException("'" + newName + "' already has a fixed fare from '" + newFromLocation + "'");
        }

        loc.setName(newName);
        loc.setFromLocation(newFromLocation);
        if (body.containsKey("fare") && body.get("fare") != null) {
            loc.setFare(Double.valueOf(body.get("fare").toString()));
        }

        RouteLocation saved = routeLocationRepository.save(loc);
        locationService.ensureKnown(saved.getName());
        if (saved.getFromLocation() != null) {
            locationService.ensureKnown(saved.getFromLocation());
        }
        return saved;
    }

    public String deleteDestination(Long id) {
        RouteLocation loc = routeLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Destination not found"));
        routeLocationRepository.delete(loc);
        return "Destination " + loc.getName() + " deleted.";
    }

    // KNOWN LOCATIONS MANAGEMENT — the master list of selectable place
    // names shown in every pickup/destination dropdown, separate from the
    // fare-rule table above. Lets an admin fix a typo or remove a junk
    // entry (a test name that should never have been added) and have it
    // actually disappear everywhere, not just from the fare-rule table.
    public List<KnownLocation> getKnownLocations() {
        return locationService.getAllKnown();
    }

    public KnownLocation renameKnownLocation(Long id, String newName) {
        return locationService.renameKnown(id, newName);
    }

    public void deleteKnownLocation(Long id) {
        locationService.deleteKnown(id);
    }

    // platform analytics
    public Map<String, Object> getAnalytics() {
        List<User> allUsers = userRepository.findAll();
        List<Ride> allRides = rideRepository.findAll();

        long totalUsers = allUsers.size();
        long totalRiders = allUsers.stream()
                .filter(u -> u.getRole() == Role.RIDER).count();
        long totalDrivers = allUsers.stream()
                .filter(u -> u.getRole() == Role.DRIVER).count();

        long totalRides = allRides.size();
        long activeRides = allRides.stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.REQUESTED
                        || r.getStatus() == Ride.RideStatus.ACCEPTED
                        || r.getStatus() == Ride.RideStatus.STARTED).count();
        long completedRides = allRides.stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.COMPLETED).count();
        long cancelledRides = allRides.stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.CANCELLED || r.getStatus() == Ride.RideStatus.EXPIRED).count();
        long instantRides = allRides.stream()
                .filter(r -> r.getRideType() == Ride.RideType.INSTANT).count();
        long plannedRides = allRides.stream()
                .filter(r -> r.getRideType() == Ride.RideType.PLANNED).count();

        double totalFare = allRides.stream()
                .filter(r -> r.getFare() != null)
                .mapToDouble(Ride::getFare).sum();

        double totalDistanceTraveled = allRides.stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.COMPLETED)
                .mapToDouble(r -> getRideDistance(r.getPickupLocation(), r.getDestination()))
                .sum();

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalUsers", totalUsers);
        analytics.put("totalRiders", totalRiders);
        analytics.put("totalDrivers", totalDrivers);
        analytics.put("totalRides", totalRides);
        analytics.put("activeRides", activeRides);
        analytics.put("completedRides", completedRides);
        analytics.put("cancelledRides", cancelledRides);
        analytics.put("totalInstantRides", instantRides);
        analytics.put("totalPlannedRides", plannedRides);
        analytics.put("totalFareVolume", totalFare);
        analytics.put("totalDistanceTraveled", Math.round(totalDistanceTraveled * 10.0) / 10.0);
        return analytics;
    }

    // Per-driver breakdown of the same earnings totalFareVolume already
    // rolls up platform-wide — lets the admin see who's actually driving.
    public List<Map<String, Object>> getDriverEarnings() {
        return driverProfileRepository.findAll().stream()
                .map(driver -> {
                    List<Ride> driverRides = rideRepository.findByDriver(driver);
                    long completedCount = driverRides.stream()
                            .filter(r -> r.getStatus() == Ride.RideStatus.COMPLETED)
                            .count();

                    Map<String, Object> row = new HashMap<>();
                    row.put("driverId", driver.getId());
                    row.put("driverName", driver.getUser() != null ? driver.getUser().getFullName() : null);
                    row.put("email", driver.getUser() != null ? driver.getUser().getEmail() : null);
                    row.put("phone", driver.getUser() != null ? driver.getUser().getPhone() : null);
                    row.put("vehicleType", driver.getVehicleType());
                    row.put("vehicleModel", driver.getVehicleModel());
                    row.put("available", driver.isAvailable());
                    row.put("totalEarnings", driver.getTotalEarnings() != null ? driver.getTotalEarnings() : 0.0);
                    row.put("completedRides", completedCount);
                    row.put("totalRides", driverRides.size());
                    return row;
                })
                .sorted((a, b) -> Double.compare(
                        (Double) b.get("totalEarnings"), (Double) a.get("totalEarnings")))
                .toList();
    }

    // SUPPORT INBOX — thin delegation, matches the LocationService pattern
    // already used above for destinations.
    public List<Map<String, Object>> getAllSupportMessages() {
        return supportService.getAllMessages();
    }

    public Map<String, Object> replyToSupportMessage(Long id, String reply) {
        return supportService.replyToMessage(id, reply);
    }

    public String deleteSupportMessage(Long id) {
        return supportService.deleteMessage(id);
    }

    // LOCATION REQUESTS — thin delegation
    public List<Map<String, Object>> getLocationRequests() {
        return locationRequestService.getAllRequests();
    }

    public Map<String, Object> approveLocationRequest(Long id, Map<String, Object> body) {
        return locationRequestService.approveRequest(id, body);
    }

    public Map<String, Object> rejectLocationRequest(Long id, String reason) {
        return locationRequestService.rejectRequest(id, reason);
    }

    // SOS ALERTS — thin delegation, same pattern as support/location
    // requests above.
    public List<Map<String, Object>> getSosAlerts() {
        return sosService.getAllAlerts();
    }

    public Map<String, Object> resolveSosAlert(Long id) {
        return sosService.resolveAlert(id);
    }

    // HARD DELETE a user account and everything tied to it — support
    // messages, location requests, vehicles, driver profile, and every ride
    // they appear on (as rider or as driver). This is deliberately a full
    // purge rather than a soft-delete/anonymize: the point is to actually
    // reclaim space for test/junk accounts, not just hide them. Admin
    // accounts are refused here as a safety rail — suspend/activate still
    // covers moderating another admin if that's ever needed.
    @Transactional
    public String deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            throw new RuntimeException("Admin accounts can't be deleted from here.");
        }

        supportMessageRepository.deleteAll(supportMessageRepository.findAllByUserOrderByCreatedAtDesc(user));
        locationRequestRepository.deleteAll(locationRequestRepository.findAllByUserOrderByCreatedAtDesc(user));

        // Rides where this user is the RIDER.
        rideRepository.deleteAll(rideRepository.findByRider(user));

        // If they're a driver: their offered/accepted rides, their
        // vehicles, then the driver profile itself.
        DriverProfile driverProfile = driverProfileRepository.findByUser(user).orElse(null);
        if (driverProfile != null) {
            rideRepository.deleteAll(rideRepository.findByDriver(driverProfile));
            vehicleRepository.deleteAll(vehicleRepository.findByDriver(driverProfile));
            driverProfileRepository.delete(driverProfile);
        }

        String name = user.getFullName();
        userRepository.delete(user);
        log.warn("Admin deleted user #{} ({}) and all associated data", id, name);
        return "User " + name + " and all their data have been permanently deleted.";
    }
}