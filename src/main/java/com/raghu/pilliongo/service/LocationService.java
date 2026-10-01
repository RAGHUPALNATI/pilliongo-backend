package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.KnownLocation;
import com.raghu.pilliongo.repository.KnownLocationRepository;
import com.raghu.pilliongo.repository.RouteLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final KnownLocationRepository knownLocationRepository;
    private final RouteLocationRepository routeLocationRepository;
    private final GeocodingService geocodingService;

    public List<String> getAllLocationNames() {
        return knownLocationRepository.findAllByOrderByNameAsc()
                .stream()
                .map(KnownLocation::getName)
                .toList();
    }

    // Admin — full rows (with id), so the admin panel can rename/delete a
    // specific place instead of only matching by its display name.
    public List<KnownLocation> getAllKnown() {
        return knownLocationRepository.findAllByOrderByNameAsc();
    }

    // Admin — fix a typo or a junk name (e.g. a test entry someone added by
    // mistake) without losing whatever fare rule already points at it.
    // Cascades to every RouteLocation fare rule that references the old
    // name, as either endpoint, so a rename never leaves a rule silently
    // pointing at a name nobody can select anymore. Ride records are left
    // untouched — pickup/destination on a Ride is a point-in-time text
    // snapshot taken at request time, not a reference to this table.
    public KnownLocation renameKnown(Long id, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new RuntimeException("Location name is required");
        }
        String trimmed = newName.trim();
        KnownLocation loc = knownLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found"));
        String oldName = loc.getName();
        if (oldName.equalsIgnoreCase(trimmed)) {
            return loc;
        }
        if (knownLocationRepository.existsByName(trimmed)) {
            throw new RuntimeException("A location named '" + trimmed + "' already exists");
        }

        loc.setName(trimmed);
        KnownLocation saved = knownLocationRepository.save(loc);

        // A place can now have several fixed-fare rows (one per distinct
        // origin), so every row named after it gets relabeled, not just one.
        routeLocationRepository.findAllByName(oldName).forEach(rl -> {
            rl.setName(trimmed);
            routeLocationRepository.save(rl);
        });
        routeLocationRepository.findAll().stream()
                .filter(rl -> oldName.equalsIgnoreCase(rl.getFromLocation()))
                .forEach(rl -> {
                    rl.setFromLocation(trimmed);
                    routeLocationRepository.save(rl);
                });

        return saved;
    }

    // Admin — remove a place from every pickup/destination dropdown in the
    // app (a spelling mistake, a joke entry, somewhere that should never
    // have been added). Any fare rule that used this name as either
    // endpoint is removed with it — a fixed fare for a place that no
    // longer exists is meaningless. Existing rides keep their original
    // pickup/destination text untouched, same reasoning as renameKnown.
    public void deleteKnown(Long id) {
        KnownLocation loc = knownLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found"));
        String name = loc.getName();

        // Delete every fixed-fare row named after this place, not just one —
        // it may have several (one per distinct origin it's priced from).
        routeLocationRepository.findAllByName(name).forEach(routeLocationRepository::delete);
        routeLocationRepository.findAll().stream()
                .filter(rl -> name.equalsIgnoreCase(rl.getFromLocation()))
                .forEach(routeLocationRepository::delete);

        knownLocationRepository.delete(loc);
    }

    // ---- Map positions (what makes fares work in any city) ----

    // Admin adds a brand-new place. If no position is given, try to find it
    // on OpenStreetMap; the place is still added if that fails.
    public KnownLocation addKnown(String name, Double latitude, Double longitude) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Location name is required");
        }
        String trimmed = name.trim();
        if (knownLocationRepository.existsByName(trimmed)) {
            throw new RuntimeException("A location named '" + trimmed + "' already exists");
        }
        KnownLocation loc = KnownLocation.builder().name(trimmed).build();
        if (latitude != null || longitude != null) {
            validateCoordinates(latitude, longitude);
            loc.setLatitude(latitude);
            loc.setLongitude(longitude);
        } else {
            geocodingService.lookup(trimmed).ifPresent(p -> {
                loc.setLatitude(p[0]);
                loc.setLongitude(p[1]);
            });
        }
        return knownLocationRepository.save(loc);
    }

    // Admin types a position in by hand (e.g. copied from Google Maps).
    // Both null = clear it.
    public KnownLocation setCoordinates(Long id, Double latitude, Double longitude) {
        KnownLocation loc = knownLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found"));
        if (latitude != null || longitude != null) {
            validateCoordinates(latitude, longitude);
        }
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);
        return knownLocationRepository.save(loc);
    }

    // Admin clicks "Auto-locate" on one place.
    public KnownLocation geocode(Long id) {
        KnownLocation loc = knownLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found"));
        double[] p = geocodingService.lookup(loc.getName())
                .orElseThrow(() -> new RuntimeException(
                        "Couldn't find '" + loc.getName() + "' on the map. Try a more specific name "
                                + "(e.g. add the city) or enter the coordinates manually."));
        loc.setLatitude(p[0]);
        loc.setLongitude(p[1]);
        return knownLocationRepository.save(loc);
    }

    // Admin clicks "Locate all missing". Waits ~1s between lookups to stay
    // within OpenStreetMap's free-use limit, so a long list takes a while.
    public Map<String, Object> geocodeMissing() {
        List<String> located = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (KnownLocation loc : knownLocationRepository.findAllByOrderByNameAsc()) {
            if (loc.getLatitude() != null && loc.getLongitude() != null) continue;
            Optional<double[]> p = geocodingService.lookup(loc.getName());
            if (p.isPresent()) {
                loc.setLatitude(p.get()[0]);
                loc.setLongitude(p.get()[1]);
                knownLocationRepository.save(loc);
                located.add(loc.getName());
            } else {
                notFound.add(loc.getName());
            }
            try {
                Thread.sleep(1100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Map.of("located", located, "notFound", notFound);
    }

    private static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new RuntimeException("Enter both latitude and longitude");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new RuntimeException("Latitude must be -90..90 and longitude -180..180");
        }
    }

    // Idempotent — safe to call every time a place name shows up anywhere
    // (admin adds/edits a destination fare rule, a location request gets
    // approved, a seeder runs). Only inserts if it isn't already known, so
    // this is what makes a newly approved location actually appear as a
    // selectable pickup/destination across the app without another
    // deploy.
    public void ensureKnown(String name) {
        if (name == null || name.isBlank()) return;
        String trimmed = name.trim();
        if (!knownLocationRepository.existsByName(trimmed)) {
            knownLocationRepository.save(KnownLocation.builder().name(trimmed).build());
        }
    }
}
