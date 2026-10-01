package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.KnownLocation;
import com.raghu.pilliongo.repository.KnownLocationRepository;
import com.raghu.pilliongo.repository.RouteLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final KnownLocationRepository knownLocationRepository;
    private final RouteLocationRepository routeLocationRepository;

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
