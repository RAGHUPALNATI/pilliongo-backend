package com.raghu.pilliongo.service;

import com.raghu.pilliongo.dto.RideResponse;
import com.raghu.pilliongo.model.*;
import com.raghu.pilliongo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Unit tests for the booking/cancel logic in RideService — the most
// complex part of the backend and the part multi-seat carpooling changed.
// Pure Mockito, no Spring context or database needed.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RideServiceTest {

    @Mock RideRepository rideRepository;
    @Mock UserRepository userRepository;
    @Mock DriverProfileRepository driverProfileRepository;
    @Mock RouteLocationRepository routeLocationRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock NotificationService notificationService;
    @Mock DistanceService distanceService;

    @InjectMocks RideService rideService;

    private User driverUser;
    private DriverProfile driver;
    private User riderA;
    private User riderB;
    private final List<Ride> db = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        driverUser = user(1L, "driver@lpu.in", Role.DRIVER);
        driver = DriverProfile.builder().id(1L).user(driverUser).build();
        riderA = user(2L, "a@lpu.in", Role.RIDER);
        riderB = user(3L, "b@lpu.in", Role.RIDER);

        for (User u : List.of(driverUser, riderA, riderB)) {
            when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        }
        when(driverProfileRepository.findByUser(driverUser)).thenReturn(Optional.of(driver));

        // tiny in-memory "database" behind the mocked repository
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> {
            Ride r = inv.getArgument(0);
            if (r.getId() == null) r.setId(ids.incrementAndGet());
            if (!db.contains(r)) db.add(r);
            return r;
        });
        when(rideRepository.findById(any())).thenAnswer(inv ->
                db.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst());
        when(rideRepository.findByParentOfferId(any())).thenAnswer(inv ->
                db.stream().filter(r -> inv.getArgument(0).equals(r.getParentOfferId())).toList());
        when(rideRepository.findByRider(any())).thenAnswer(inv ->
                db.stream().filter(r -> inv.getArgument(0).equals(r.getRider())).toList());
    }

    private static User user(Long id, String email, Role role) {
        return User.builder().id(id).email(email).fullName(email).phone("9999999999")
                .password("x").role(role).build();
    }

    private Ride offer(int seats, double farePerSeat) {
        Ride r = Ride.builder()
                .driver(driver)
                .pickupLocation("LPU University Main Gate")
                .destination("Jalandhar City")
                .rideType(Ride.RideType.PLANNED)
                .status(Ride.RideStatus.REQUESTED)
                .scheduledDate(LocalDate.now().plusDays(2))
                .scheduledTime(LocalTime.of(9, 30))
                .seatsTotal(seats)
                .seatsBooked(0)
                .fare(farePerSeat)
                .build();
        return rideRepository.save(r);
    }

    // ---- seat rules ----

    @Test
    void bikeOffersAreAlwaysOneSeat() {
        Vehicle bike = Vehicle.builder().vehicleType("Bike").build();
        Vehicle car = Vehicle.builder().vehicleType("Car").build();
        assertEquals(1, RideService.resolveOfferSeats(4, bike));
        assertEquals(4, RideService.resolveOfferSeats(4, car));
        assertEquals(1, RideService.resolveOfferSeats(null, car));
        assertEquals(6, RideService.resolveOfferSeats(10, car));
    }

    // ---- booking ----

    @Test
    void bookingMultiSeatOfferCreatesSeparateBookingAndKeepsOfferOpen() {
        Ride offer = offer(3, 100.0);

        RideResponse res = rideService.bookDriverOffer(offer.getId(), 2, riderA.getEmail());

        assertEquals("ACCEPTED", res.getStatus());
        assertEquals(2, res.getSeatCount());
        assertEquals(200.0, res.getFare());
        assertEquals(offer.getId(), res.getParentOfferId());
        assertEquals(Ride.RideStatus.REQUESTED, offer.getStatus());
        assertNull(offer.getRider());
        assertEquals(2, offer.getSeatsBooked());
        assertEquals(1, offer.seatsRemaining());
    }

    @Test
    void cannotBookMoreSeatsThanRemain() {
        Ride offer = offer(3, 100.0);
        rideService.bookDriverOffer(offer.getId(), 2, riderA.getEmail());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> rideService.bookDriverOffer(offer.getId(), 2, riderB.getEmail()));
        assertTrue(ex.getMessage().contains("Only 1 seat left"));
    }

    @Test
    void sameRiderCannotBookSameOfferTwice() {
        Ride offer = offer(3, 100.0);
        rideService.bookDriverOffer(offer.getId(), 1, riderA.getEmail());

        assertThrows(RuntimeException.class,
                () -> rideService.bookDriverOffer(offer.getId(), 1, riderA.getEmail()));
    }

    @Test
    void singleSeatOfferBecomesTheBookingItself() {
        Ride offer = offer(1, 80.0);

        RideResponse res = rideService.bookDriverOffer(offer.getId(), 1, riderA.getEmail());

        assertEquals(offer.getId(), res.getId());
        assertEquals(Ride.RideStatus.ACCEPTED, offer.getStatus());
        assertSame(riderA, offer.getRider());
        assertEquals(0, offer.seatsRemaining());
    }

    @Test
    void driversCannotBookOffers() {
        Ride offer = offer(3, 100.0);
        assertThrows(RuntimeException.class,
                () -> rideService.bookDriverOffer(offer.getId(), 1, driverUser.getEmail()));
    }

    @Test
    void fullOfferRejectsFurtherBookings() {
        Ride offer = offer(2, 100.0);
        rideService.bookDriverOffer(offer.getId(), 2, riderA.getEmail());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> rideService.bookDriverOffer(offer.getId(), 1, riderB.getEmail()));
        assertTrue(ex.getMessage().contains("full"));
    }

    // ---- cancelling ----

    @Test
    void riderCancellingBookingGivesSeatsBack() {
        Ride offer = offer(3, 100.0);
        RideResponse booking = rideService.bookDriverOffer(offer.getId(), 2, riderA.getEmail());

        rideService.cancelRide(booking.getId(), riderA.getEmail());

        assertEquals(0, offer.getSeatsBooked());
        assertEquals(3, offer.seatsRemaining());
    }

    @Test
    void driverCancellingOfferCancelsEveryBookingAndNotifiesRiders() {
        Ride offer = offer(3, 100.0);
        RideResponse a = rideService.bookDriverOffer(offer.getId(), 1, riderA.getEmail());
        RideResponse b = rideService.bookDriverOffer(offer.getId(), 1, riderB.getEmail());

        rideService.cancelRide(offer.getId(), driverUser.getEmail());

        assertEquals(Ride.RideStatus.CANCELLED, offer.getStatus());
        assertEquals(Ride.RideStatus.CANCELLED, rideRepository.findById(a.getId()).orElseThrow().getStatus());
        assertEquals(Ride.RideStatus.CANCELLED, rideRepository.findById(b.getId()).orElseThrow().getStatus());

        ArgumentCaptor<User> notified = ArgumentCaptor.forClass(User.class);
        verify(notificationService, atLeast(2)).notify(notified.capture(), eq("RIDE_CANCELLED"), any(), any());
        assertTrue(notified.getAllValues().contains(riderA));
        assertTrue(notified.getAllValues().contains(riderB));
    }

    @Test
    void strangersCannotCancelSomeoneElsesRide() {
        Ride offer = offer(3, 100.0);
        RideResponse booking = rideService.bookDriverOffer(offer.getId(), 1, riderA.getEmail());

        assertThrows(RuntimeException.class,
                () -> rideService.cancelRide(booking.getId(), riderB.getEmail()));
    }

    // ---- start / complete ----

    @Test
    void completingABookingCreditsDriverWithThatBookingsFare() {
        Ride offer = offer(3, 100.0);
        RideResponse booking = rideService.bookDriverOffer(offer.getId(), 2, riderA.getEmail());

        rideService.startRide(booking.getId(), driverUser.getEmail());
        rideService.completeRide(booking.getId(), driverUser.getEmail());

        assertEquals(200.0, driver.getTotalEarnings());
    }

    @Test
    void cannotCompleteARideThatHasNotStarted() {
        Ride offer = offer(3, 100.0);
        RideResponse booking = rideService.bookDriverOffer(offer.getId(), 1, riderA.getEmail());

        assertThrows(RuntimeException.class,
                () -> rideService.completeRide(booking.getId(), driverUser.getEmail()));
    }
}
