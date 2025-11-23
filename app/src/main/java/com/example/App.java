package com.example;

import java.util.Arrays;
import java.util.List;

public class App {
    public static void main(String[] args) {
        // Beispielwerte aus der vorherigen Erklärung:
        double solarProduction = 10.0; // kW
        List<Double> consumption = Arrays.asList(2.0, 8.0, 5.0); // kW (C1, C2, C3)

        System.out.println("--- ZEV-Verteilungssimulation (Fall B) ---");
        System.out.println("Solarproduktion: " + solarProduction + " kW");
        System.out.println("Verbraucher-Bedarf: " + consumption);

        List<Double> allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);

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

        allocation = SolarDistribution.distributeSolarPower(solarProduction, consumption);
        System.out.println("Zuteilung (Solar): " + allocation); // Soll: [5.0, 3.0, 4.0]
    }
}
