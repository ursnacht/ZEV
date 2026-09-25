package ch.nacht.controller;

import ch.nacht.dto.PrognosepunktDTO;
import ch.nacht.dto.SimulationDTO;
import ch.nacht.dto.SteuerentscheidDTO;
import ch.nacht.service.ProduktionsprognoseService;
import ch.nacht.service.SteuerungService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * REST-Endpunkte der Einspeisesteuerung (Specs/Einspeisesteuerung.md, FR-4).
 *
 * <p>Dieselbe Permission wie die Preiszeitreihe, mit der sich die Ansicht ihre Datengrundlage
 * teilt: {@code tarife:manage} — also <b>org_admin</b> und <b>zev_admin</b>, nicht {@code zev_user}.
 * Die Seite zeigt die Bewirtschaftungslogik der Anlage, nicht Verbrauchsdaten eines Mieters.
 *
 * <p>Zusätzlich prüft der Service das Feature-Flag: Ohne das wäre der Flag reine Kosmetik, denn
 * der Endpunkt ist über jeden HTTP-Client erreichbar.
 */
@RestController
@RequestMapping("/api/einspeisesteuerung")
@PreAuthorize("hasAuthority('tarife:manage')")
public class SteuerungController {

    private static final Logger log = LoggerFactory.getLogger(SteuerungController.class);

    /** Obergrenze der Rückrechnung. Ohne sie zöge ein getippter Bereich die ganze Historie. */
    private static final int MAX_TAGE = 366;

    private final SteuerungService steuerungService;
    private final ProduktionsprognoseService produktionsprognoseService;

    public SteuerungController(SteuerungService steuerungService,
                               ProduktionsprognoseService produktionsprognoseService) {
        this.steuerungService = steuerungService;
        this.produktionsprognoseService = produktionsprognoseService;
        log.info("SteuerungController initialized");
    }

    /**
     * Entscheide eines Ortstages.
     *
     * <p>Ohne Treffer {@code 200} mit leerer Liste — kein {@code 404}: Ein Tag ohne Entscheide ist
     * kein Fehler, sondern ein Tag, an dem die Steuerung nicht lief.
     *
     * @param datum Tag in Ortszeit (Europe/Zurich)
     * @return Entscheide des Tages, aufsteigend
     */
    @GetMapping("/entscheide")
    public List<SteuerentscheidDTO> getEntscheide(
            @RequestParam("datum") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum) {
        return steuerungService.getEntscheide(datum);
    }

    /**
     * Entscheide eines Ortstages, <b>nachgerechnet</b> mit abweichenden Schwellen (FR-6a).
     *
     * <p><b>Verändert nichts.</b> Das Ergebnis hat dieselbe Form wie {@code /entscheide}, stammt
     * aber aus der Rechnung statt aus der Aufzeichnung — die Tagesansicht zeigt damit, <i>wann</i>
     * ein anderer Schwellwert gesperrt hätte.
     *
     * <p><b>Eigener Pfad statt eines optionalen Parameters auf {@code /entscheide}:</b> Beide
     * liefern dieselbe Form, meinen aber Verschiedenes — Protokoll gegen Hypothese. Ein Aufrufer,
     * der den Parameter übersieht, hielte sonst eine Rechnung für eine Aufzeichnung.
     *
     * @param datum        Tag in Ortszeit (Europe/Zurich)
     * @param schwellwert  zu erprobender Schwellwert; darf negativ sein
     * @param speicherwert zu erprobender Speicherwert; fehlt er, gilt der des Mandanten
     * @param mindestAbstand zu erprobender Mindest-Preisabstand; fehlt er, gilt der des Mandanten
     */
    @GetMapping("/entscheide/simuliert")
    public ResponseEntity<?> getEntscheideSimuliert(
            @RequestParam("datum") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum,
            @RequestParam("schwellwert") BigDecimal schwellwert,
            @RequestParam(value = "speicherwert", required = false) BigDecimal speicherwert,
            @RequestParam(value = "mindestAbstand", required = false) BigDecimal mindestAbstand) {
        if (schwellwert == null) {
            return ResponseEntity.badRequest().body("schwellwert ist Pflicht");
        }
        if (mindestAbstand != null && mindestAbstand.signum() < 0) {
            return ResponseEntity.badRequest().body("mindestAbstand darf nicht negativ sein");
        }
        return ResponseEntity.ok(steuerungService.getEntscheideSimuliert(
                datum, schwellwert, speicherwert, mindestAbstand));
    }

