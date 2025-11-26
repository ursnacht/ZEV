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
            // Find the consumer with the highest consumption
            int maxConsumptionIndex = 0;
            BigDecimal maxConsumption = currentConsumption.get(0);
            for (int i = 1; i < N; i++) {
                if (currentConsumption.get(i).compareTo(maxConsumption) > 0) {
                    maxConsumption = currentConsumption.get(i);
                    maxConsumptionIndex = i;
                }
            }
            // Adjust the allocation for the highest consumer
            BigDecimal adjusted = round3(allocation.get(maxConsumptionIndex).add(difference));
            allocation.set(maxConsumptionIndex, adjusted);
        }

        return allocation;
    }

    // --- Beispiel-Hauptmethode ---
    public static void main(String[] args) {
        // Beispielwerte aus der vorherigen Erklärung:
        BigDecimal solarProduction = BigDecimal.valueOf(10.0); // kW
        List<BigDecimal> consumption = Arrays.asList(
                BigDecimal.valueOf(2.0),
                BigDecimal.valueOf(8.0),
                BigDecimal.valueOf(5.0)); // kW (C1, C2, C3)

        System.out.println("--- ZEV-Verteilungssimulation (Fall B) ---");
        System.out.println("Solarproduktion: " + solarProduction + " kW");
        System.out.println("Verbraucher-Bedarf: " + consumption);

        List<BigDecimal> allocation = distributeSolarPower(solarProduction, consumption);

        System.out.println("\nErgebnisse:");
        BigDecimal totalAllocated = BigDecimal.ZERO;
        for (int i = 0; i < allocation.size(); i++) {
            BigDecimal allocated = allocation.get(i);
            BigDecimal remainingGrid = consumption.get(i).subtract(allocated);
            System.out.printf("Verbraucher %d: Zuteilung (Solar): %s kW | Restbezug (Netz): %s kW%n",
                    i + 1, allocated, remainingGrid);
            totalAllocated = totalAllocated.add(allocated);
        }

        System.out.printf("\nGesamte Zuteilung (Solar): %s kW (Soll: %s kW)%n",
                totalAllocated, solarProduction);

        // Zusätzliches Beispiel (Fall A: volle Deckung)
        System.out.println("\n--- ZEV-Verteilungssimulation (Fall A) ---");
        solarProduction = BigDecimal.valueOf(20.0); // kW
        consumption = Arrays.asList(
                BigDecimal.valueOf(5.0),
                BigDecimal.valueOf(3.0),
                BigDecimal.valueOf(4.0)); // Gesamt: 12 kW
        System.out.println("Solarproduktion: " + solarProduction + " kW");
        System.out.println("Verbraucher-Bedarf: " + consumption);

        allocation = distributeSolarPower(solarProduction, consumption);
        System.out.println("Zuteilung (Solar): " + allocation); // Soll: [5.0, 3.0, 4.0]
    }
}