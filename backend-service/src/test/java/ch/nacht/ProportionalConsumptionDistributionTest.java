package ch.nacht;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class ProportionalConsumptionDistributionTest {

    @Test
    public void testProportionalDistribution_PartialCoverage() {
        // Production < total consumption: proportional distribution
        BigDecimal solarProduction = BigDecimal.valueOf(10.0);
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.valueOf(2.0), // 2/15 = 13.33% -> 1.333
                BigDecimal.valueOf(8.0), // 8/15 = 53.33% -> 5.333
                BigDecimal.valueOf(5.0)); // 5/15 = 33.33% -> 3.333

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        // Verify allocations are proportional
        assertEquals(3, allocation.size());
        assertEquals(0, new BigDecimal("1.333").compareTo(allocation.get(0)));
        assertEquals(0, new BigDecimal("5.334").compareTo(allocation.get(1))); // Gets remainder
        assertEquals(0, new BigDecimal("3.333").compareTo(allocation.get(2)));

        // Verify total allocation equals production
        BigDecimal totalAllocated = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, solarProduction.compareTo(totalAllocated));
    }

    @Test
    public void testProportionalDistribution_FullCoverage() {
        // Production >= total consumption: everyone gets full demand
        BigDecimal solarProduction = BigDecimal.valueOf(20.0);
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.valueOf(5.0),
                BigDecimal.valueOf(3.0),
                BigDecimal.valueOf(4.0));

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        // Each consumer gets their full demand
        assertEquals(3, allocation.size());
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(allocation.get(0)));
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(allocation.get(1)));
        assertEquals(0, BigDecimal.valueOf(4.0).compareTo(allocation.get(2)));
    }

    @Test
    public void testRemainderToHighestConsumer() {
        // Test that rounding remainder goes to highest consumer
        BigDecimal solarProduction = new BigDecimal("10.0");
        List<BigDecimal> consumption = Arrays.asList(
                new BigDecimal("3.0"), // Lowest
                new BigDecimal("12.0"), // Highest - should get remainder
                new BigDecimal("6.0")); // Middle

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        // Verify total equals production
        BigDecimal totalAllocated = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, solarProduction.compareTo(totalAllocated));

        // Highest consumer (index 1) should have received proportional share + any
        // remainder
        // 12/21 * 10 = 5.714... rounded to 5.714
        assertTrue(allocation.get(1).compareTo(new BigDecimal("5.7")) > 0);
    }

    @Test
    public void testNoProduction() {
        BigDecimal solarProduction = BigDecimal.ZERO;
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.valueOf(2.0),
                BigDecimal.valueOf(3.0));

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        assertEquals(2, allocation.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(0)));
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(1)));
    }

    @Test
    public void testEmptyConsumers() {
        BigDecimal solarProduction = BigDecimal.valueOf(10.0);
        List<BigDecimal> consumption = Arrays.asList();

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        assertTrue(allocation.isEmpty());
    }

    @Test
    public void testZeroConsumption() {
        BigDecimal solarProduction = BigDecimal.valueOf(10.0);
        List<BigDecimal> consumption = Arrays.asList(BigDecimal.ZERO, BigDecimal.ZERO);

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        assertEquals(2, allocation.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(0)));
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(1)));
    }

    @Test
    public void testMixedConsumption() {
        // One consumer has zero consumption, other has demand
        BigDecimal solarProduction = BigDecimal.valueOf(5.0);
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.ZERO,
                BigDecimal.valueOf(10.0));

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        assertEquals(2, allocation.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(allocation.get(0)));
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(allocation.get(1)));
    }

    @Test
    public void testSingleConsumer() {
        BigDecimal solarProduction = BigDecimal.valueOf(5.0);
        List<BigDecimal> consumption = Arrays.asList(BigDecimal.valueOf(10.0));

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        assertEquals(1, allocation.size());
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(allocation.get(0)));
    }

    @Test
    public void testRoundingAdjustments() {
        // Test with values that create rounding challenges
        BigDecimal solarProduction = new BigDecimal("7.0");
        List<BigDecimal> consumption = Arrays.asList(
                new BigDecimal("3.0"),
                new BigDecimal("3.0"),
                new BigDecimal("3.0"));

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        // Verify total equals production exactly
        BigDecimal totalAllocated = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, solarProduction.compareTo(totalAllocated));

        // Each should get approximately 7/3 = 2.333
        assertTrue(allocation.stream().allMatch(v -> v.compareTo(new BigDecimal("2.3")) > 0));
        assertTrue(allocation.stream().allMatch(v -> v.compareTo(new BigDecimal("2.4")) < 0));
    }

    @Test
    public void testLargeNumbers() {
        BigDecimal solarProduction = new BigDecimal("1000000.000");
        List<BigDecimal> consumption = Arrays.asList(
                new BigDecimal("2000000.000"), // 2/5 = 40%
                new BigDecimal("3000000.000")); // 3/5 = 60%

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        assertEquals(2, allocation.size());

        // Verify total equals production
        BigDecimal totalAllocated = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, solarProduction.compareTo(totalAllocated));

        // Verify proportional distribution: 40% and 60%
        assertEquals(0, new BigDecimal("400000.000").compareTo(allocation.get(0)));
        assertEquals(0, new BigDecimal("600000.000").compareTo(allocation.get(1)));
    }

    @Test
    public void testSmallNumbers() {
        BigDecimal solarProduction = new BigDecimal("0.015");
        List<BigDecimal> consumption = Arrays.asList(
                new BigDecimal("0.010"), // 10/30 = 33.33%
                new BigDecimal("0.020")); // 20/30 = 66.67%

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        assertEquals(2, allocation.size());

        // Verify total equals production
        BigDecimal totalAllocated = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, solarProduction.compareTo(totalAllocated));

        // Verify proportional distribution
        assertEquals(0, new BigDecimal("0.005").compareTo(allocation.get(0)));
        assertEquals(0, new BigDecimal("0.010").compareTo(allocation.get(1)));
    }

    @Test
    public void testDifferenceFromEqualShare() {
        // Document the difference between proportional and equal-share algorithms
        BigDecimal solarProduction = BigDecimal.valueOf(10.0);
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.valueOf(2.0),
                BigDecimal.valueOf(8.0),
                BigDecimal.valueOf(5.0));

        // Proportional algorithm
        List<BigDecimal> proportionalAllocation = ProportionalConsumptionDistribution.distributeSolarPower(
                solarProduction, consumption);

        // Equal-share algorithm (for comparison)
        List<BigDecimal> equalShareAllocation = SolarDistribution.distributeSolarPower(
                solarProduction, consumption);

        // Proportional: [1.333, 5.333, 3.333]
        // Equal-share: [2.0, 4.0, 4.0]

        // Verify they are different
        assertNotEquals(proportionalAllocation.get(0), equalShareAllocation.get(0));
        assertNotEquals(proportionalAllocation.get(1), equalShareAllocation.get(1));
        assertNotEquals(proportionalAllocation.get(2), equalShareAllocation.get(2));

        // Proportional gives more to higher consumers
        assertTrue(proportionalAllocation.get(1).compareTo(equalShareAllocation.get(1)) > 0,
                "Highest consumer should get more with proportional algorithm");
        assertTrue(proportionalAllocation.get(0).compareTo(equalShareAllocation.get(0)) < 0,
                "Lowest consumer should get less with proportional algorithm");
    }

    @Test
    public void testNoAllocationExceedsConsumption() {
        // Verify that no allocation ever exceeds individual consumption
        BigDecimal solarProduction = BigDecimal.valueOf(100.0);
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.valueOf(5.0),
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(15.0));

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        for (int i = 0; i < consumption.size(); i++) {
            assertTrue(allocation.get(i).compareTo(consumption.get(i)) <= 0,
                    "Allocation should not exceed consumption for consumer " + i);
        }
    }

    @Test
    public void testPrecisionWith3Decimals() {
        // Verify all allocations are rounded to exactly 3 decimal places
        BigDecimal solarProduction = new BigDecimal("10.123456");
        List<BigDecimal> consumption = Arrays.asList(
                new BigDecimal("7.654321"),
                new BigDecimal("8.987654"));

        List<BigDecimal> allocation = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction,
                consumption);

        for (BigDecimal alloc : allocation) {
            assertEquals(3, alloc.scale(), "All allocations should have exactly 3 decimal places");
        }
    }
}
