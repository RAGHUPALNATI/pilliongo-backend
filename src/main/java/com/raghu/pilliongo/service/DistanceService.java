package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.KnownLocation;
import com.raghu.pilliongo.repository.KnownLocationRepository;
import com.raghu.pilliongo.util.FareCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Distance between two place names, using their map positions when both
// are known. Every fare and distance in the app goes through here, so
// adding a map position to a location instantly fixes prices for every
// route that touches it, in any city.
@Service
@RequiredArgsConstructor
public class DistanceService {

    private final KnownLocationRepository knownLocationRepository;

    public double distanceKm(String pickup, String destination) {
        return FareCalculator.getDistance(pickup, destination, coordinatesOf(pickup), coordinatesOf(destination));
    }

    public double fare(String pickup, String destination) {
        return FareCalculator.fareForDistance(distanceKm(pickup, destination));
    }

    private double[] coordinatesOf(String name) {
        if (name == null || name.isBlank()) return null;
        return knownLocationRepository.findFirstByNameIgnoreCase(name.trim())
                .filter(l -> l.getLatitude() != null && l.getLongitude() != null)
                .map(l -> new double[]{l.getLatitude(), l.getLongitude()})
                .orElse(null);
    }
}