    /**
     * Die Produktionsprognose eines Ortstages (Specs/Ladeplanung.md, FR-3).
     *
     * <p><b>Eigener Endpunkt, nicht Teil der Entscheide.</b> Die Prognose beschreibt die Zukunft;
     * Entscheide gibt es nur fuer abgeschlossene Intervalle. Haengte sie daran, waere der Resttag
     * nie sichtbar - also genau der Teil, um den es geht.
     *
     * <p>Leer, wenn fuer den Tag keine Prognose vorliegt. Das ist kein Fehler: Vor dem ersten
     * Abruf gibt es keine, und ohne Standort wird gar nicht erst geholt.
     *
     * @param datum Tag in Ortszeit (Europe/Zurich)
     */
    @GetMapping("/prognose")
    public ResponseEntity<List<PrognosepunktDTO>> getPrognose(
            @RequestParam("datum") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum) {
        return ResponseEntity.ok(produktionsprognoseService.getPrognose(datum));
    }

    /**
     * Rechnet die Regel über einen Zeitraum mit abweichenden Schwellen nach.
     *
     * <p><b>Verändert nichts.</b> Gespeicherte Entscheide bleiben unberührt; das Ergebnis dient
     * allein dazu, den Schwellwert einzugrenzen.
     *
     * <p>Fehlerrümpfe sind <b>Klartext</b>, kein Objekt — ein Objekt erscheint in der Maske als
     * {@code [object Object]}.
     */
    @PostMapping("/simulation")
    public ResponseEntity<?> simuliere(@RequestBody SimulationAnfrage anfrage) {
        if (anfrage == null || anfrage.von() == null || anfrage.bis() == null) {
            return ResponseEntity.badRequest().body("von und bis sind Pflicht");
        }
        if (anfrage.von().isAfter(anfrage.bis())) {
            return ResponseEntity.badRequest().body("von muss vor oder gleich bis liegen");
        }
        long tage = ChronoUnit.DAYS.between(anfrage.von(), anfrage.bis()) + 1;
        if (tage > MAX_TAGE) {
            return ResponseEntity.badRequest()
                    .body("Zeitraum umfasst " + tage + " Tage; erlaubt sind hoechstens " + MAX_TAGE);
        }
        if (anfrage.schwellwert() == null) {
            return ResponseEntity.badRequest().body("schwellwert ist Pflicht");
        }
        // Negativ hiesse, auch auf ein TEURERES Intervall zu warten - das ist keine Erprobung,
        // sondern eine Umkehrung der Regel.
        if (anfrage.mindestAbstand() != null && anfrage.mindestAbstand().signum() < 0) {
            return ResponseEntity.badRequest().body("mindestAbstand darf nicht negativ sein");
        }

        SimulationDTO ergebnis = steuerungService.simuliere(
                anfrage.von(), anfrage.bis(), anfrage.schwellwert(), anfrage.speicherwert(),
                anfrage.mindestAbstand());
        return ResponseEntity.ok(ergebnis);
    }

    /**
     * Rumpf der Rückrechnung.
     *
     * @param von          erster Ortstag (einschliesslich)
     * @param bis          letzter Ortstag (einschliesslich)
     * @param schwellwert  zu erprobender Schwellwert; darf negativ sein
     * @param speicherwert zu erprobender Speicherwert; {@code null} → Wert des Mandanten
     * @param mindestAbstand zu erprobender Mindest-Preisabstand; {@code null} → Wert des Mandanten
     */
    public record SimulationAnfrage(LocalDate von, LocalDate bis,
                                    BigDecimal schwellwert, BigDecimal speicherwert,
                                    BigDecimal mindestAbstand) {
    }
}
