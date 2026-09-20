package ch.nacht.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft den <b>Upsert-Text</b> von {@link SteuerentscheidRepository} — ohne Datenbank.
 *
 * <p><b>Warum es diese Klasse gibt.</b> Der Upsert ist natives SQL in einer Annotation. Kein
 * Compiler sieht hinein, und die Unit-Tests der Services mocken das Repository weg: Ein Fehler
 * darin fällt erst zur Laufzeit auf, im Job, der viertelstündlich läuft und dessen Ausfall
 * niemandem sofort auffällt.
 *
 * <p>Genau das ist am 18.09.2026 passiert. Beim Ergänzen von {@code soc_minimum} und
 * {@code soc_hysterese} kamen die Spalten in die Zielliste, die zugehörigen Platzhalter aber
 * nicht in {@code VALUES} — die Anlage quittierte jeden Lauf mit
 * {@code INSERT has more target columns than expressions}. Zwei Migrationen und 1335 grüne Tests
 * später.
 *
 * <p>Die beiden Prüfungen hier sind bewusst stumpf: Sie zählen und vergleichen Namen. Genau die
 * Arbeit, die man beim Ergänzen einer Spalte von Hand macht — und dabei übersieht.
 */
public class SteuerentscheidUpsertQueryTest {

    /** Spalten, die beim Überschreiben <b>nicht</b> mitgeführt werden. */
    private static final List<String> OHNE_UPDATE = List.of("org_id", "zeit_von");

    private String upsertSql() throws NoSuchMethodException {
        Method upsert = Arrays.stream(SteuerentscheidRepository.class.getMethods())
                .filter(m -> m.getName().equals("upsert"))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("upsert"));
        return upsert.getAnnotation(Query.class).value();
    }

    @Test
    void upsert_ZielspaltenUndWerte_SindGleichViele() throws Exception {
        String sql = upsertSql();

        List<String> spalten = liste(sql, "steuerentscheid (", ")");
        List<String> werte = liste(sql, "VALUES (", ")");

        assertEquals(spalten.size(), werte.size(),
                "Zielspalten und VALUES-Ausdruecke muessen gleich viele sein. Spalten: "
                        + spalten + " / Werte: " + werte);
    }

    @Test
    void upsert_JedeSpalte_WirdBeimUeberschreibenGesetzt() throws Exception {
        String sql = upsertSql();
        String doUpdate = sql.substring(sql.indexOf("DO UPDATE SET"));

        List<String> fehlend = new ArrayList<>();
        for (String spalte : liste(sql, "steuerentscheid (", ")")) {
            if (OHNE_UPDATE.contains(spalte)) {
                continue;
            }
            // Eine vergessene Spalte hier waere still: Der erste Lauf schriebe sie, ein zweiter
            // ueber dasselbe Intervall liesse den alten Wert stehen - und niemand saehe es.
            if (!doUpdate.contains(spalte + " ") && !doUpdate.contains(spalte + "=")) {
                fehlend.add(spalte);
            }
        }

        assertTrue(fehlend.isEmpty(),
                "Diese Spalten fehlen im DO UPDATE SET und blieben beim Ueberschreiben stehen: "
                        + fehlend);
    }

    /** Die Einträge zwischen zwei Markierungen, per Komma getrennt und getrimmt. */
    private List<String> liste(String sql, String start, String ende) {
        int von = sql.indexOf(start) + start.length();
        int bis = sql.indexOf(ende, von);
        return Arrays.stream(sql.substring(von, bis).split(","))
                .map(String::trim)
                .filter(eintrag -> !eintrag.isEmpty())
                .toList();
    }
}
