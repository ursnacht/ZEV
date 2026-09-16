package ch.nacht.entity;

public enum EinheitTyp {
    PRODUCER,
    CONSUMER,
    /** Bilanzmesspunkt am Netzanschluss: Bezug vom VNB (positiv). Max. eine Einheit je Mandant. */
    BEZUG,
    /** Bilanzmesspunkt am Netzanschluss: Rücklieferung an den VNB (negativ). Max. eine Einheit je Mandant. */
    RUECKLIEFERUNG,
    /**
     * Ladestation für Fahrzeuge (Specs/Ladestationen.md). Der {@code messpunkt} trägt die
     * <b>RFID</b>, mit der der Ladevorgang gestartet wird; beim Mieterwechsel wird die RFID
     * invalidiert und eine neue Einheit angelegt — dadurch gehört jede Einheit über ihre ganze
     * Lebensdauer genau einem Nutzer.
     *
     * <p>Ladestationen erhalten <b>keine</b> Messwerte: Ihre Mengen kommen als Tarifposition aus
     * dem Lademanagement, nicht vom Zähler. Sie nehmen deshalb weder an der Solarverteilung noch
     * an Aggregation oder Statistik teil.
     */
    LADESTATION,
    /**
     * Batteriespeicher am Hybrid-Wechselrichter (Specs/Batteriespeicher.md, FR-1).
     *
     * <p>Die beiden Register des Wire-Contracts tragen hier <b>Ladung</b>
     * ({@code zaehlerstandBezug}) und <b>Entladung</b> ({@code zaehlerstandEinspeisung}).
     * Ingest und Aggregation laufen dadurch unverändert: {@code total = ΔLadung − ΔEntladung}
     * — <b>positiv = lädt, negativ = entlädt</b>.
     *
     * <p><b>Nimmt nicht an der Solarverteilung teil.</b> Die Verteilung fragt typspezifisch nach
     * {@code PRODUCER} und {@code CONSUMER}; der Speicher taucht dort nicht auf. Das Bilanzmodell
     * bleibt damit unberührt.
     *
     * <p>Je Mandant höchstens <b>eine</b> Einheit dieses Typs (Validierung im
     * {@code EinheitService}).
     */
    SPEICHER
}
