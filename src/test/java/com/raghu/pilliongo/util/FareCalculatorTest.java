package com.raghu.pilliongo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FareCalculatorTest {

    // Connaught Place, Delhi -> India Gate, Delhi (~2.4 km straight line)
    private static final double[] CONNAUGHT_PLACE = {28.6315, 77.2167};
    private static final double[] INDIA_GATE = {28.6129, 77.2295};

    @Test
    void haversineMatchesAKnownDistance() {
        // Delhi -> Mumbai is ~1,150 km in a straight line
        double km = FareCalculator.haversineKm(28.6139, 77.2090, 19.0760, 72.8777);
        assertEquals(1150, km, 15);
    }

    @Test
    void placesInAnyCityUseTheirMapDistance() {
        double km = FareCalculator.getDistance("Connaught Place", "India Gate", CONNAUGHT_PLACE, INDIA_GATE);
        // ~2.4 km straight line x 1.3 road factor ~= 3.1 km
        assertEquals(3.1, km, 0.3);
    }

    @Test
    void handVerifiedPairBeatsMapDistance() {
        double km = FareCalculator.getDistance("LPU University Main Gate", "Jalandhar City",
                new double[]{0, 0}, new double[]{10, 10});
        assertEquals(18.0, km);
    }

    @Test
    void tinyTripsAreAtLeastOneKm() {
        double km = FareCalculator.getDistance("A", "B", new double[]{28.6315, 77.2167}, new double[]{28.6316, 77.2167});
        assertEquals(1.0, km);
    }

    @Test
    void withoutMapPositionsUnknownPlacesFallBackToDefault() {
        assertEquals(5.0, FareCalculator.getDistance("Somewhere", "Elsewhere", null, null));
    }

    @Test
    void fareIsBasePlusPerKm() {
        assertEquals(20.0 + 10 * 12.5, FareCalculator.fareForDistance(10));
    }
}
