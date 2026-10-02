package com.raghu.pilliongo.service;

import com.raghu.pilliongo.dto.RideRequest;
import com.raghu.pilliongo.dto.RideResponse;
import com.raghu.pilliongo.model.*;
import com.raghu.pilliongo.repository.*;
import com.raghu.pilliongo.util.FareCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideService {

    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final RouteLocationRepository routeLocationRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;
    private final DistanceService distanceService;

    // helper — get current user from email
    private User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // helper — get driver profile from email
    private DriverProfile getCurrentDriver(String email) {
        User user = getCurrentUser(email);
        return driverProfileRepository.findByUser(user)
                .orElseGet(() -> {
                    if (user.getRole() == Role.DRIVER) {
                        DriverProfile profile = DriverProfile.builder()
                                .user(user)
                                .vehicleType("Bike")
                                .vehicleModel("Standard")
                                .vehiclePlate("PB-09-NEW")
                                .available(true)
                                .totalEarnings(0.0)
                                .build();
                        return driverProfileRepository.save(profile);
                    }
                    throw new RuntimeException("Driver profile not found for user: " + email);
                });
    }

    // helper — auto expire instant rides older than 15 minutes
    // Asks the database for ONLY the instant rides that are already past
    // their 15 minutes (normally none), instead of loading every open ride
    // and checking each one in Java. This runs on a timer and at the start
    // of most ride requests, so it has to be cheap.
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 30000)
    public void checkAndExpireInstantRides() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        List<Ride> expired = rideRepository.findByStatusAndRideTypeAndCreatedAtBefore(
                Ride.RideStatus.REQUESTED, Ride.RideType.INSTANT, cutoff);
        for (Ride r : expired) {
            r.setStatus(Ride.RideStatus.EXPIRED);
            rideRepository.save(r);
        }
    }

    // helper — convert Ride to RideResponse
    private RideResponse toResponse(Ride ride) {
        LocalDateTime expiresAt = null;
        if (ride.getRideType() == Ride.RideType.INSTANT && ride.getCreatedAt() != null) {
            expiresAt = ride.getCreatedAt().plusMinutes(15);
        }

        boolean showContacts = ride.getStatus() == Ride.RideStatus.ACCEPTED
                || ride.getStatus() == Ride.RideStatus.STARTED
                || ride.getStatus() == Ride.RideStatus.COMPLETED;

        String riderPhone = (showContacts && ride.getRider() != null) ? ride.getRider().getPhone() : null;
        String driverPhone = (showContacts && ride.getDriver() != null && ride.getDriver().getUser() != null)
                ? ride.getDriver().getUser().getPhone() : null;

        // Prefer the vehicle actually selected for THIS ride (snapshotted on
        // the Ride itself at offer time). Falls back to the driver's
        // profile-level vehicle fields only for rides saved before this
        // feature existed, which never got a vehicleType/Model/Plate of
        // their own.
        String vehicleType = ride.getVehicleType() != null ? ride.getVehicleType()
                : (ride.getDriver() != null ? ride.getDriver().getVehicleType() : null);
        String vehicleModel = ride.getVehicleModel() != null ? ride.getVehicleModel()
                : (ride.getDriver() != null ? ride.getDriver().getVehicleModel() : null);
        String vehiclePlate = ride.getVehiclePlate() != null ? ride.getVehiclePlate()
                : (ride.getDriver() != null ? ride.getDriver().getVehiclePlate() : null);

        // Same visibility gate as phone numbers — a driver's live position
        // is only meaningful (and only shared) once the ride is actually
        // underway, not while it's still an open request.
        Double driverLat = null;
        Double driverLng = null;
        LocalDateTime driverLocationUpdatedAt = null;
        if (showContacts && ride.getDriver() != null) {
            driverLat = ride.getDriver().getCurrentLat();
            driverLng = ride.getDriver().getCurrentLng();
            driverLocationUpdatedAt = ride.getDriver().getLocationUpdatedAt();
        }

        // Same idea in reverse — the rider's live position, so a driver
        // hunting for a pickup on a busy street can see roughly where to go.
        Double riderLat = null;
        Double riderLng = null;
        LocalDateTime riderLocationUpdatedAt = null;
        if (showContacts && ride.getRider() != null) {
            riderLat = ride.getRider().getCurrentLat();
            riderLng = ride.getRider().getCurrentLng();
            riderLocationUpdatedAt = ride.getRider().getLocationUpdatedAt();
        }

        return RideResponse.builder()
                .id(ride.getId())
                .riderName(ride.getRider() != null ? ride.getRider().getFullName() : null)
                .riderPhone(riderPhone)
                .driverName(ride.getDriver() != null && ride.getDriver().getUser() != null ? ride.getDriver().getUser().getFullName() : null)
                .driverPhone(driverPhone)
                .vehicleType(vehicleType)
                .vehicleModel(vehicleModel)
                .vehiclePlate(vehiclePlate)
                .pickupLocation(ride.getPickupLocation())
                .destination(ride.getDestination())
                .rideType(ride.getRideType().name())
                .status(ride.getStatus().name())
                .scheduledDate(ride.getScheduledDate())
                .scheduledTime(ride.getScheduledTime())
                .description(ride.getDescription())
                .fare(ride.getFare() != null ? ride.getFare() : calculateFare(ride.getPickupLocation(), ride.getDestination()))
                .seatsTotal(ride.getRider() == null ? ride.effectiveSeatsTotal() : null)
                .seatsBooked(ride.getRider() == null ? ride.effectiveSeatsBooked() : null)
                .seatsAvailable(ride.getRider() == null ? ride.seatsRemaining() : null)
                .seatCount(ride.getRider() != null ? ride.effectiveSeatCount() : null)
                .parentOfferId(ride.getParentOfferId())
                .paid(ride.isPaid())
                .paidAt(ride.getPaidAt())
                .driverLat(driverLat)
                .driverLng(driverLng)
                .driverLocationUpdatedAt(driverLocationUpdatedAt)
                .riderLat(riderLat)
                .riderLng(riderLng)
                .riderLocationUpdatedAt(riderLocationUpdatedAt)
                .createdAt(ride.getCreatedAt())
                .expiresAt(expiresAt)
                .build();
    }

    // Calculate fare. A fixed admin-set fare (RouteLocation) wins if this
    // exact pickup/destination pair matches one, checked in BOTH directions
    // (e.g. an admin rule "LPU -> Green Valley = ₹30" also covers a ride
    // going Green Valley -> LPU); otherwise falls back to FareCalculator's
    // shared distance-based formula. Public (not private) so RideController
    // can reuse it for a live fare-estimate endpoint — same numbers a real
    // ride would get, just without persisting anything.
    public double calculateFare(String pickup, String destination) {
        Double fixedFare = lookupFixedFare(pickup, destination);
        if (fixedFare != null) {
            return fixedFare;
        }
        return distanceService.fare(pickup, destination);
    }

    private Double lookupFixedFare(String pickup, String destination) {
        if (routeLocationRepository == null || destination == null) {
            return null;
        }

        // Forward: any rule named after the destination whose fromLocation
        // either isn't set (legacy/wildcard row) or matches this exact
        // pickup. A place can now have several fixed-fare rows — one per
        // distinct "from" (e.g. "Butani Colony" priced separately from LPU
        // Main Gate and from Meheru) — so every row sharing that name is
        // checked, not just a single assumed one.
        for (RouteLocation loc : routeLocationRepository.findAllByName(destination)) {
            if (loc.getFare() == null) continue;
            if (loc.getFromLocation() == null || loc.getFromLocation().equalsIgnoreCase(pickup)) {
                return loc.getFare();
            }
        }

        // Reverse: a rule named after the PICKUP, whose fromLocation
        // matches this destination (covers the return trip of the same
        // fixed-fare route).
        if (pickup != null) {
            for (RouteLocation loc : routeLocationRepository.findAllByName(pickup)) {
                if (loc.getFare() == null) continue;
                if (loc.getFromLocation() != null && loc.getFromLocation().equalsIgnoreCase(destination)) {
                    return loc.getFare();
                }
            }
        }

        return null;
    }

    // helper — a driver posting an offer (instant or planned) must pick one
    // of their OWN vehicles for that specific ride. Throws the same style
    // of plain RuntimeException (-> 400, "{message: ...}") the rest of this
    // service already uses, so the frontend error toast just works.
    private Vehicle resolveOfferedVehicle(Long vehicleId, DriverProfile driver) {
        if (vehicleId == null) {
            throw new RuntimeException("Please select which vehicle you're using for this ride");
        }
        return vehicleRepository.findByIdAndDriver(vehicleId, driver)
                .orElseThrow(() -> new RuntimeException("Selected vehicle not found on your account"));
    }

    // helper — how many seats a planned offer may advertise. A bike only ever
    // carries one pillion passenger, whatever the form sent; a car can take
    // up to 6 (also enforced by @Max on RideRequest.seats).
    static int resolveOfferSeats(Integer requested, Vehicle vehicle) {
        int seats = requested == null ? 1 : requested;
        if (seats < 1) seats = 1;
        if (seats > 6) seats = 6;
        if (vehicle != null && vehicle.getVehicleType() != null
                && vehicle.getVehicleType().trim().equalsIgnoreCase("Bike")) {
            seats = 1;
        }
        return seats;
    }

    // RIDER — create ride request (INSTANT or PLANNED)
    public RideResponse createRide(RideRequest request, String email) {
        checkAndExpireInstantRides();
        User rider = getCurrentUser(email);

        if (rider.getRole() == Role.DRIVER) {
            throw new RuntimeException("Drivers cannot request rider rides. Drivers can only offer planned route trips.");
        }

        Ride.RideType requestedType = Ride.RideType.valueOf(request.getRideType().toUpperCase());

        if (requestedType == Ride.RideType.INSTANT) {
            // Check rider has no active INSTANT ride
            boolean hasActiveInstantRide = rideRepository.findByRider(rider).stream()
                    .filter(r -> r.getRideType() == Ride.RideType.INSTANT)
                    .anyMatch(r -> r.getStatus() == Ride.RideStatus.REQUESTED
                            || r.getStatus() == Ride.RideStatus.ACCEPTED
                            || r.getStatus() == Ride.RideStatus.STARTED);

            if (hasActiveInstantRide) {
                throw new RuntimeException("You already have an active instant ride in progress. Complete or cancel it first.");
            }
        } else if (requestedType == Ride.RideType.PLANNED) {
            // Check rider has no conflicting PLANNED ride at exact same date & time
            if (request.getScheduledDate() != null && request.getScheduledTime() != null) {
                boolean hasConflictingPlanned = rideRepository.findByRider(rider).stream()
                        .filter(r -> r.getRideType() == Ride.RideType.PLANNED)
                        .filter(r -> r.getStatus() == Ride.RideStatus.REQUESTED
                                || r.getStatus() == Ride.RideStatus.ACCEPTED
                                || r.getStatus() == Ride.RideStatus.STARTED)
                        .anyMatch(r -> request.getScheduledDate().equals(r.getScheduledDate())
                                && request.getScheduledTime().equals(r.getScheduledTime()));

                if (hasConflictingPlanned) {
                    throw new RuntimeException("You already have a pre-planned ride request scheduled at this exact date and time.");
                }
            }
        }

        double calculatedFare = calculateFare(request.getPickupLocation(), request.getDestination());

        Ride ride = Ride.builder()
                .rider(rider)
                .pickupLocation(request.getPickupLocation())
                .destination(request.getDestination())
                .rideType(requestedType)
                .status(Ride.RideStatus.REQUESTED)
                .scheduledDate(request.getScheduledDate())
                .scheduledTime(request.getScheduledTime())
                .description(request.getDescription())
                .fare(calculatedFare)
                .build();

        return toResponse(rideRepository.save(ride));
    }

    // DRIVER — publish a pre-planned route offer
    // Second layer of protection: /api/rides/** at the SecurityConfig level
    // is only "authenticated" (any role, since riders also hit that path for
    // create/book/etc.), so this @PreAuthorize is what actually stops a
    // RIDER from calling this driver-only endpoint.
    @PreAuthorize("hasRole('DRIVER')")
    public RideResponse offerPlannedRide(RideRequest request, String email) {
        DriverProfile driver = getCurrentDriver(email);
        Vehicle vehicle = resolveOfferedVehicle(request.getVehicleId(), driver);

        double calculatedFare = calculateFare(request.getPickupLocation(), request.getDestination());
        int seats = resolveOfferSeats(request.getSeats(), vehicle);

        Ride ride = Ride.builder()
                .driver(driver)
                .rider(null) // rider will book later
                .seatsTotal(seats)
                .seatsBooked(0)
                .pickupLocation(request.getPickupLocation())
                .destination(request.getDestination())
                .rideType(Ride.RideType.PLANNED)
                .status(Ride.RideStatus.REQUESTED)
                .scheduledDate(request.getScheduledDate())
                .scheduledTime(request.getScheduledTime())
                .description(request.getDescription())
                .fare(calculatedFare)
                .vehicleType(vehicle.getVehicleType())
                .vehicleModel(vehicle.getVehicleModel())
                .vehiclePlate(vehicle.getVehiclePlate())
                .build();

        return toResponse(rideRepository.save(ride));
    }

    // RIDER — book a seat on a driver-posted offer (planned route OR instant
    // "driving right now" offer)
    // Double-booking protection comes from Ride's @Version column: every
    // booking updates the offer row, and if two riders do that at the same
    // instant the database accepts only the first; the second fails with
    // an optimistic-lock error (shown to the user as "someone else just
    // updated this ride, try again") and the whole transaction rolls back.
    // This works the same on MySQL and on TiDB, which does not support the
    // SERIALIZABLE isolation level this used to ask for.
    @Transactional
    public RideResponse bookDriverOffer(Long rideId, String email) {
        return bookDriverOffer(rideId, 1, email);
    }

    @Transactional
    public RideResponse bookDriverOffer(Long rideId, Integer requestedSeats, String email) {
        User rider = getCurrentUser(email);

        if (rider.getRole() == Role.DRIVER) {
            throw new RuntimeException("Drivers cannot book seats on other driver offers.");
        }

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Route offer not found"));

        if (ride.getDriver() == null) {
            throw new RuntimeException("Invalid route offer.");
        }

        if (ride.getStatus() != Ride.RideStatus.REQUESTED) {
            throw new RuntimeException("This route offer is no longer available.");
        }

        if (ride.getDriver().getUser().equals(rider)) {
            throw new RuntimeException("You cannot book your own route offer.");
        }

        if (ride.getRideType() == Ride.RideType.INSTANT) {
            boolean hasActiveInstantRide = rideRepository.findByRider(rider).stream()
                    .filter(r -> r.getRideType() == Ride.RideType.INSTANT)
                    .anyMatch(r -> r.getStatus() == Ride.RideStatus.REQUESTED
                            || r.getStatus() == Ride.RideStatus.ACCEPTED
                            || r.getStatus() == Ride.RideStatus.STARTED);

            if (hasActiveInstantRide) {
                throw new RuntimeException("You already have an active instant ride in progress. Complete or cancel it first.");
            }
        }

        if (ride.getRider() != null) {
            throw new RuntimeException("This route offer is no longer available.");
        }

        int seats = requestedSeats == null ? 1 : requestedSeats;
        if (seats < 1) {
            throw new RuntimeException("Please book at least 1 seat.");
        }
        int remaining = ride.seatsRemaining();
        if (remaining <= 0) {
            throw new RuntimeException("This ride is already full.");
        }
        if (seats > remaining) {
            throw new RuntimeException("Only " + remaining + " seat" + (remaining == 1 ? "" : "s") + " left on this ride.");
        }

        double farePerSeat = ride.getFare() != null ? ride.getFare() : calculateFare(ride.getPickupLocation(), ride.getDestination());
        Ride booking;

        if (ride.effectiveSeatsTotal() <= 1) {
            // Single-seat offer (every bike ride, every instant offer, and
            // any offer saved before multi-seat existed): the offer row
            // itself becomes the booking, exactly as before.
            ride.setRider(rider);
            ride.setStatus(Ride.RideStatus.ACCEPTED);
            ride.setSeatCount(1);
            ride.setSeatsBooked(1);
            booking = rideRepository.save(ride);
        } else {
            // Multi-seat offer: the offer stays open on the board and each
            // rider gets their OWN booking row, so start/complete/cancel/
            // paid/history all keep working per passenger.
            boolean alreadyBooked = rideRepository.findByParentOfferId(ride.getId()).stream()
                    .anyMatch(b -> rider.equals(b.getRider())
                            && (b.getStatus() == Ride.RideStatus.ACCEPTED || b.getStatus() == Ride.RideStatus.STARTED));
            if (alreadyBooked) {
                throw new RuntimeException("You've already booked a seat on this ride.");
            }

            // Saving the offer first bumps its @Version, so two riders racing
            // for the last seat can't both win.
            ride.setSeatsBooked(ride.effectiveSeatsBooked() + seats);
            rideRepository.save(ride);

            booking = rideRepository.save(Ride.builder()
                    .rider(rider)
                    .driver(ride.getDriver())
                    .parentOfferId(ride.getId())
                    .seatCount(seats)
                    .pickupLocation(ride.getPickupLocation())
                    .destination(ride.getDestination())
                    .rideType(ride.getRideType())
                    .status(Ride.RideStatus.ACCEPTED)
                    .scheduledDate(ride.getScheduledDate())
                    .scheduledTime(ride.getScheduledTime())
                    .description(ride.getDescription())
                    .fare(farePerSeat * seats)
                    .vehicleType(ride.getVehicleType())
                    .vehicleModel(ride.getVehicleModel())
                    .vehiclePlate(ride.getVehiclePlate())
                    .build());
        }

        RideResponse response = toResponse(booking);

        if (ride.getDriver() != null && ride.getDriver().getUser() != null) {
            String seatText = seats == 1 ? "a seat" : seats + " seats";
            notificationService.notify(
                    ride.getDriver().getUser(),
                    "RIDE_ACCEPTED",
                    rider.getFullName() + " booked " + seatText + " on your " + (ride.getRideType() == Ride.RideType.INSTANT ? "instant" : "pre-planned") + " ride offer.",
                    booking.getId()
            );
        }

        return response;
    }

    // DRIVER — see available rides (rider-posted requests only; driver-posted
    // offers are booked by riders via bookDriverOffer, never "accepted" by
    // another driver)
    @PreAuthorize("hasRole('DRIVER')")
    public List<RideResponse> getAvailableRides(String email) {
        checkAndExpireInstantRides();
        DriverProfile driver = getCurrentDriver(email);

        if (!driver.isAvailable()) {
            driver.setAvailable(true);
            driverProfileRepository.save(driver);
        }

        return rideRepository
                .findByStatusAndDriverIsNull(Ride.RideStatus.REQUESTED)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // helper — is the driver busy RIGHT NOW in a way that should block any new
    // instant action (posting or accepting an instant ride)? Two cases:
    //   - STARTED, any ride type: physically driving someone right now.
    //   - ACCEPTED + INSTANT: accepted and expected to start any moment.
    // Deliberately does NOT include ACCEPTED + PLANNED here — an accepted
    // planned ride scheduled for later today (or any other far-off time) does
    // not make the driver busy right now. That case is handled separately by
    // findUpcomingPlannedConflict(), which only blocks when the scheduled
    // time is actually close.
    private boolean isDriverBusyRightNow(DriverProfile driver) {
        return rideRepository.findByDriver(driver).stream()
                .anyMatch(r -> r.getStatus() == Ride.RideStatus.STARTED
                        || (r.getStatus() == Ride.RideStatus.ACCEPTED && r.getRideType() == Ride.RideType.INSTANT));
    }

    // helper — driver already committed (ACCEPTED) to a planned ride whose
    // scheduled time falls within the next 2 hours. Returns that ride, or
    // null if there's no such near-term conflict. A planned ride already
    // STARTED is covered by isDriverBusyRightNow() instead, so it's
    // deliberately excluded here.
    private Ride findUpcomingPlannedConflict(DriverProfile driver) {
        LocalDateTime now = LocalDateTime.now();
        return rideRepository.findByDriver(driver).stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.ACCEPTED)
                .filter(r -> r.getRideType() == Ride.RideType.PLANNED && r.getScheduledDate() != null)
                .filter(r -> {
                    LocalDateTime scheduled = LocalDateTime.of(
                            r.getScheduledDate(),
                            r.getScheduledTime() != null ? r.getScheduledTime() : LocalTime.MIDNIGHT
                    );
                    long minutesDiff = java.time.Duration.between(now, scheduled).toMinutes();
                    return minutesDiff >= 0 && minutesDiff <= 120;
                })
                .findFirst()
                .orElse(null);
    }

    // DRIVER — post an instant "I'm driving right now" ride offer, the
    // instant-ride counterpart to offerPlannedRide(). Riders browse these via
    // getAvailableInstantOffers() and book with bookDriverOffer().
    @PreAuthorize("hasRole('DRIVER')")
    public RideResponse offerInstantRide(RideRequest request, String email) {
        checkAndExpireInstantRides();
        DriverProfile driver = getCurrentDriver(email);
        Vehicle vehicle = resolveOfferedVehicle(request.getVehicleId(), driver);

        if (isDriverBusyRightNow(driver)) {
            throw new RuntimeException("You are currently on an active ride. Complete or cancel it before posting a new instant offer.");
        }

        boolean hasLiveInstantOffer = rideRepository.findByDriver(driver).stream()
                .anyMatch(r -> r.getRideType() == Ride.RideType.INSTANT && r.getStatus() == Ride.RideStatus.REQUESTED);
        if (hasLiveInstantOffer) {
            throw new RuntimeException("You already have an active instant ride offer posted. Cancel it before posting a new one.");
        }

        // Only block for a planned ride that's actually coming up soon — a
        // driver with a planned ride later today (or any other time outside
        // the 2-hour window) is still free to post/accept instant rides.
        Ride conflict = findUpcomingPlannedConflict(driver);
        if (conflict != null) {
            throw new RuntimeException("You have an accepted planned ride scheduled within the next 2 hours (" + conflict.getScheduledTime() + "). Complete or cancel it before posting instant offers.");
        }

        double calculatedFare = calculateFare(request.getPickupLocation(), request.getDestination());

        Ride ride = Ride.builder()
                .driver(driver)
                .rider(null)
                .pickupLocation(request.getPickupLocation())
                .destination(request.getDestination())
                .rideType(Ride.RideType.INSTANT)
                .status(Ride.RideStatus.REQUESTED)
                .seatsTotal(1)
                .seatsBooked(0)
                .description(request.getDescription())
                .fare(calculatedFare)
                .vehicleType(vehicle.getVehicleType())
                .vehicleModel(vehicle.getVehicleModel())
                .vehiclePlate(vehicle.getVehiclePlate())
                .build();

        return toResponse(rideRepository.save(ride));
    }

    // RIDER — browse live instant offers posted by drivers (symmetric to
    // getPlannedRides() for the planned-route marketplace)
    public List<RideResponse> getAvailableInstantOffers() {
        checkAndExpireInstantRides();
        return rideRepository
                .findByRideTypeAndStatusAndDriverIsNotNullAndRiderIsNull(Ride.RideType.INSTANT, Ride.RideStatus.REQUESTED)
                .stream()
                .filter(r -> r.seatsRemaining() > 0)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // PUBLIC — see all planned rides (bulletin board)
    public List<RideResponse> getPlannedRides() {
        checkAndExpireInstantRides();
        return rideRepository
                .findByRideTypeAndStatusAndScheduledDateGreaterThanEqual(
                        Ride.RideType.PLANNED, Ride.RideStatus.REQUESTED, LocalDate.now())
                .stream()
                // a driver offer with every seat taken drops off the board
                .filter(r -> r.getRider() != null || r.seatsRemaining() > 0)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // DRIVER — accept a ride
    // Same reasoning as bookDriverOffer(): the @Version check is what
    // prevents two drivers from both accepting the same REQUESTED ride.
    @PreAuthorize("hasRole('DRIVER')")
    @Transactional
    public RideResponse acceptRide(Long rideId, String email) {
        DriverProfile driver = getCurrentDriver(email);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getStatus() != Ride.RideStatus.REQUESTED) {
            throw new RuntimeException("Ride is no longer available.");
        }

        List<Ride> driverAcceptedRides = rideRepository.findByDriver(driver).stream()
                .filter(r -> r.getStatus() == Ride.RideStatus.ACCEPTED || r.getStatus() == Ride.RideStatus.STARTED)
                .toList();

        boolean isCurrentlyDriving = driverAcceptedRides.stream()
                .anyMatch(r -> r.getStatus() == Ride.RideStatus.STARTED);

        if (ride.getRideType() == Ride.RideType.INSTANT) {
            if (isCurrentlyDriving) {
                throw new RuntimeException("You are currently driving another ride. Complete it first.");
            }

            boolean hasActiveAcceptedInstant = driverAcceptedRides.stream()
                    .anyMatch(r -> r.getStatus() == Ride.RideStatus.ACCEPTED && r.getRideType() == Ride.RideType.INSTANT);
            if (hasActiveAcceptedInstant) {
                throw new RuntimeException("You are currently servicing an instant ride. Complete it first.");
            }

            // Check if driver has an accepted planned ride scheduled within 2 hours of now
            Ride conflict = findUpcomingPlannedConflict(driver);
            if (conflict != null) {
                throw new RuntimeException("You have an accepted planned ride scheduled within the next 2 hours (" + conflict.getScheduledTime() + "). Complete or cancel it before accepting instant rides.");
            }
        } else if (ride.getRideType() == Ride.RideType.PLANNED) {
            if (isCurrentlyDriving) {
                throw new RuntimeException("You are currently driving another ride. Complete it first.");
            }

            boolean hasActiveAcceptedInstant = driverAcceptedRides.stream()
                    .anyMatch(r -> r.getStatus() == Ride.RideStatus.ACCEPTED && r.getRideType() == Ride.RideType.INSTANT);
            if (hasActiveAcceptedInstant) {
                throw new RuntimeException("You are currently servicing an instant ride. Complete it first before accepting a planned ride.");
            }

            if (ride.getScheduledDate() != null) {
                for (Ride existing : driverAcceptedRides) {
                    if (existing.getStatus() == Ride.RideStatus.ACCEPTED
                            && existing.getRideType() == Ride.RideType.PLANNED && existing.getScheduledDate() != null) {
                        LocalDateTime targetTime = LocalDateTime.of(
                                ride.getScheduledDate(),
                                ride.getScheduledTime() != null ? ride.getScheduledTime() : LocalTime.MIDNIGHT
                        );
                        LocalDateTime existingTime = LocalDateTime.of(
                                existing.getScheduledDate(),
                                existing.getScheduledTime() != null ? existing.getScheduledTime() : LocalTime.MIDNIGHT
                        );

                        long hoursDiff = Math.abs(java.time.Duration.between(targetTime, existingTime).toHours());
                        if (hoursDiff < 6) {
                            throw new RuntimeException("You already have an accepted planned ride scheduled within 6 hours of this time.");
                        }
                    }
                }
            }
        }

        ride.setDriver(driver);
        ride.setStatus(Ride.RideStatus.ACCEPTED);
        RideResponse response = toResponse(rideRepository.save(ride));
        log.info("Ride {} accepted by driver {}", rideId, email);

        if (ride.getRider() != null && driver.getUser() != null) {
            notificationService.notify(
                    ride.getRider(),
                    "RIDE_ACCEPTED",
                    driver.getUser().getFullName() + " accepted your ride request.",
                    ride.getId()
            );
        }

        return response;
    }

    // RIDER or DRIVER — start ride
    public RideResponse startRide(Long rideId, String email) {
        User currentUser = getCurrentUser(email);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        boolean isRider = ride.getRider() != null && ride.getRider().equals(currentUser);
        boolean isDriver = ride.getDriver() != null && ride.getDriver().getUser().equals(currentUser);

        if (!isRider && !isDriver) {
            throw new RuntimeException("Access denied. You are not part of this ride.");
        }

        if (ride.getStatus() != Ride.RideStatus.ACCEPTED) {
            throw new RuntimeException("Ride must be accepted before starting.");
        }

        ride.setStatus(Ride.RideStatus.STARTED);
        RideResponse response = toResponse(rideRepository.save(ride));

        // Notify whichever side didn't tap "Start" — RIDER or DRIVER,
        // whoever `isRider`/`isDriver` above says triggered this.
        User other = isRider
                ? (ride.getDriver() != null ? ride.getDriver().getUser() : null)
                : ride.getRider();
        notificationService.notify(other, "RIDE_STARTED", "Your ride has started.", ride.getId());

        return response;
    }

    // RIDER or DRIVER — complete ride
    // Two save() calls here (driver earnings, then the ride itself) — wrap
    // in @Transactional so a failure on either one rolls both back, instead
    // of ever crediting earnings for a ride that didn't actually get marked
    // COMPLETED (or vice versa).
    @Transactional
    public RideResponse completeRide(Long rideId, String email) {
        User currentUser = getCurrentUser(email);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        boolean isRider = ride.getRider() != null && ride.getRider().equals(currentUser);
        boolean isDriver = ride.getDriver() != null && ride.getDriver().getUser().equals(currentUser);

        if (!isRider && !isDriver) {
            throw new RuntimeException("Access denied. You are not part of this ride.");
        }

        if (ride.getStatus() != Ride.RideStatus.STARTED) {
            throw new RuntimeException("Ride must be STARTED before completing.");
        }

        double fare = ride.getFare() != null ? ride.getFare() : calculateFare(ride.getPickupLocation(), ride.getDestination());

        ride.setStatus(Ride.RideStatus.COMPLETED);
        ride.setFare(fare);

        if (ride.getDriver() != null) {
            DriverProfile driver = ride.getDriver();
            driver.setTotalEarnings((driver.getTotalEarnings() != null ? driver.getTotalEarnings() : 0.0) + fare);
            driverProfileRepository.save(driver);
        }

        RideResponse response = toResponse(rideRepository.save(ride));

        User other = isRider
                ? (ride.getDriver() != null ? ride.getDriver().getUser() : null)
                : ride.getRider();
        notificationService.notify(other, "RIDE_COMPLETED", "Ride completed! Fare: ₹" + fare, ride.getId());

        return response;
    }

    // RIDER — live location ping, the reverse direction of the driver's
    // existing one (DriverService.updateLocation). Called by the rider's
    // own browser every ~10-15s while on the ride status page, so a driver
    // hunting for a pickup on a busy street can see roughly where to go.
    // Stored on the User row itself (not the Ride) so this never touches
    // Ride.version — a rapid location ping racing an accept/start/complete
    // on the same ride would otherwise risk an optimistic-lock failure on
    // either one for no good reason.
    public java.util.Map<String, Object> updateRiderLocation(Long rideId, Double lat, Double lng, String email) {
        if (lat == null || lng == null) {
            throw new RuntimeException("lat and lng are required");
        }
        User rider = getCurrentUser(email);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));
        if (ride.getRider() == null || !ride.getRider().equals(rider)) {
            throw new RuntimeException("Access denied. You are not the rider on this ride.");
        }
        if (ride.getStatus() != Ride.RideStatus.ACCEPTED && ride.getStatus() != Ride.RideStatus.STARTED) {
            throw new RuntimeException("Location sharing is only available while the ride is active.");
        }

        rider.setCurrentLat(lat);
        rider.setCurrentLng(lng);
        rider.setLocationUpdatedAt(LocalDateTime.now());
        userRepository.save(rider);

        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("riderLat", rider.getCurrentLat());
        map.put("riderLng", rider.getCurrentLng());
        map.put("riderLocationUpdatedAt", rider.getLocationUpdatedAt());
        return map;
    }

    // RIDER or DRIVER — cancel ride
    @Transactional
    public String cancelRide(Long rideId, String email) {
        User user = getCurrentUser(email);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        boolean isRider = ride.getRider() != null && ride.getRider().equals(user);
        boolean isDriver = ride.getDriver() != null && ride.getDriver().getUser().equals(user);

        if (!isRider && !isDriver) {
            throw new RuntimeException("This is not your ride.");
        }

        if (ride.getStatus() != Ride.RideStatus.REQUESTED && ride.getStatus() != Ride.RideStatus.ACCEPTED) {
            throw new RuntimeException("Only REQUESTED or ACCEPTED rides can be cancelled.");
        }

        ride.setStatus(Ride.RideStatus.CANCELLED);
        rideRepository.save(ride);

        // A booking on a multi-seat offer gives its seats back, so the
        // offer re-appears on the board for someone else.
        if (ride.getParentOfferId() != null) {
            rideRepository.findById(ride.getParentOfferId()).ifPresent(offer -> {
                if (offer.getStatus() == Ride.RideStatus.REQUESTED) {
                    offer.setSeatsBooked(Math.max(0, offer.effectiveSeatsBooked() - ride.effectiveSeatCount()));
                    rideRepository.save(offer);
                }
            });
        }

        // The driver pulling a whole multi-seat offer cancels every
        // passenger who'd booked onto it, and tells each of them.
        if (ride.getRider() == null && isDriver) {
            for (Ride booking : rideRepository.findByParentOfferId(ride.getId())) {
                if (booking.getStatus() == Ride.RideStatus.ACCEPTED) {
                    booking.setStatus(Ride.RideStatus.CANCELLED);
                    rideRepository.save(booking);
                    notificationService.notify(booking.getRider(), "RIDE_CANCELLED",
                            "The driver cancelled the ride you booked (" + ride.getPickupLocation() + " → " + ride.getDestination() + ").",
                            booking.getId());
                }
            }
        }

        User other = isRider
                ? (ride.getDriver() != null ? ride.getDriver().getUser() : null)
                : ride.getRider();
        notificationService.notify(other, "RIDE_CANCELLED", "A ride was cancelled.", ride.getId());

        return "Ride cancelled successfully.";
    }

    // RIDER or DRIVER — mark a ride's cash fare as settled. Deliberately a
    // one-way flag (no "unmark") and allowed any time from STARTED onward —
    // covers paying up front once the ride begins as well as paying after
    // it completes. Either side on the ride can confirm it since it's cash
    // changing hands between them, not a payment this app processes.
    public RideResponse markAsPaid(Long rideId, String email) {
        User user = getCurrentUser(email);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        boolean isRider = ride.getRider() != null && ride.getRider().equals(user);
        boolean isDriver = ride.getDriver() != null && ride.getDriver().getUser().equals(user);

        if (!isRider && !isDriver) {
            throw new RuntimeException("Access denied. You are not part of this ride.");
        }

        if (ride.getStatus() != Ride.RideStatus.STARTED && ride.getStatus() != Ride.RideStatus.COMPLETED) {
            throw new RuntimeException("Ride must be started or completed before it can be marked as paid.");
        }

        if (ride.isPaid()) {
            return toResponse(ride);
        }

        ride.setPaid(true);
        ride.setPaidAt(LocalDateTime.now());
        RideResponse response = toResponse(rideRepository.save(ride));

        User other = isRider
                ? (ride.getDriver() != null ? ride.getDriver().getUser() : null)
                : ride.getRider();
        notificationService.notify(other, "RIDE_PAID", user.getFullName() + " marked ride #" + ride.getId() + " as paid.", ride.getId());

        return response;
    }

    // RIDER or DRIVER — ride history
    public List<RideResponse> getRideHistory(String email) {
        checkAndExpireInstantRides();
        User user = getCurrentUser(email);

        if (user.getRole() == Role.DRIVER) {
            DriverProfile driver = getCurrentDriver(email);
            return rideRepository.findByDriver(driver)
                    .stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }

        return rideRepository.findByRider(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // get single ride by id
    public RideResponse getRideById(Long rideId, String email) {
        checkAndExpireInstantRides();
        User user = getCurrentUser(email);
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        boolean isRider = ride.getRider() != null && ride.getRider().equals(user);
        boolean isDriver = ride.getDriver() != null && ride.getDriver().getUser().equals(user);

        if (!isRider && !isDriver) {
            throw new RuntimeException("Access denied.");
        }

        return toResponse(ride);
    }
}