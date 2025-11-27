package ch.nacht;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class SolarDistributionTest {

    @Test
    public void testFallB_PartialCoverage() {
        // Fall B: Produktion < Gesamtbedarf
        BigDecimal solarProduction = BigDecimal.valueOf(10.0);
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.valueOf(2.0),
                BigDecimal.valueOf(8.0),
                BigDecimal.valueOf(5.0));

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        // Verify allocations
        assertEquals(3, allocation.size());
        assertEquals(0, BigDecimal.valueOf(2.0).compareTo(allocation.get(0))); // Verbraucher 1
        assertEquals(0, BigDecimal.valueOf(4.0).compareTo(allocation.get(1))); // Verbraucher 2
        assertEquals(0, BigDecimal.valueOf(4.0).compareTo(allocation.get(2))); // Verbraucher 3

        // Verify total allocation equals production
        BigDecimal totalAllocated = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, solarProduction.compareTo(totalAllocated));
    }

    @Test
    public void testFallA_FullCoverage() {
        // Fall A: Produktion >= Gesamtbedarf
        BigDecimal solarProduction = BigDecimal.valueOf(20.0);
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.valueOf(5.0),
                BigDecimal.valueOf(3.0),
                BigDecimal.valueOf(4.0));

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        // Each consumer gets their full demand
        assertEquals(3, allocation.size());
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(allocation.get(0)));
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(allocation.get(1)));
        assertEquals(0, BigDecimal.valueOf(4.0).compareTo(allocation.get(2)));
    }

    @Test
    public void test0Adjustments() {
        BigDecimal solarProduction = new BigDecimal("0.036");
        List<BigDecimal> consumption = Arrays.asList(
                new BigDecimal("0.019"),  // 0
                new BigDecimal("0.008"),  // 1
                new BigDecimal("0.005"),  // 2
                new BigDecimal("0.009"),  // 3
                new BigDecimal("0.038"),  // 4
                new BigDecimal("0.011"),  // 5
                new BigDecimal("0.008"),  // 6
                new BigDecimal("0.001"),  // 7
                new BigDecimal("0.003"),  // 8
                new BigDecimal("0.024")); // 9

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        // Each consumer gets their share, with rounding adjustments starting from lowest consumers
        assertEquals(10, allocation.size());
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(0)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(1)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(2)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(3)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(4)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(5)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(6)));
        assertEquals(0, new BigDecimal("0.001").compareTo(allocation.get(7)));
        assertEquals(0, new BigDecimal("0.003").compareTo(allocation.get(8)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(9)));
    }

    @Test
    public void test3Adjustments() {
        BigDecimal solarProduction = new BigDecimal("0.033");
        List<BigDecimal> consumption = Arrays.asList(
                new BigDecimal("0.019"),  // 0
                new BigDecimal("0.008"),  // 1
                new BigDecimal("0.005"),  // 2
                new BigDecimal("0.009"),  // 3
                new BigDecimal("0.038"),  // 4
                new BigDecimal("0.011"),  // 5
                new BigDecimal("0.008"),  // 6
                new BigDecimal("0.001"),  // 7
                new BigDecimal("0.003"),  // 8
                new BigDecimal("0.024")); // 9

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        // Each consumer gets their share, with rounding adjustments starting from lowest consumers
        assertEquals(10, allocation.size());
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(0)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(1)));
        assertEquals(0, new BigDecimal("0.003").compareTo(allocation.get(2))); // adjusted
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(3)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(4)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(5)));
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(6)));
        assertEquals(0, new BigDecimal("0.000").compareTo(allocation.get(7))); // adjusted
        assertEquals(0, new BigDecimal("0.002").compareTo(allocation.get(8))); // adjusted
        assertEquals(0, new BigDecimal("0.004").compareTo(allocation.get(9)));
    }

    @Test
    public void testNoProduction() {
        BigDecimal solarProduction = BigDecimal.ZERO;
        List<BigDecimal> consumption = Arrays.asList(BigDecimal.valueOf(2.0), BigDecimal.valueOf(3.0));

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        assertEquals(2, allocation.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(0)));
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(1)));
    }

    @Test
    public void testEmptyConsumers() {
        BigDecimal solarProduction = BigDecimal.valueOf(10.0);
        List<BigDecimal> consumption = Arrays.asList();

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        assertTrue(allocation.isEmpty());
    }

    @Test
    public void testZeroConsumption() {
        BigDecimal solarProduction = BigDecimal.valueOf(10.0);
        List<BigDecimal> consumption = Arrays.asList(BigDecimal.ZERO, BigDecimal.ZERO);

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        assertEquals(2, allocation.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(0)));
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(1)));
    }

    @Test
    public void testMixedConsumption() {
        BigDecimal solarProduction = BigDecimal.valueOf(5.0);
        List<BigDecimal> consumption = Arrays.asList(BigDecimal.ZERO, BigDecimal.valueOf(10.0));

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        assertEquals(2, allocation.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(0)));
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(allocation.get(1)));
    }

    @Test
    public void testRoundingSplit() {
        // 10 units produced, 3 consumers with equal demand of 10.
        // Should be split 3.333, 3.333, 3.334 (to sum to 10)
        BigDecimal solarProduction = BigDecimal.valueOf(10.0);
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(10.0));

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        assertEquals(3, allocation.size());
        BigDecimal total = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, solarProduction.compareTo(total));

        // Check that values are close to 3.333
        assertTrue(allocation.stream().allMatch(v -> v.compareTo(new BigDecimal("3.333")) >= 0));
        assertTrue(allocation.stream().allMatch(v -> v.compareTo(new BigDecimal("3.334")) <= 0));
    }

    @Test
    public void testLargeNumbers() {
        BigDecimal solarProduction = new BigDecimal("1000000.000");
        List<BigDecimal> consumption = Arrays.asList(
                new BigDecimal("2000000.000"),
                new BigDecimal("3000000.000"));

        List<BigDecimal> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

        assertEquals(2, allocation.size());
        BigDecimal total = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, solarProduction.compareTo(total));

        // Equal distribution: 1M / 2 = 500k each (since both need more than 500k)
        assertEquals(0, new BigDecimal("500000.000").compareTo(allocation.get(0)));
        assertEquals(0, new BigDecimal("500000.000").compareTo(allocation.get(1)));
    }
}
