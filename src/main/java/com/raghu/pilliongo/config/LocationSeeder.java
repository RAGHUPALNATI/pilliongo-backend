package com.raghu.pilliongo.config;

import com.raghu.pilliongo.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// Seeds the starting set of selectable pickup/destination locations.
// Idempotent (LocationService.ensureKnown only inserts a name that isn't
// already there), so this safely runs on every boot instead of needing a
// one-off manual insert — a fresh database ends up with a working location
// list without anyone typing all of these into the admin panel by hand.
@Component
@RequiredArgsConstructor
@Order(10)
public class LocationSeeder implements ApplicationRunner {

    private final LocationService locationService;

    private static final List<String> STARTER_LOCATIONS = List.of(
            "LPU University Main Gate",
            "LPU Gate 2 (Back Gate)",
            "Green Valley Main Gate",
            "Law Gate",
            "Butani Colony",
            "Jazzy Properties",
            "Hardaspur",
            "Meheru",
            "Cheharu",
            "Rama Mandi",
            "Deep Nagar",
            "Model Town, Phagwara",
            "Phagwara Bus Stand",
            "Jalandhar City",
            "Jalandhar Bus Stand",
            "Jalandhar Cantt Railway Station",
            "PAU Chowk, Jalandhar",
            "Nakodar Chowk",
            "Kapurthala City",
            "Adampur",
            "Goraya",
            "Begowal",
            "Mehatpur",
            "Bhogpur",
            "Banga",
            "Nawanshahr (SBS Nagar)",
            "Ladowal Toll Plaza"
    );

    @Override
    public void run(ApplicationArguments args) {
        // Only seed a genuinely fresh database. Once any locations exist,
        // admins own this list from the admin panel (add/rename/delete
        // under "All Locations") — re-running this on every boot would
        // silently resurrect anything they deliberately deleted.
        if (!locationService.getAllLocationNames().isEmpty()) {
            return;
        }
        STARTER_LOCATIONS.forEach(locationService::ensureKnown);
    }
}
