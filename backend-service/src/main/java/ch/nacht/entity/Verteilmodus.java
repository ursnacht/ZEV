package ch.nacht.entity;

/**
 * Verteilmodus je Mandant (in {@code einstellungen.konfiguration}, JSONB).
 * Steuert die Herkunft des ZEV-Eigenverbrauchs in {@code MesswerteService.distribute}.
 */
public enum Verteilmodus {
    /** Heutiges Verhalten: Verteilung der Producer-Produktion (Default). */
    PRODUCER_MESSUNG,
    /** Verteilung aus der Netz-Bilanz: {@code S = max(0, ConsumerTotal − Bezug)}. */
    BILANZ
}
