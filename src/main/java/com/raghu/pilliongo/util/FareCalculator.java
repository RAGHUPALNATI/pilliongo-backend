package com.raghu.pilliongo.util;

import java.util.Map;

// Single source of truth for route distances and the base fare formula.
// Previously DISTANCE_MAP was copy-pasted in both RideService (for fare
// calculation) and AdminService (for the analytics distance totals) — a
// route added to one silently never showed up in the other. Both now call
// getDistance()/calculateFare() here instead.
public class FareCalculator {

    private FareCalculator() {
        // static utility class — never instantiated
    }

    private static final double BASE_FARE = 20.0;
    private static final double RATE_PER_KM = 12.5;
    private static final double DEFAULT_DISTANCE_KM = 5.0;
    private static final String HUB = "LPU University Main Gate";

    // Real roads wind; ~1.3x the straight-line distance is the usual
    // rule of thumb for city/regional trips.
    private static final double ROAD_FACTOR = 1.3;
    private static final double MIN_MAP_DISTANCE_KM = 1.0;

    // A guessed distance between two spokes off the hub is never trusted
    // completely — floored so two different places never price as if
    // they were next door, capped so a bad guess can't run away high.
    private static final double ESTIMATE_FLOOR_KM = 1.5;
    private static final double ESTIMATE_CAP_KM = 15.0;

    // Distances (km) between LOCATIONS pairs (see frontend lib/api.js for
    // the canonical 24-location list). Keyed one direction only —
    // getDistance() checks both "A-B" and "B-A" so entries aren't
    // duplicated. Distances radiate from LPU University Main Gate (the
    // campus every ride is overwhelmingly likely to start or end at),
    // plus the handful of cross-routes below between the other hub
    // points (Phagwara/Jalandhar/Kapurthala). These are estimated road
    // distances for that area, not GPS-measured — replace with real
    // measured distances when you have them.
    private static final Map<String, Double> DISTANCE_MAP = Map.ofEntries(
            // LPU University Main Gate <-> every other location
            Map.entry("LPU University Main Gate-LPU Gate 2 (Back Gate)", 1.0),
            Map.entry("LPU University Main Gate-Green Valley Main Gate", 1.0),
            // These three are also flat "fixed fare zone" rows in the DB
            // (see FareZoneSeeder) — a real distance entry for them here
            // is what lets the hub-difference estimate below work for any
            // OTHER pair involving them (e.g. "Meheru to Butani Colony"),
            // not just trips from the hub itself.
            Map.entry("LPU University Main Gate-Law Gate", 1.0),
            Map.entry("LPU University Main Gate-Butani Colony", 1.0),
            Map.entry("LPU University Main Gate-Jazzy Properties", 1.0),
            Map.entry("LPU University Main Gate-Hardaspur", 4.0),
            Map.entry("LPU University Main Gate-Meheru", 5.0),
            Map.entry("LPU University Main Gate-Cheharu", 6.0),
            Map.entry("LPU University Main Gate-Rama Mandi", 8.0),
            Map.entry("LPU University Main Gate-Deep Nagar", 2.0),
            Map.entry("LPU University Main Gate-Model Town, Phagwara", 4.0),
            Map.entry("LPU University Main Gate-Phagwara Bus Stand", 3.0),
            Map.entry("LPU University Main Gate-Jalandhar City", 18.0),
            Map.entry("LPU University Main Gate-Jalandhar Bus Stand", 19.0),
            Map.entry("LPU University Main Gate-Jalandhar Cantt Railway Station", 20.0),
            Map.entry("LPU University Main Gate-PAU Chowk, Jalandhar", 16.0),
            Map.entry("LPU University Main Gate-Nakodar Chowk", 12.0),
            Map.entry("LPU University Main Gate-Kapurthala City", 20.0),
            Map.entry("LPU University Main Gate-Adampur", 25.0),
            Map.entry("LPU University Main Gate-Goraya", 10.0),
            Map.entry("LPU University Main Gate-Begowal", 15.0),
            Map.entry("LPU University Main Gate-Mehatpur", 8.0),
            Map.entry("LPU University Main Gate-Bhogpur", 22.0),
            Map.entry("LPU University Main Gate-Banga", 15.0),
            Map.entry("LPU University Main Gate-Nawanshahr (SBS Nagar)", 30.0),
            Map.entry("LPU University Main Gate-Ladowal Toll Plaza", 35.0),

            // Other well-travelled cross-routes that don't touch LPU directly
            Map.entry("Phagwara Bus Stand-Jalandhar City", 15.0),
            Map.entry("Phagwara Bus Stand-Nakodar Chowk", 9.0),
            Map.entry("Phagwara Bus Stand-Model Town, Phagwara", 2.0),
            Map.entry("Jalandhar City-Nakodar Chowk", 10.0),
            Map.entry("Jalandhar City-Jalandhar Bus Stand", 2.0),
            Map.entry("Jalandhar City-Jalandhar Cantt Railway Station", 3.0),
            Map.entry("Jalandhar City-PAU Chowk, Jalandhar", 3.0),
            Map.entry("Jalandhar City-Adampur", 12.0),
            Map.entry("Nakodar Chowk-Mehatpur", 6.0),
            Map.entry("Kapurthala City-Begowal", 8.0),
            Map.entry("Kapurthala City-Bhogpur", 18.0)
    );

