package ch.nacht.dto;

import java.util.List;

/**
 * Response DTO für die generische Datenbank-Ansicht.
 * {@code spalten} = Spaltennamen (ohne bytea); {@code zeilen} = Zeilen als Wert-Listen
 * (Werte als String repräsentiert). {@code hatMehr} = es existieren weitere Seiten.
 */
public class DatenbankAbfrageResponseDTO {

    private final List<String> spalten;
    private final List<List<Object>> zeilen;
    private final int seite;
    private final int groesse;
    private final boolean hatMehr;

    public DatenbankAbfrageResponseDTO(List<String> spalten, List<List<Object>> zeilen,
                                    int seite, int groesse, boolean hatMehr) {
        this.spalten = spalten;
        this.zeilen = zeilen;
        this.seite = seite;
        this.groesse = groesse;
        this.hatMehr = hatMehr;
    }

    public List<String> getSpalten() {
        return spalten;
    }

    public List<List<Object>> getZeilen() {
        return zeilen;
    }

    public int getSeite() {
        return seite;
    }

    public int getGroesse() {
        return groesse;
    }

    public boolean isHatMehr() {
        return hatMehr;
    }
}
