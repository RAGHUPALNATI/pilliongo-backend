package com.raghu.pilliongo.config;

import com.raghu.pilliongo.model.RouteLocation;
import com.raghu.pilliongo.repository.RouteLocationRepository;
import com.raghu.pilliongo.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

// Seeds fixed-fare "zones" near campus that should always cost the same
// flat amount instead of the per-km distance formula. Idempotent — only
// inserts a RouteLocation row for a name that doesn't already have one, so
// this never overwrites a fare an admin later edits by hand via the admin
// panel, and re-running it on every boot is a no-op once these exist.
// "Green Valley Main Gate" is deliberately NOT listed here — it already
// exists as an admin-entered RouteLocation row (fare 30), and
// SchemaPatchRunner backfills its fromLocation instead of this seeder
// touching it.
@Component
@RequiredArgsConstructor
@Order(11)
public class FareZoneSeeder implements ApplicationRunner {

    private final RouteLocationRepository routeLocationRepository;
    private final LocationService locationService;

    private static final String HUB = "LPU University Main Gate";

    private static final Map<String, Double> FIXED_FARE_ZONES = new LinkedHashMap<>();
    static {
        FIXED_FARE_ZONES.put("Law Gate", 30.0);
        FIXED_FARE_ZONES.put("Butani Colony", 30.0);
        FIXED_FARE_ZONES.put("Jazzy Properties", 30.0);
        FIXED_FARE_ZONES.put("Model Town, Phagwara", 50.0);
        FIXED_FARE_ZONES.put("Phagwara Bus Stand", 50.0);
    }

    @Override
    public void run(ApplicationArguments args) {
        // Only seed a genuinely fresh database — same reasoning as
        // LocationSeeder. Once any fare rule exists, admins own this table
        // from the admin panel; re-running this on every boot would
        // silently resurrect a fixed-fare zone (and its known-location
        // entry) an admin deliberately deleted.
        if (!routeLocationRepository.findAll().isEmpty()) {
            return;
        }
        locationService.ensureKnown(HUB);
        FIXED_FARE_ZONES.forEach((name, fare) -> {
            routeLocationRepository.save(RouteLocation.builder()
                    .name(name)
                    .fromLocation(HUB)
                    .fare(fare)
                    .build());
            locationService.ensureKnown(name);
        });
    }
}
