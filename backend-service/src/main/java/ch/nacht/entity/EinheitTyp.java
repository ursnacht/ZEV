package ch.nacht.entity;

public enum EinheitTyp {
    PRODUCER,
    CONSUMER,
    /** Bilanzmesspunkt am Netzanschluss: Bezug vom VNB (positiv). Max. eine Einheit je Mandant. */
    BEZUG,
    /** Bilanzmesspunkt am Netzanschluss: Rücklieferung an den VNB (negativ). Max. eine Einheit je Mandant. */
    RUECKLIEFERUNG
}
