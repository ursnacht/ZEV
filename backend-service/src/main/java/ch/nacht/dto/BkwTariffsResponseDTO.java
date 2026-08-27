package ch.nacht.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Abbild der Antwort von {@code https://api.bkw.ch/api/dyntariffs/v1/Tariffs}
 * (Specs/Preiszeitreihe.md, §1 Datenquelle).
 *
 * <p>Beispiel (verifiziert am 27.08.2026, 96 Eintraege im 15-Minuten-Raster):
 * <pre>
 * {
 *   "publication_timestamp": "2026-08-27T13:50:00Z",
 *   "prices": [
 *     { "start_timestamp": "2026-08-26T22:00:00Z",
 *       "end_timestamp":   "2026-08-26T22:15:00Z",
 *       "feed_in": [ { "unit": "CHF_kWh", "value": 0.138 } ] }
 *   ]
 * }
 * </pre>
 *
 * <p>Die Feldnamen der Quelle sind {@code snake_case} und werden hier <b>je Feld</b> abgebildet —
 * nicht ueber eine globale Namensstrategie: Die eigene API bleibt bei {@code camelCase}, und eine
 * globale Umstellung wuerde sie mitziehen.
 *
 * <p>{@code feed_in} ist ein <b>Array</b>, enthaelt heute aber genau einen Eintrag. Fremddaten
 * werden vom Service geprueft, bevor etwas gespeichert wird: Einheit, Vollstaendigkeit, Menge.
 */
public record BkwTariffsResponseDTO(
        @JsonProperty("publication_timestamp") Instant publicationTimestamp,
        @JsonProperty("prices") List<BkwPrice> prices) {

    /** Ein Preisintervall der Quelle. */
    public record BkwPrice(
            @JsonProperty("start_timestamp") Instant startTimestamp,
            @JsonProperty("end_timestamp") Instant endTimestamp,
            @JsonProperty("feed_in") List<BkwValue> feedIn) {
    }

    /** Ein Preiswert samt Einheit. */
    public record BkwValue(
            @JsonProperty("unit") String unit,
            @JsonProperty("value") BigDecimal value) {
    }
}
