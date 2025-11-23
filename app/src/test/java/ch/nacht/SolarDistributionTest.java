package ch.nacht;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

public class SolarDistributionTest {

    private static final double DELTA = 0.001;

    @Test
    public void testFallB_PartialCoverage() {
        // Fall B: Produktion < Gesamtbedarf
        double solarProduction = 10.0;
        List<Double> consumption = Arrays.asList(2.0, 8.0, 5.0);

        List<Double> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        // Verify allocations
        assertEquals(3, allocation.size());
        assertEquals(2.0, allocation.get(0), DELTA); // Verbraucher 1
        assertEquals(4.0, allocation.get(1), DELTA); // Verbraucher 2
        assertEquals(4.0, allocation.get(2), DELTA); // Verbraucher 3

        // Verify total allocation equals production
        double totalAllocated = allocation.stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(solarProduction, totalAllocated, DELTA);
    }

    @Test
    public void testFallA_FullCoverage() {
        // Fall A: Produktion >= Gesamtbedarf
        double solarProduction = 20.0;
        List<Double> consumption = Arrays.asList(5.0, 3.0, 4.0);

        List<Double> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        // Each consumer gets their full demand
        assertEquals(3, allocation.size());
        assertEquals(5.0, allocation.get(0), DELTA);
        assertEquals(3.0, allocation.get(1), DELTA);
        assertEquals(4.0, allocation.get(2), DELTA);
    }

    @Test
    public void testNoProduction() {
        double solarProduction = 0.0;
        List<Double> consumption = Arrays.asList(2.0, 3.0);

        List<Double> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        assertEquals(2, allocation.size());
    }

    @Test
    public void testEmptyConsumers() {
        double solarProduction = 10.0;
        List<Double> consumption = Arrays.asList();

        List<Double> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        assertTrue(allocation.isEmpty());
    }
}