    // Three tiers, in order:
    //   1. A DIRECT entry above — an admin/dev has personally verified
    //      this exact pair's real distance. Most accurate when present.
    //   2. A HUB-DIFFERENCE ESTIMATE — almost every entry above is
    //      "hub to somewhere", so if both ends of this trip each have a
    //      known hub distance, estimate this leg as the difference
    //      between them. Two spokes that are each ~5km from the hub are
    //      probably fairly close to EACH OTHER too, not 5km apart in an
    //      arbitrary direction — which is what the old flat-default
    //      fallback effectively assumed for every unlisted pair. This is
    //      still a guess (there's no real map/GPS data behind it), just a
    //      much better-informed one, and it's floored/capped so it can't
    //      go absurd in either direction.
    //   3. The flat DEFAULT_DISTANCE_KM — only when neither end has any
    //      known hub distance either, i.e. genuinely no data to go on.
    // Whatever this returns, an admin can always override a SPECIFIC pair
    // exactly by adding it as a fixed-fare route in the admin panel —
    // that takes priority over all of this (see RideService.calculateFare
    // / lookupFixedFare), so a case you know this estimate gets wrong is
    // one admin action away from being exactly right, permanently.
    public static double getDistance(String pickup, String destination) {
        return getDistance(pickup, destination, null, null);
    }

    // Same, but with optional map positions {lat, lng} for each end. Order:
    //   1. a hand-verified pair in DISTANCE_MAP (most trusted)
    //   2. MAP DISTANCE: straight-line distance between the two positions
    //      x ROAD_FACTOR (roads are never straight). This is what makes
    //      any city in the world work: give its places a map position and
    //      fares just work, no distance table needed.
    //   3. the LPU hub-difference estimate, 4. the flat default (below).
    public static double getDistance(String pickup, String destination, double[] from, double[] to) {
        if (pickup == null || destination == null) return DEFAULT_DISTANCE_KM;
        String key1 = pickup + "-" + destination;
        String key2 = destination + "-" + pickup;
        if (DISTANCE_MAP.containsKey(key1)) return DISTANCE_MAP.get(key1);
        if (DISTANCE_MAP.containsKey(key2)) return DISTANCE_MAP.get(key2);

        if (from != null && to != null) {
            double km = haversineKm(from[0], from[1], to[0], to[1]) * ROAD_FACTOR;
            return Math.round(Math.max(MIN_MAP_DISTANCE_KM, km) * 10.0) / 10.0;
        }

        Double hubToPickup = distanceFromHub(pickup);
        Double hubToDestination = distanceFromHub(destination);
        if (hubToPickup != null && hubToDestination != null) {
            double estimate = Math.abs(hubToPickup - hubToDestination);
            return Math.max(ESTIMATE_FLOOR_KM, Math.min(estimate, ESTIMATE_CAP_KM));
        }

        return DEFAULT_DISTANCE_KM;
    }

    private static Double distanceFromHub(String place) {
        if (HUB.equals(place)) return 0.0;
        return DISTANCE_MAP.get(HUB + "-" + place);
    }

    public static double calculateFare(String pickup, String destination) {
        return fareForDistance(getDistance(pickup, destination));
    }

    public static double fareForDistance(double km) {
        return BASE_FARE + (km * RATE_PER_KM);
    }

    // Great-circle distance between two lat/lng points, in km.
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
