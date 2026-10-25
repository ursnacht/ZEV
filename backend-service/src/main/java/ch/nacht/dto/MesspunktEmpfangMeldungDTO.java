package ch.nacht.dto;

/**
 * Payload der letzten Meldung eines Messpunkts (Specs/Messpunkt-Empfang.md, FR-2.9).
 *
 * <p>Eigener Endpunkt statt eines Felds in {@link MesspunktEmpfangDTO}: Die Liste bleibt schlank,
 * der Payload kommt einzeln, wenn ihn jemand ansehen will.
 */
public class MesspunktEmpfangMeldungDTO {

    /** Wie empfangen, höchstens 10'000 Zeichen; {@code null} bei Zeilen von vor V188. */
    private String payload;

    public MesspunktEmpfangMeldungDTO() {
    }

    public MesspunktEmpfangMeldungDTO(String payload) {
        this.payload = payload;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
