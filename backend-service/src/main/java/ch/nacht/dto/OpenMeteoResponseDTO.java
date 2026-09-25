package ch.nacht.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Antwort von Open-Meteo, soweit sie hier gebraucht wird (Specs/Ladeplanung.md, FR-2).
 *
 * <p><b>{@code ignoreUnknown = true}:</b> Die API liefert ein Dutzend weiterer Felder (Höhe,
 * Zeitzone, Rechenzeit, Einheiten). Ein strikter Mapper bräche bei jeder Erweiterung der
 * Fremd-API — und zwar im Job, nachts.
 *
 * <p><b>Die Feldnamen stehen ausdrücklich:</b> Die API liefert {@code minutely_15} und
 * {@code global_tilted_irradiance} in Snake Case, die Anwendung hat keine globale Namensstrategie.
 * Ohne {@code @JsonProperty} bliebe alles {@code null} — und der Abruf schriebe still nichts.
 * Vorbild ist {@link BkwTariffsResponseDTO}.
 *
 * <p><b>Zeitstempel als {@code String}:</b> Sie kommen als {@code "2026-09-25T13:15"} ohne
 * Zonenangabe, weil der Abruf {@code timezone=Europe/Zurich} setzt. Das Parsen nach
 * {@link java.time.LocalDateTime} geschieht im Service — ein automatisches Mapping könnte je nach
 * Konfiguration eine Zone annehmen, die nicht dasteht.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoResponseDTO(
        @JsonProperty("timezone") String timezone,
        @JsonProperty("minutely_15") Minutely15 minutely15) {

    /**
     * Die 15-Minuten-Reihe. Beide Listen sind <b>parallel</b>: Eintrag i von {@code time} gehört
     * zu Eintrag i von {@code globalTiltedIrradiance}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Minutely15(
            @JsonProperty("time") List<String> time,
            /*
             * Einzelne Werte koennen null sein, wenn das Modell fuer ein Intervall nichts liefert.
             * Solche Intervalle werden uebersprungen, nicht als 0 geschrieben - eine 0 hiesse
             * "kein Licht" statt "nicht bekannt", und der gelernte Faktor wuerde daran verdorben.
             */
            @JsonProperty("global_tilted_irradiance") List<Double> globalTiltedIrradiance) {
    }
}
