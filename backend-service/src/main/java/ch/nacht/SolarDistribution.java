package ch.nacht;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SolarDistribution {

    private static final BigDecimal EPSILON = new BigDecimal("0.000001");

    /**
     * Berechnet die faire Verteilung des Solarstroms auf die Verbraucher
     * (ZEV-Mitglieder).
     *
     * @param solarProduction    Aktuelle Solarproduktion in kW.
     * @param currentConsumption Liste des aktuellen Verbrauchs jedes Teilnehmers in
     *                           kW.
     * @return Liste der Zuteilungen (Solarstrom-Bezug) für jeden Teilnehmer in kW.
     */
    public static List<BigDecimal> distributeSolarPower(BigDecimal solarProduction,
            List<BigDecimal> currentConsumption) {
        int N = currentConsumption.size();
        if (N == 0 || solarProduction.compareTo(BigDecimal.ZERO) <= 0) {
            // Keine Verbraucher oder keine Produktion.
            List<BigDecimal> zeros = new ArrayList<>(N);
            for (int i = 0; i < N; i++) {
                zeros.add(BigDecimal.ZERO);
            }
            return zeros;
        }

        // 1. Berechnung des Gesamtverbrauchs und Initialisierung der Zuteilung
        BigDecimal totalConsumption = currentConsumption.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> allocation = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            allocation.add(BigDecimal.ZERO);
        }

        // --- Fall A: Produktion deckt den Gesamtbedarf ---
        if (solarProduction.compareTo(totalConsumption) >= 0) {
            // Jeder erhält seinen vollen Bedarf aus der Solaranlage.
            for (int i = 0; i < N; i++) {
                allocation.set(i, round3(currentConsumption.get(i)));
            }

            // Adjust for rounding errors: ensure total distributed equals total consumption
            return adjustRounding(allocation, totalConsumption, currentConsumption);
        }

        // --- Fall B: Produktion ist kleiner als der Gesamtbedarf (Faire Verteilung)
        // ---
        // Verteilung des Sroms. Zu Beginn ist der Rest gleich der Produktion
        BigDecimal remainingPower = solarProduction;

        // Iterativer Prozess zur Verteilung des Rests
        int maxIterations = 100; // Hard limit to prevent infinite loops
        int iteration = 0;
        while (remainingPower.compareTo(EPSILON) > 0 && iteration < maxIterations) {
            iteration++;
            List<Integer> underSuppliedIndices = new ArrayList<>();
            BigDecimal currentUncoveredDemand = BigDecimal.ZERO;

            // Identifiziere unterversorgte Verbraucher und berechne ihren ungedeckten
            // Bedarf
            for (int i = 0; i < N; i++) {
                BigDecimal uncovered = currentConsumption.get(i).subtract(allocation.get(i));
                if (uncovered.compareTo(EPSILON) > 0) {
                    underSuppliedIndices.add(i);
                    currentUncoveredDemand = currentUncoveredDemand.add(uncovered);
                }
            }

            if (underSuppliedIndices.isEmpty()) {
                break;
            }

            // Gleichmäßige Verteilung des Reststroms auf die unterversorgten Verbraucher,
            // proportional zum verbleibenden ungedeckten Bedarf.

            BigDecimal share = remainingPower;
            if (currentUncoveredDemand.compareTo(BigDecimal.ZERO) > 0) {
                // Verteilung proportional zum ungedeckten Restbedarf.
                // share = remainingPower / underSuppliedIndices.size();
                share = remainingPower.divide(BigDecimal.valueOf(underSuppliedIndices.size()), 10,
                        RoundingMode.HALF_UP);
            } else {
                break;
            }

            for (int i : underSuppliedIndices) {
                BigDecimal uncovered = currentConsumption.get(i).subtract(allocation.get(i));

                // Zuteilung ist entweder der errechnete Anteil (share) oder der verbleibende
                // Bedarf (uncovered).
                BigDecimal extraAllocation = uncovered.min(share);

                allocation.set(i, allocation.get(i).add(extraAllocation));
                remainingPower = remainingPower.subtract(extraAllocation);
            }
        }

        // Round all allocations to 3 decimal places
        for (int i = 0; i < N; i++) {
            allocation.set(i, round3(allocation.get(i)));
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
     * Adjust for rounding errors: ensure total distributed equals total consumption
     * Distributes the rounding difference across all einheiten, starting with the one
     * that needs the least energy.
     *
     * @param allocation
     * @param targetAmount
     * @param currentConsumption
     * @return
     */
    private static List<BigDecimal> adjustRounding(List<BigDecimal> allocation, BigDecimal targetAmount,
            List<BigDecimal> currentConsumption) {
        // Adjust for rounding errors: ensure total distributed equals target amount
        BigDecimal totalDistributed = allocation.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = round3(targetAmount.subtract(totalDistributed));
        int N = currentConsumption.size();

        if (difference.abs().compareTo(BigDecimal.ZERO) > 0) {
            // Create a list of indices sorted by consumption (ascending - lowest first)
            List<Integer> sortedIndices = new ArrayList<>();
            for (int i = 0; i < N; i++) {
                sortedIndices.add(i);
            }
            sortedIndices.sort((a, b) -> currentConsumption.get(a).compareTo(currentConsumption.get(b)));

            // Distribute the difference across einheiten, starting with lowest consumption
            BigDecimal increment = new BigDecimal("0.001");
            BigDecimal remainingDifference = difference;

            // Determine if we're adding or subtracting
            boolean isAddition = difference.compareTo(BigDecimal.ZERO) > 0;

            int currentIndex = 0;
            while (remainingDifference.abs().compareTo(EPSILON) > 0 && currentIndex < sortedIndices.size()) {
                int idx = sortedIndices.get(currentIndex);
                BigDecimal currentAllocation = allocation.get(idx);

                if (isAddition) {
                    // Add increment to this einheit
                    BigDecimal toAdd = increment.min(remainingDifference);
                    allocation.set(idx, round3(currentAllocation.add(toAdd)));
                    remainingDifference = remainingDifference.subtract(toAdd);
                } else {
                    // Subtract increment, but ensure we don't go negative
                    BigDecimal toSubtract = increment.min(remainingDifference.abs());
                    BigDecimal newValue = currentAllocation.subtract(toSubtract);

                    if (newValue.compareTo(BigDecimal.ZERO) >= 0) {
                        allocation.set(idx, round3(newValue));
                        remainingDifference = remainingDifference.add(toSubtract);
                    }
                }

                // Move to next einheit in the sorted list
                currentIndex++;

                // If we've gone through all einheiten but still have remainder, loop back
                if (currentIndex >= sortedIndices.size() && remainingDifference.abs().compareTo(EPSILON) > 0) {
                    currentIndex = 0;
                    // Use smaller increment for fine-tuning
                    if (increment.compareTo(new BigDecimal("0.001")) == 0) {
                        // Break if we can't make further progress
                        break;
                    }
                }
            }
        }

        return allocation;
    }

}