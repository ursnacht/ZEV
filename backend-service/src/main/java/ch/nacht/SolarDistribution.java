package ch.nacht;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SolarDistribution {

    /**
     * Berechnet die faire Verteilung des Solarstroms auf die Verbraucher (ZEV-Mitglieder).
     *
     * @param solarProduction Aktuelle Solarproduktion in kW.
     * @param currentConsumption Liste des aktuellen Verbrauchs jedes Teilnehmers in kW.
     * @return Liste der Zuteilungen (Solarstrom-Bezug) für jeden Teilnehmer in kW.
     */
    public static List<Double> distributeSolarPower(double solarProduction, List<Double> currentConsumption) {
        int N = currentConsumption.size();
        if (N == 0 || solarProduction <= 0) {
            // Keine Verbraucher oder keine Produktion.
            List<Double> zeros = new ArrayList<>(N);
            for (int i = 0; i < N; i++) {
                zeros.add(0.0);
            }
            return zeros;
        }

        // 1. Berechnung des Gesamtverbrauchs und Initialisierung der Zuteilung
        double totalConsumption = currentConsumption.stream().mapToDouble(Double::doubleValue).sum();
        List<Double> allocation = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            allocation.add(0.0);
        }

        // --- Fall A: Produktion deckt den Gesamtbedarf ---
        if (solarProduction >= totalConsumption) {
            // Jeder erhält seinen vollen Bedarf aus der Solaranlage.
            for (int i = 0; i < N; i++) {
                allocation.set(i, round3(currentConsumption.get(i)));
            }

            // Adjust for rounding errors: ensure total distributed equals total consumption
            return adjustRounding(allocation, totalConsumption, currentConsumption);
            /*
            double totalDistributed = allocation.stream().mapToDouble(Double::doubleValue).sum();
            double difference = round3(totalConsumption - totalDistributed);

            if (Math.abs(difference) > 0.0) {
                // Find the consumer with the highest consumption
                int maxConsumptionIndex = 0;
                double maxConsumption = currentConsumption.get(0);
                for (int i = 1; i < N; i++) {
                    if (currentConsumption.get(i) > maxConsumption) {
                        maxConsumption = currentConsumption.get(i);
                        maxConsumptionIndex = i;
                    }
                }
                // Adjust the allocation for the highest consumer
                double adjusted = round3(allocation.get(maxConsumptionIndex) + difference);
                allocation.set(maxConsumptionIndex, adjusted);
            }

            // Überschuss = solarProduction - totalConsumption (wird hier nicht zurückgegeben, aber existiert)
            return allocation;
             */
        }

        // --- Fall B: Produktion ist kleiner als der Gesamtbedarf (Faire Verteilung) ---
        // Verteilung des Sroms. Zu Beginn ist der Rest gleich der Produktion
        double remainingPower = solarProduction;

        // Iterativer Prozess zur Verteilung des Rests
        while (remainingPower > 1e-6) { // Verwende eine Toleranz für Double-Vergleiche
            List<Integer> underSuppliedIndices = new ArrayList<>();
            double currentUncoveredDemand = 0.0;

            // Identifiziere unterversorgte Verbraucher und berechne ihren ungedeckten Bedarf
            for (int i = 0; i < N; i++) {
                double uncovered = currentConsumption.get(i) - allocation.get(i);
                if (uncovered > 1e-6) {
                    underSuppliedIndices.add(i);
                    currentUncoveredDemand += uncovered;
                }
            }

            if (underSuppliedIndices.isEmpty()) {
                // Sollte theoretisch nicht passieren, wenn remainingPower > 0, dient aber zur Sicherheit.
                break;
            }

            // Gleichmäßige Verteilung des Reststroms auf die unterversorgten Verbraucher, 
            // proportional zum verbleibenden ungedeckten Bedarf.

            // Verteilungsrate: entweder der Reststrom / Anzahl oder die anteilige Zuteilung, 
            // wenn der Reststrom kleiner ist als der Gesamtbedarf der unterversorgten.
            double share = remainingPower;
            if (currentUncoveredDemand > 0) {
                // Verteilung proportional zum ungedeckten Restbedarf.
                share = remainingPower / underSuppliedIndices.size();
            } else {
                // Alle sind vollständig versorgt, aber es ist noch ein minimaler Reststrom da. 
                // Dies wird normalerweise durch die Schleifenbedingung abgefangen.
                break;
            }

            for (int i : underSuppliedIndices) {
                double uncovered = currentConsumption.get(i) - allocation.get(i);

                // Zuteilung ist entweder der errechnete Anteil (share) oder der verbleibende Bedarf (uncovered).
                double extraAllocation = Math.min(uncovered, share);

                allocation.set(i, allocation.get(i) + extraAllocation);
                remainingPower -= extraAllocation;
            }
        }

        // Round all allocations to 3 decimal places
        for (int i = 0; i < N; i++) {
            allocation.set(i, round3(allocation.get(i)));
        }

        // Adjust for rounding errors: ensure total distributed equals solar production
        return adjustRounding(allocation, solarProduction, currentConsumption);
        /*
        double totalDistributed = allocation.stream().mapToDouble(Double::doubleValue).sum();
        double difference = round3(solarProduction - totalDistributed);

        if (Math.abs(difference) > 0.0) {
            // Find the consumer with the highest consumption
            int maxConsumptionIndex = 0;
            double maxConsumption = currentConsumption.get(0);
            for (int i = 1; i < N; i++) {
                if (currentConsumption.get(i) > maxConsumption) {
                    maxConsumption = currentConsumption.get(i);
                    maxConsumptionIndex = i;
                }
            }
            // Adjust the allocation for the highest consumer
            double adjusted = round3(allocation.get(maxConsumptionIndex) + difference);
            allocation.set(maxConsumptionIndex, adjusted);
        }

        return allocation;
         */
    }

    /**
     * Rounds a value to 3 decimal places.
     */
    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static List<Double> adjustRounding(List<Double> allocation, double targetAmount, List<Double> currentConsumption) {
        // Adjust for rounding errors: ensure total distributed equals target amount
        double totalDistributed = allocation.stream().mapToDouble(Double::doubleValue).sum();
        double difference = round3(targetAmount - totalDistributed);
        int N = currentConsumption.size();

        if (Math.abs(difference) > 0.0) {
            // Find the consumer with the highest consumption
            int maxConsumptionIndex = 0;
            double maxConsumption = currentConsumption.get(0);
            for (int i = 1; i < N; i++) {
                if (currentConsumption.get(i) > maxConsumption) {
                    maxConsumption = currentConsumption.get(i);
                    maxConsumptionIndex = i;
                }
            }
            // Adjust the allocation for the highest consumer
            double adjusted = round3(allocation.get(maxConsumptionIndex) + difference);
            allocation.set(maxConsumptionIndex, adjusted);
        }

        return allocation;
    }

    // --- Beispiel-Hauptmethode ---
    public static void main(String[] args) {
        // Beispielwerte aus der vorherigen Erklärung:
        double solarProduction = 10.0; // kW
        List<Double> consumption = Arrays.asList(2.0, 8.0, 5.0); // kW (C1, C2, C3)

        System.out.println("--- ZEV-Verteilungssimulation (Fall B) ---");
        System.out.println("Solarproduktion: " + solarProduction + " kW");
        System.out.println("Verbraucher-Bedarf: " + consumption);

        List<Double> allocation = distributeSolarPower(solarProduction, consumption);

        System.out.println("\nErgebnisse:");
        double totalAllocated = 0.0;
        for (int i = 0; i < allocation.size(); i++) {
            double allocated = allocation.get(i);
            double remainingGrid = consumption.get(i) - allocated;
            System.out.printf("Verbraucher %d: Zuteilung (Solar): %.2f kW | Restbezug (Netz): %.2f kW%n",
                    i + 1, allocated, remainingGrid);
            totalAllocated += allocated;
        }

        System.out.printf("\nGesamte Zuteilung (Solar): %.2f kW (Soll: %.2f kW)%n",
                totalAllocated, solarProduction);

        // Zusätzliches Beispiel (Fall A: volle Deckung)
        System.out.println("\n--- ZEV-Verteilungssimulation (Fall A) ---");
        solarProduction = 20.0; // kW
        consumption = Arrays.asList(5.0, 3.0, 4.0); // Gesamt: 12 kW
        System.out.println("Solarproduktion: " + solarProduction + " kW");
        System.out.println("Verbraucher-Bedarf: " + consumption);

        allocation = distributeSolarPower(solarProduction, consumption);
        System.out.println("Zuteilung (Solar): " + allocation); // Soll: [5.0, 3.0, 4.0]
    }
}