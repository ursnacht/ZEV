package ch.nacht;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Proportional consumption-based solar distribution algorithm.
 * Distributes solar power proportionally to consumption - consumers with higher
 * consumption receive a proportionally larger share of available solar power.
 * Any remainder from rounding is allocated to the consumer with the highest
 * consumption.
 */
public class ProportionalConsumptionDistribution {

    private static final BigDecimal EPSILON = new BigDecimal("0.000001");

    /**
     * Distributes solar power proportionally to consumption.
     *
     * @param solarProduction    Current solar production in kW.
     * @param currentConsumption List of current consumption for each participant in
     *                           kW.
     * @return List of allocations (solar power received) for each participant in
     *         kW.
     */
    public static List<BigDecimal> distributeSolarPower(BigDecimal solarProduction,
            List<BigDecimal> currentConsumption) {
        int N = currentConsumption.size();
        if (N == 0 || solarProduction.compareTo(BigDecimal.ZERO) <= 0) {
            // No consumers or no production
            List<BigDecimal> zeros = new ArrayList<>(N);
            for (int i = 0; i < N; i++) {
                zeros.add(BigDecimal.ZERO);
            }
            return zeros;
        }

        // Calculate total consumption
        BigDecimal totalConsumption = currentConsumption.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Initialize allocation list
        List<BigDecimal> allocation = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            allocation.add(BigDecimal.ZERO);
        }

        // Handle zero total consumption edge case
        if (totalConsumption.compareTo(BigDecimal.ZERO) <= 0) {
            return allocation;
        }

        // --- Case A: Production covers total demand ---
        if (solarProduction.compareTo(totalConsumption) >= 0) {
            // Each consumer gets their full demand
            for (int i = 0; i < N; i++) {
                allocation.set(i, round3(currentConsumption.get(i)));
            }
            return adjustRounding(allocation, totalConsumption, currentConsumption);
        }

        // --- Case B: Production is less than total demand (Proportional Distribution)
        // ---
        // Distribute proportionally: allocation[i] = production * (consumption[i] /
        // totalConsumption)
        for (int i = 0; i < N; i++) {
            BigDecimal proportion = currentConsumption.get(i).divide(totalConsumption, 10, RoundingMode.HALF_UP);
            BigDecimal proportionalAllocation = solarProduction.multiply(proportion);
            allocation.set(i, round3(proportionalAllocation));
        }

        // Adjust for rounding errors: ensure total distributed equals solar production
        return adjustRounding(allocation, solarProduction, currentConsumption);
    }

    /**
     * Rounds a value to 3 decimal places.
     */
    private static BigDecimal round3(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Adjust for rounding errors: ensure total distributed equals target amount.
     * Distributes the rounding difference to the consumer with the highest
     * consumption.
     *
     * @param allocation         Current allocation list
     * @param targetAmount       Target total amount to distribute
     * @param currentConsumption List of current consumption
     * @return Adjusted allocation list
     */
    private static List<BigDecimal> adjustRounding(List<BigDecimal> allocation, BigDecimal targetAmount,
            List<BigDecimal> currentConsumption) {
        int N = currentConsumption.size();

        // Calculate the difference between target and current total
        BigDecimal totalDistributed = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = round3(targetAmount.subtract(totalDistributed));

        if (difference.abs().compareTo(EPSILON) > 0) {
            // Find the index of the consumer with the highest consumption
            int highestConsumerIndex = 0;
            BigDecimal highestConsumption = currentConsumption.get(0);

            for (int i = 1; i < N; i++) {
                if (currentConsumption.get(i).compareTo(highestConsumption) > 0) {
                    highestConsumption = currentConsumption.get(i);
                    highestConsumerIndex = i;
                }
            }

            // Allocate the remainder to the highest consumer in 0.001 increments
            BigDecimal increment = new BigDecimal("0.001");
            BigDecimal remainingDifference = difference;
            boolean isAddition = difference.compareTo(BigDecimal.ZERO) > 0;

            while (remainingDifference.abs().compareTo(EPSILON) > 0) {
                BigDecimal currentAllocation = allocation.get(highestConsumerIndex);
                BigDecimal maxAllowed = currentConsumption.get(highestConsumerIndex);

                if (isAddition) {
                    // Add increment, but don't exceed consumption
                    BigDecimal toAdd = increment.min(remainingDifference);
                    BigDecimal newValue = currentAllocation.add(toAdd);

                    if (newValue.compareTo(maxAllowed) <= 0) {
                        allocation.set(highestConsumerIndex, round3(newValue));
                        remainingDifference = remainingDifference.subtract(toAdd);
                    } else {
                        // Can't add more to highest consumer, break
                        break;
                    }
                } else {
                    // Subtract increment, but don't go negative
                    BigDecimal toSubtract = increment.min(remainingDifference.abs());
                    BigDecimal newValue = currentAllocation.subtract(toSubtract);

                    if (newValue.compareTo(BigDecimal.ZERO) >= 0) {
                        allocation.set(highestConsumerIndex, round3(newValue));
                        remainingDifference = remainingDifference.add(toSubtract);
                    } else {
                        // Can't subtract more, break
                        break;
                    }
                }
            }
        }

        // Final safety check: ensure no allocation exceeds consumption
        for (int i = 0; i < N; i++) {
            BigDecimal allocated = allocation.get(i);
            BigDecimal consumption = currentConsumption.get(i);
            if (allocated.compareTo(consumption) > 0) {
                allocation.set(i, round3(consumption));
            }
        }

        return allocation;
    }
}
