package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.KnownLocation;
import com.raghu.pilliongo.repository.KnownLocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistanceServiceTest {

    @Mock KnownLocationRepository knownLocationRepository;
    @InjectMocks DistanceService distanceService;

    private void place(String name, Double lat, Double lng) {
        when(knownLocationRepository.findFirstByNameIgnoreCase(name)).thenReturn(Optional.of(
                KnownLocation.builder().name(name).latitude(lat).longitude(lng).build()));
    }

    @Test
    void usesMapPositionsWhenBothPlacesHaveThem() {
        place("Koramangala", 12.9352, 77.6245);
        place("MG Road, Bengaluru", 12.9756, 77.6050);
        double km = distanceService.distanceKm("Koramangala", "MG Road, Bengaluru");
        assertTrue(km > 5 && km < 8, "expected ~6.5 km, got " + km);
    }

    @Test
    void missingPositionFallsBackToDefaultEstimate() {
        place("Koramangala", 12.9352, 77.6245);
        when(knownLocationRepository.findFirstByNameIgnoreCase("Nowhere")).thenReturn(Optional.empty());
        assertEquals(5.0, distanceService.distanceKm("Koramangala", "Nowhere"));
    }

    @Test
    void placeWithOnlyHalfAPositionIsIgnored() {
        place("Half", 12.9, null);
        place("Koramangala", 12.9352, 77.6245);
        assertEquals(5.0, distanceService.distanceKm("Half", "Koramangala"));
    }

    @Test
    void fareFollowsDistance() {
        when(knownLocationRepository.findFirstByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        assertEquals(20.0 + 5.0 * 12.5, distanceService.fare("A", "B"));
    }
}
