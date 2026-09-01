package ch.nacht.service;

import ch.nacht.dto.NkAbrechnungDetailDTO;
import ch.nacht.dto.NkAkontoDTO;
import ch.nacht.dto.NkPersonDTO;
import ch.nacht.dto.NkMieterBasisDTO;
import ch.nacht.dto.NkPositionDTO;
import ch.nacht.dto.NkVerbrauchDTO;
import ch.nacht.dto.NkZusatzDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.MieterEinheit;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkAkonto;
import ch.nacht.entity.NkPerson;
import ch.nacht.entity.NkPosition;
import ch.nacht.entity.NkPositionsart;
import ch.nacht.entity.NkVerbrauch;
import ch.nacht.entity.NkZusatz;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MieterEinheitRepository;
import ch.nacht.repository.MieterRepository;
import ch.nacht.repository.NkAbrechnungRepository;
import ch.nacht.repository.NkAkontoRepository;
import ch.nacht.repository.NkPersonRepository;
import ch.nacht.repository.NkPositionRepository;
import ch.nacht.repository.NkVerbrauchRepository;
import ch.nacht.repository.NkZusatzRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Verwaltung der Nebenkostenabrechnungen (Specs/Nebenkosten/Abrechnung.md).
 *
 * <p>Jede öffentliche Methode beginnt mit {@link #pruefeFeatureFlag()} <b>und</b>
 * {@code enableOrgFilter()}. Der Flag-Aufruf ist bewusst explizit und nicht als Aspect gelöst: Er
 * steht damit im Code sichtbar neben dem Org-Filter und folgt dessen Muster. Der Preis ist
 * bekannt — wird er in einer neuen Methode vergessen, ist sie ungeschützt; ein Architekturtest
 * sichert das ab.
 *
 * <p>Gerechnet wird nirgends: Das macht {@link NkBerechnungService}, der ohne Datenbank auskommt.
 * Dieser Service lädt, prüft und speichert.
 */
@Service
public class NkAbrechnungService {

    private static final Logger log = LoggerFactory.getLogger(NkAbrechnungService.class);

    /** Spaltenbreite von {@code nk_abrechnung.bezeichnung}. */
    private static final int BEZEICHNUNG_MAX_LAENGE = 150;

    private final NkAbrechnungRepository abrechnungRepository;
    private final NkPositionRepository positionRepository;
    private final NkVerbrauchRepository verbrauchRepository;
    private final NkZusatzRepository zusatzRepository;
    private final NkAkontoRepository akontoRepository;
    private final NkPersonRepository personRepository;
    private final MieterRepository mieterRepository;
    private final MieterEinheitRepository mieterEinheitRepository;
    private final EinheitRepository einheitRepository;
    private final NkBerechnungService berechnungService;
    private final FeatureFlagService featureFlagService;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public NkAbrechnungService(NkAbrechnungRepository abrechnungRepository,
                               NkPositionRepository positionRepository,
                               NkVerbrauchRepository verbrauchRepository,
                               NkZusatzRepository zusatzRepository,
                               NkAkontoRepository akontoRepository,
                               NkPersonRepository personRepository,
                               MieterRepository mieterRepository,
                               MieterEinheitRepository mieterEinheitRepository,
                               EinheitRepository einheitRepository,
                               NkBerechnungService berechnungService,
                               FeatureFlagService featureFlagService,
                               OrganizationContextService organizationContextService,
                               HibernateFilterService hibernateFilterService) {
        this.abrechnungRepository = abrechnungRepository;
        this.positionRepository = positionRepository;
        this.verbrauchRepository = verbrauchRepository;
        this.zusatzRepository = zusatzRepository;
        this.akontoRepository = akontoRepository;
        this.personRepository = personRepository;
        this.mieterRepository = mieterRepository;
        this.mieterEinheitRepository = mieterEinheitRepository;
        this.einheitRepository = einheitRepository;
        this.berechnungService = berechnungService;
        this.featureFlagService = featureFlagService;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Alle Abrechnungen, neuste zuerst.
     *
     * @return Liste der Abrechnungen
     */
    @Transactional(readOnly = true)
    public List<NkAbrechnung> getAllAbrechnungen() {
        pruefeFeatureFlag();
        hibernateFilterService.enableOrgFilter();
        return abrechnungRepository.findAllByOrderByDatumVonDesc();
    }

    /**
     * Eine Abrechnung samt Positionen, Mieterblöcken und berechneten Beträgen.
     *
     * @param id ID der Abrechnung
     * @return Die zusammengesetzte Antwort, falls die Abrechnung existiert
     */
    @Transactional(readOnly = true)
    public Optional<NkAbrechnungDetailDTO> getAbrechnungDetail(Long id) {
        pruefeFeatureFlag();
        hibernateFilterService.enableOrgFilter();
        return abrechnungRepository.findFirstById(id).map(this::baueDetail);
    }

    /**
     * Vorlage für eine neue Abrechnung: leere Listen und die vorgeschlagene Anzahl Wohnungen.
     *
     * <p>Eigener Endpunkt, weil die Maske die Zahl schon <b>vor</b> dem ersten Speichern braucht —
     * ohne sie müsste der Benutzer den Nenner der Umlage raten.
     *
     * @return Vorlage ohne ID
     */
    @Transactional(readOnly = true)
    public NkAbrechnungDetailDTO getVorlage() {
        pruefeFeatureFlag();
        hibernateFilterService.enableOrgFilter();

        NkAbrechnungDetailDTO detail = new NkAbrechnungDetailDTO();
        detail.setAbrechnung(new NkAbrechnung());
        detail.setAnzahlWohnungenVorschlag(vorschlagAnzahlWohnungen());
        detail.setAnzahlPersonenVorschlag(vorschlagAnzahlWohnungen());
        return detail;
    }

    /**
     * Legt eine Abrechnung an.
     *
     * @param abrechnung Die neue Abrechnung
     * @return Die gespeicherte Abrechnung
     * @throws IllegalArgumentException bei ungültigem Zeitraum oder ungültiger Anzahl Wohnungen
     */
    @Transactional
    public NkAbrechnung createAbrechnung(NkAbrechnung abrechnung) {
        pruefeFeatureFlag();
        hibernateFilterService.enableOrgFilter();
        log.info("Creating Nebenkostenabrechnung: {}", abrechnung);

        ergaenzeAnzahlPersonen(abrechnung);
        pruefeKopf(abrechnung);
        abrechnung.setId(null);
        abrechnung.setOrgId(organizationContextService.getCurrentOrgId());
        return abrechnungRepository.save(abrechnung);
    }

    /**
     * Speichert Kopf, Positionen, Mengen, Zusatzpositionen und Akonto einer Abrechnung.
     *
     * <p>Positionen und Zeilen werden <b>ersetzt</b>, nicht abgeglichen: Die Maske schickt immer
     * den vollständigen Stand, und die Reihenfolge ergibt sich aus der Listenposition (Drag &amp;
     * Drop). Ein Abgleich je Zeile brächte nur die Frage mit, was mit fehlenden Zeilen geschieht.
     *
     * @param id ID der Abrechnung
     * @param detail Der vollständige neue Stand
     * @return Der neu geladene und berechnete Stand
     * @throws IllegalArgumentException bei Validierungsfehlern oder abgeschlossener Abrechnung
     */
    @Transactional
    public Optional<NkAbrechnungDetailDTO> saveAbrechnung(Long id, NkAbrechnungDetailDTO detail) {
        pruefeFeatureFlag();
        hibernateFilterService.enableOrgFilter();

        Optional<NkAbrechnung> vorhanden = abrechnungRepository.findFirstById(id);
        if (vorhanden.isEmpty()) {
            log.warn("Nebenkostenabrechnung {} not found", id);
            return Optional.empty();
        }

        NkAbrechnung abrechnung = vorhanden.get();
        pruefeNichtAbgerechnet(abrechnung);

        NkAbrechnung neu = detail.getAbrechnung();
        if (neu != null) {
            // Vorgabe vor der Pruefung setzen: Ein Aufrufer, der das Feld nicht kennt, soll nicht
            // an einer Meldung scheitern, deren Antwort die Anzahl Wohnungen ist.
            ergaenzeAnzahlPersonen(neu);
            pruefeKopf(neu);
            abrechnung.setBezeichnung(neu.getBezeichnung());
            abrechnung.setDatumVon(neu.getDatumVon());
            abrechnung.setDatumBis(neu.getDatumBis());
            abrechnung.setAnzahlWohnungen(neu.getAnzahlWohnungen());
            abrechnung.setAnzahlPersonen(neu.getAnzahlPersonen());
        }

        List<NkMieterBasisDTO> mieter = ladeMieter(abrechnung);
        pruefeNenner(abrechnung, mieter);
        pruefeNennerPerson(abrechnung, mieter, detail.getPositionen(), detail.getPersonen());
        pruefePositionen(detail.getPositionen());

        Long orgId = organizationContextService.getCurrentOrgId();
        // Nur Mieter im Zeitraum der Abrechnung: Wird der Zeitraum verschoben, fallen Mieter heraus,
        // und ihre Angaben verschwinden mit diesem Speichern (FR-8). Die ersetze-Methoden loeschen
        // ohnehin alles und schreiben neu - was hier durchfaellt, ist damit weg.
        Set<Long> imZeitraum = mieterIds(mieter);
        ersetzePositionen(abrechnung, detail.getPositionen(), orgId, imZeitraum);
        ersetzeZusaetze(abrechnung, detail.getZusaetze(), orgId, imZeitraum);
        ersetzeAkonto(abrechnung, detail.getAkonto(), orgId, imZeitraum);
        ersetzePersonen(abrechnung, detail.getPersonen(), orgId, imZeitraum);

        abrechnungRepository.save(abrechnung);
        log.info("Saved Nebenkostenabrechnung {} with {} positions", id, detail.getPositionen().size());

        return Optional.of(baueDetail(abrechnung));
    }

    /**
     * Kopiert eine Abrechnung samt allem, was zu ihr gehört (FR-8).
     *
     * <p>Zweck ist der Jahreswechsel: Die Abrechnung des Vorjahres ist die Vorlage für die neue.
     * Kopiert werden Kopf, Positionen, erfasste Mengen und Prozentsätze, Zusatzpositionen, Akonto
     * und die Personenzahlen — mit neuen IDs, damit die Kopie vom Original unabhängig ist.
     *
     * <p><b>Auch eine abgeschlossene Abrechnung ist kopierbar</b> (kein
     * {@link #pruefeNichtAbgerechnet}): Genau die ist der typische Ausgangspunkt. Die Kopie selbst
     * ist immer <b>offen</b> — sonst müsste man sie erst wieder aufschliessen, um sie zu bearbeiten.
     *
     * <p>Der Zeitraum bleibt zunächst derselbe. Wird er in der Maske geändert und fällt dadurch ein
     * Mieter heraus, verschwinden dessen Angaben beim nächsten Speichern (s.
     * {@link #ersetzeAkonto} und die übrigen {@code ersetze}-Methoden).
     *
     * @param id ID der Vorlage
     * @param bezeichnung Bezeichnung der Kopie; leer = die des Originals
     * @return Die neue Abrechnung samt Berechnung, falls die Vorlage existiert
     */
    @Transactional
    public Optional<NkAbrechnungDetailDTO> kopiereAbrechnung(Long id, String bezeichnung) {
        pruefeFeatureFlag();
        hibernateFilterService.enableOrgFilter();

        Optional<NkAbrechnung> vorhanden = abrechnungRepository.findFirstById(id);
        if (vorhanden.isEmpty()) {
            log.warn("Nebenkostenabrechnung {} not found for copy", id);
            return Optional.empty();
        }

        NkAbrechnung vorlage = vorhanden.get();
        Long orgId = organizationContextService.getCurrentOrgId();

        NkAbrechnung kopie = new NkAbrechnung();
        kopie.setOrgId(orgId);
        kopie.setBezeichnung(bezeichnungDerKopie(vorlage, bezeichnung));
        kopie.setDatumVon(vorlage.getDatumVon());
        kopie.setDatumBis(vorlage.getDatumBis());
        kopie.setAnzahlWohnungen(vorlage.getAnzahlWohnungen());
        kopie.setAnzahlPersonen(vorlage.getAnzahlPersonen());
        kopie.setAbgerechnet(false);
        NkAbrechnung gespeichert = abrechnungRepository.save(kopie);

        int positionen = kopierePositionen(vorlage.getId(), gespeichert.getId(), orgId);
        kopiereZusaetze(vorlage.getId(), gespeichert.getId(), orgId);
        kopiereAkonto(vorlage.getId(), gespeichert.getId(), orgId);
        kopierePersonen(vorlage.getId(), gespeichert.getId(), orgId);

        log.info("Copied Nebenkostenabrechnung {} to {} with {} positions",
                id, gespeichert.getId(), positionen);

        return Optional.of(baueDetail(gespeichert));
    }

    /**
     * Bezeichnung der Kopie — gekürzt auf die Spaltenbreite.
     *
     * <p>Der Text kommt vom Aufrufer und nicht aus dem Backend: Der Zusatz „(Kopie)" ist ein
     * Anzeigetext und gehört damit zu den Übersetzungen im Frontend. Ohne Angabe behält die Kopie
     * den Namen des Originals — dann sind zwei gleich benannte Abrechnungen sichtbar, was
     * unschön aber nicht falsch ist; die Maske öffnet ohnehin direkt danach.
     */
    private String bezeichnungDerKopie(NkAbrechnung vorlage, String bezeichnung) {
        String text = bezeichnung != null && !bezeichnung.isBlank()
                ? bezeichnung.trim() : vorlage.getBezeichnung();
        return text.length() > BEZEICHNUNG_MAX_LAENGE
                ? text.substring(0, BEZEICHNUNG_MAX_LAENGE) : text;
    }

    /** Positionen samt erfasster Mengen; gibt die Anzahl kopierter Positionen zurück. */
    private int kopierePositionen(Long vonAbrechnung, Long nachAbrechnung, Long orgId) {
        List<NkPosition> positionen =
                positionRepository.findByAbrechnungIdOrderByReihenfolge(vonAbrechnung);
        for (NkPosition alt : positionen) {
            NkPosition neu = new NkPosition();
            neu.setOrgId(orgId);
            neu.setAbrechnungId(nachAbrechnung);
            neu.setArt(alt.getArt());
            neu.setBezeichnung(alt.getBezeichnung());
            neu.setReihenfolge(alt.getReihenfolge());
            neu.setEinheit(alt.getEinheit());
            neu.setTotalbetrag(alt.getTotalbetrag());
            neu.setGesamtmenge(alt.getGesamtmenge());
            neu.setBetragProEinheit(alt.getBetragProEinheit());
            neu.setProzentsatz(alt.getProzentsatz());
            NkPosition gespeichert = positionRepository.save(neu);

            // Die Mengen haengen an der Position, nicht an der Abrechnung - sie muessen auf die
            // NEUE Positions-ID zeigen, sonst gehoerten sie weiterhin zum Original.
            for (NkVerbrauch v : verbrauchRepository.findByPositionId(alt.getId())) {
                NkVerbrauch kopie = new NkVerbrauch(gespeichert.getId(), v.getMieterId(), v.getMenge());
                kopie.setOrgId(orgId);
                verbrauchRepository.save(kopie);
            }
        }
        return positionen.size();
    }

    private void kopiereZusaetze(Long vonAbrechnung, Long nachAbrechnung, Long orgId) {
        for (NkZusatz alt : zusatzRepository
                .findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(vonAbrechnung)) {
            NkZusatz neu = new NkZusatz();
            neu.setOrgId(orgId);
            neu.setAbrechnungId(nachAbrechnung);
            neu.setMieterId(alt.getMieterId());
            neu.setReihenfolge(alt.getReihenfolge());
            neu.setBezeichnung(alt.getBezeichnung());
            neu.setEinheit(alt.getEinheit());
            neu.setMenge(alt.getMenge());
            neu.setBetragProEinheit(alt.getBetragProEinheit());
            zusatzRepository.save(neu);
        }
    }

    private void kopiereAkonto(Long vonAbrechnung, Long nachAbrechnung, Long orgId) {
        for (NkAkonto alt : akontoRepository.findByAbrechnungId(vonAbrechnung)) {
            NkAkonto neu = new NkAkonto();
            neu.setOrgId(orgId);
            neu.setAbrechnungId(nachAbrechnung);
            neu.setMieterId(alt.getMieterId());
            neu.setAnzahlMonate(alt.getAnzahlMonate());
            neu.setBetragProMonat(alt.getBetragProMonat());
            neu.setKorrektur(alt.getKorrektur());
            akontoRepository.save(neu);
        }
    }

    private void kopierePersonen(Long vonAbrechnung, Long nachAbrechnung, Long orgId) {
        for (NkPerson alt : personRepository.findByAbrechnungId(vonAbrechnung)) {
            personRepository.save(new NkPerson(orgId, nachAbrechnung,
                    alt.getMieterId(), alt.getAnzahlPersonen()));
        }
    }

    /**
     * Setzt oder löst das Flag „abgerechnet".
     *
     * <p>Der einzige Schreibzugriff, der auf einer abgeschlossenen Abrechnung erlaubt ist — sonst
     * liesse sie sich nie wieder öffnen.
     *
     * @param id ID der Abrechnung
     * @param abgerechnet Neuer Wert
     * @return Die geänderte Abrechnung, falls vorhanden
     */
    @Transactional
    public Optional<NkAbrechnung> setAbgerechnet(Long id, boolean abgerechnet) {
        pruefeFeatureFlag();
        hibernateFilterService.enableOrgFilter();

        return abrechnungRepository.findFirstById(id).map(abrechnung -> {
            abrechnung.setAbgerechnet(abgerechnet);
            log.info("Nebenkostenabrechnung {} abgerechnet={}", id, abgerechnet);
            return abrechnungRepository.save(abrechnung);
        });
    }

    /**
     * Löscht eine Abrechnung samt Positionen, Mengen, Zusatzpositionen und Akonto
     * (Fremdschlüssel {@code ON DELETE CASCADE}).
     *
     * @param id ID der Abrechnung
     * @return true, wenn gelöscht
     */
    @Transactional
    public boolean deleteAbrechnung(Long id) {
        pruefeFeatureFlag();
        hibernateFilterService.enableOrgFilter();

        if (!abrechnungRepository.existsById(id)) {
            log.warn("Nebenkostenabrechnung {} not found for deletion", id);
            return false;
        }
        abrechnungRepository.deleteById(id);
        log.info("Deleted Nebenkostenabrechnung {}", id);
        return true;
    }

    // ===================== Zusammensetzen =====================

    private NkAbrechnungDetailDTO baueDetail(NkAbrechnung abrechnung) {
        List<NkPosition> positionen = positionRepository.findByAbrechnungIdOrderByReihenfolge(abrechnung.getId());
        List<NkVerbrauch> verbraeuche = verbrauchRepository.findByAbrechnungId(abrechnung.getId());
        List<NkZusatz> zusaetze = zusatzRepository.findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(abrechnung.getId());
        List<NkAkonto> akonto = akontoRepository.findByAbrechnungId(abrechnung.getId());
        List<NkPerson> personen = personRepository.findByAbrechnungId(abrechnung.getId());
        List<NkMieterBasisDTO> mieter = ladeMieter(abrechnung);

        NkAbrechnungDetailDTO detail = new NkAbrechnungDetailDTO();
        detail.setAbrechnung(abrechnung);
        detail.setPositionen(zuPositionsDTOs(positionen, verbraeuche));
        detail.setZusaetze(zuZusatzDTOs(zusaetze));
        detail.setAkonto(zuAkontoDTOs(akonto));
        detail.setPersonen(zuPersonDTOs(personen));
        detail.setAnzahlWohnungenVorschlag(vorschlagAnzahlWohnungen());
        detail.setAnzahlPersonenVorschlag(vorschlagAnzahlWohnungen());
        detail.setBerechnung(berechnungService.berechne(
                abrechnung, positionen, verbraeuche, zusaetze, akonto, personen, mieter));
        return detail;
    }

    private List<NkPositionDTO> zuPositionsDTOs(List<NkPosition> positionen, List<NkVerbrauch> verbraeuche) {
        Map<Long, List<NkVerbrauchDTO>> mengen = new HashMap<>();
        for (NkVerbrauch v : verbraeuche) {
            mengen.computeIfAbsent(v.getPositionId(), k -> new ArrayList<>())
                    .add(new NkVerbrauchDTO(v.getMieterId(), v.getMenge()));
        }

        List<NkPositionDTO> dtos = new ArrayList<>();
        for (NkPosition p : positionen) {
            NkPositionDTO dto = new NkPositionDTO();
            dto.setId(p.getId());
            dto.setArt(p.getArt());
            dto.setBezeichnung(p.getBezeichnung());
            dto.setReihenfolge(p.getReihenfolge());
            dto.setEinheit(p.getEinheit());
            dto.setTotalbetrag(p.getTotalbetrag());
            dto.setGesamtmenge(p.getGesamtmenge());
            dto.setBetragProEinheit(p.getBetragProEinheit());
            dto.setProzentsatz(p.getProzentsatz());
            dto.setVerbraeuche(mengen.getOrDefault(p.getId(), new ArrayList<>()));
            dtos.add(dto);
        }
        return dtos;
    }

    private List<NkPersonDTO> zuPersonDTOs(List<NkPerson> personen) {
        List<NkPersonDTO> dtos = new ArrayList<>();
        for (NkPerson person : personen) {
            NkPersonDTO dto = new NkPersonDTO(person.getMieterId(), person.getAnzahlPersonen());
            dto.setId(person.getId());
            dtos.add(dto);
        }
        return dtos;
    }

    private List<NkZusatzDTO> zuZusatzDTOs(List<NkZusatz> zusaetze) {
        List<NkZusatzDTO> dtos = new ArrayList<>();
        for (NkZusatz z : zusaetze) {
            NkZusatzDTO dto = new NkZusatzDTO();
            dto.setId(z.getId());
            dto.setMieterId(z.getMieterId());
            dto.setReihenfolge(z.getReihenfolge());
            dto.setBezeichnung(z.getBezeichnung());
            dto.setEinheit(z.getEinheit());
            dto.setMenge(z.getMenge());
            dto.setBetragProEinheit(z.getBetragProEinheit());
            dtos.add(dto);
        }
        return dtos;
    }

    private List<NkAkontoDTO> zuAkontoDTOs(List<NkAkonto> akonto) {
        List<NkAkontoDTO> dtos = new ArrayList<>();
        for (NkAkonto a : akonto) {
            NkAkontoDTO dto = new NkAkontoDTO();
            dto.setId(a.getId());
            dto.setMieterId(a.getMieterId());
            dto.setAnzahlMonate(a.getAnzahlMonate());
            dto.setBetragProMonat(a.getBetragProMonat());
            dto.setKorrektur(a.getKorrektur());
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Die abzurechnenden Mieter samt der Zahl ihrer Wohnungen.
     *
     * <p>Gezählt werden nur {@code CONSUMER}-Einheiten <b>mit gesetztem Kennzeichen</b>
     * {@code nebenkostenRelevant} — <b>dieselbe Regel wie beim Nenner</b> (FR-2). Eine Ladestation
     * gehört nicht dazu und würde den Anteil ihres Mieters verdoppeln, ein Messpunkt wie
     * Allgemeinstrom ebenso.
     *
     * <p>Zähler und Nenner müssen zwingend dieselbe Regel verwenden. Weichen sie ab, überschreitet
     * die Summe der Miettage den Nenner und das Speichern wird abgewiesen — oder, schlimmer, die
     * Abrechnung geht durch und verteilt Anteile an Messpunkte, die keine Wohnung sind.
     */
    private List<NkMieterBasisDTO> ladeMieter(NkAbrechnung abrechnung) {
        List<Mieter> mieter = mieterRepository.findByZeitraumOverlapping(
                abrechnung.getDatumVon(), abrechnung.getDatumBis());
        if (mieter.isEmpty()) {
            return List.of();
        }

        List<Long> mieterIds = mieter.stream().map(Mieter::getId).toList();
        List<MieterEinheit> zuordnungen = mieterEinheitRepository.findByMieterIdIn(mieterIds);

        Set<Long> einheitIds = new HashSet<>();
        for (MieterEinheit z : zuordnungen) {
            einheitIds.add(z.getEinheitId());
        }
        Set<Long> wohnungen = new HashSet<>();
        for (Einheit e : einheitRepository.findAllById(einheitIds)) {
            if (e.getTyp() == EinheitTyp.CONSUMER && e.isNebenkostenRelevant()) {
                wohnungen.add(e.getId());
            }
        }

        Map<Long, Integer> anzahlJeMieter = new HashMap<>();
        for (MieterEinheit z : zuordnungen) {
            if (wohnungen.contains(z.getEinheitId())) {
                anzahlJeMieter.merge(z.getMieterId(), 1, Integer::sum);
            }
        }

        List<NkMieterBasisDTO> basis = new ArrayList<>();
        for (Mieter m : mieter) {
            basis.add(new NkMieterBasisDTO(m.getId(), m.getName(), m.getMietbeginn(), m.getMietende(),
                    anzahlJeMieter.getOrDefault(m.getId(), 0), m.getAkontoProMonat()));
        }
        return basis;
    }

    /**
     * Zahl der Wohnungen des Mandanten; {@code null}, wenn es keine gibt (FR-2).
     *
     * <p>Gezählt werden nur {@code CONSUMER}-Einheiten mit gesetztem Kennzeichen
     * {@code nebenkostenRelevant}. Unter den Verbrauchern stehen auch Messpunkte, die keine
     * Wohnung sind (Allgemeinstrom, Eigenverbrauch der PV-Anlage); sie zählten sonst in den
     * Nenner und liessen bei jeder Umlage einen Anteil unverteilt, als stünde eine Wohnung leer.
     */
    private Integer vorschlagAnzahlWohnungen() {
        long anzahl = einheitRepository.countByTypAndNebenkostenRelevantTrue(EinheitTyp.CONSUMER);
        return anzahl > 0 ? (int) anzahl : null;
    }

    // ===================== Schreiben =====================

    /** IDs der Mieter, die im Zeitraum der Abrechnung liegen. */
    private Set<Long> mieterIds(List<NkMieterBasisDTO> mieter) {
        Set<Long> ids = new HashSet<>();
        for (NkMieterBasisDTO m : mieter) {
            ids.add(m.getMieterId());
        }
        return ids;
    }

    private void ersetzePositionen(NkAbrechnung abrechnung, List<NkPositionDTO> dtos, Long orgId,
                                  Set<Long> imZeitraum) {
        for (NkPosition alt : positionRepository.findByAbrechnungIdOrderByReihenfolge(abrechnung.getId())) {
            verbrauchRepository.deleteByPositionId(alt.getId());
        }
        positionRepository.deleteByAbrechnungId(abrechnung.getId());
        positionRepository.flush();

        int reihenfolge = 1;
        for (NkPositionDTO dto : dtos) {
            NkPosition position = new NkPosition();
            position.setOrgId(orgId);
            position.setAbrechnungId(abrechnung.getId());
            position.setArt(dto.getArt());
            position.setBezeichnung(dto.getBezeichnung());
            // Die Reihenfolge kommt aus der Listenposition, nicht aus dem Rumpf: Sie bestimmt das
            // Ergebnis der Zuschlagskaskade und muss zu dem passen, was die Maske zeigt.
            position.setReihenfolge(reihenfolge++);
            position.setEinheit(dto.getEinheit());
            position.setTotalbetrag(dto.getTotalbetrag());
            position.setGesamtmenge(dto.getGesamtmenge());
            position.setBetragProEinheit(dto.getBetragProEinheit());
            position.setProzentsatz(dto.getProzentsatz());
            NkPosition gespeichert = positionRepository.save(position);

            // VERBRAUCH speichert die Menge je Mieter, ANTEIL den Prozentsatz - beide in
            // derselben Zeile, unterschieden allein durch die Art der Position.
            if (dto.getArt() == NkPositionsart.VERBRAUCH || dto.getArt() == NkPositionsart.ANTEIL) {
                for (NkVerbrauchDTO v : dto.getVerbraeuche()) {
                    if (v.getMieterId() == null || v.getMenge() == null
                            || !imZeitraum.contains(v.getMieterId())) {
                        continue;
                    }
                    NkVerbrauch verbrauch = new NkVerbrauch(gespeichert.getId(), v.getMieterId(), v.getMenge());
                    verbrauch.setOrgId(orgId);
                    verbrauchRepository.save(verbrauch);
                }
            }
        }
    }

    private void ersetzeZusaetze(NkAbrechnung abrechnung, List<NkZusatzDTO> dtos, Long orgId,
                                 Set<Long> imZeitraum) {
        zusatzRepository.deleteByAbrechnungId(abrechnung.getId());
        zusatzRepository.flush();

        // Die Reihenfolge wird je Mieter neu vergeben - sie ist je Abrechnung UND Mieter eindeutig.
        Map<Long, Integer> naechste = new HashMap<>();
        for (NkZusatzDTO dto : dtos) {
            if (dto.getMieterId() != null && !imZeitraum.contains(dto.getMieterId())) {
                continue;
            }
            if (dto.getMieterId() == null) {
                throw new IllegalArgumentException("Zusatzposition ohne Mieter");
            }
            NkZusatz zusatz = new NkZusatz();
            zusatz.setOrgId(orgId);
            zusatz.setAbrechnungId(abrechnung.getId());
            zusatz.setMieterId(dto.getMieterId());
            zusatz.setReihenfolge(dto.getReihenfolge() != null
                    ? dto.getReihenfolge()
                    : naechste.merge(dto.getMieterId(), 1, Integer::sum));
            zusatz.setBezeichnung(dto.getBezeichnung());
            zusatz.setEinheit(dto.getEinheit());
            zusatz.setMenge(dto.getMenge());
            zusatz.setBetragProEinheit(dto.getBetragProEinheit());
            zusatzRepository.save(zusatz);
        }
    }

    private void ersetzeAkonto(NkAbrechnung abrechnung, List<NkAkontoDTO> dtos, Long orgId,
                               Set<Long> imZeitraum) {
        akontoRepository.deleteByAbrechnungId(abrechnung.getId());
        akontoRepository.flush();

        for (NkAkontoDTO dto : dtos) {
            if (dto.getMieterId() == null) {
                throw new IllegalArgumentException("Akonto ohne Mieter");
            }
            if (!imZeitraum.contains(dto.getMieterId())) {
                continue;
            }
            NkAkonto akonto = new NkAkonto();
            akonto.setOrgId(orgId);
            akonto.setAbrechnungId(abrechnung.getId());
            akonto.setMieterId(dto.getMieterId());
            akonto.setAnzahlMonate(dto.getAnzahlMonate() != null ? dto.getAnzahlMonate() : BigDecimal.ZERO);
            akonto.setBetragProMonat(dto.getBetragProMonat() != null ? dto.getBetragProMonat() : BigDecimal.ZERO);
            akonto.setKorrektur(dto.getKorrektur() != null ? dto.getKorrektur() : BigDecimal.ZERO);
            akontoRepository.save(akonto);
        }
    }

    /**
     * Ersetzt die Personenzahlen der Abrechnung — wie beim Akonto: erst löschen, dann neu schreiben.
     *
     * <p>Eine Zahl gleich der Vorgabe wird <b>nicht</b> gespeichert. Sonst entstünde für jeden
     * Mieter jeder Abrechnung eine Zeile, nur um „1" festzuhalten — und weil {@code nk_person}
     * mit {@code ON DELETE RESTRICT} auf den Mieter zeigt, wäre danach kein Mieter mehr löschbar,
     * der überhaupt in einer Abrechnung vorkommt.
     */
    private void ersetzePersonen(NkAbrechnung abrechnung, List<NkPersonDTO> dtos, Long orgId,
                                 Set<Long> imZeitraum) {
        personRepository.deleteByAbrechnungId(abrechnung.getId());
        personRepository.flush();

        for (NkPersonDTO dto : dtos) {
            if (dto.getMieterId() == null) {
                throw new IllegalArgumentException("Anzahl Personen ohne Mieter");
            }
            if (!imZeitraum.contains(dto.getMieterId())) {
                continue;
            }
            int personen = dto.getAnzahlPersonen() != null
                    ? dto.getAnzahlPersonen() : NkBerechnungService.PERSONEN_VORGABE;
            if (personen < 1) {
                throw new IllegalArgumentException("NK_FEHLER_ANZAHL_PERSONEN");
            }
            if (personen == NkBerechnungService.PERSONEN_VORGABE) {
                continue;
            }
            personRepository.save(new NkPerson(orgId, abrechnung.getId(), dto.getMieterId(), personen));
        }
    }

    // ===================== Prüfungen =====================

    /**
     * Wirft, wenn der Feature-Flag {@code NEBENKOSTENABRECHNUNG} für den Mandanten aus ist.
     *
     * <p>Ohne diese Prüfung wäre der Flag reine Kosmetik: Das Menü bliebe verborgen, die API aber
     * über jeden HTTP-Client erreichbar.
     */
    private void pruefeFeatureFlag() {
        Long orgId = organizationContextService.getCurrentOrgId();
        if (!featureFlagService.isEnabled(orgId, FeatureFlag.NEBENKOSTENABRECHNUNG)) {
            log.warn("Nebenkostenabrechnung rejected - feature disabled for org: {}", orgId);
            throw new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT");
        }
    }

    /**
     * Fehlt die Anzahl Personen, gilt die Anzahl Wohnungen (FR-2).
     *
     * <p>Nicht als Fehler behandelt: Der Vorschlag ist genau diese Zahl, und ein Aufrufer, der das
     * Feld nicht kennt, soll dieselbe Rechnung bekommen wie vor der Erweiterung — eine Umlage pro
     * Person verhält sich dann wie eine Umlage pro Wohnung.
     */
    private void ergaenzeAnzahlPersonen(NkAbrechnung abrechnung) {
        if (abrechnung.getAnzahlPersonen() == null) {
            abrechnung.setAnzahlPersonen(abrechnung.getAnzahlWohnungen());
        }
    }

    private void pruefeKopf(NkAbrechnung abrechnung) {
        if (abrechnung.getDatumVon() == null || abrechnung.getDatumBis() == null) {
            throw new IllegalArgumentException("NK_FEHLER_ZEITRAUM_PFLICHT");
        }
        if (abrechnung.getDatumVon().isAfter(abrechnung.getDatumBis())) {
            throw new IllegalArgumentException("NK_FEHLER_ZEITRAUM");
        }
        if (abrechnung.getAnzahlWohnungen() == null || abrechnung.getAnzahlWohnungen() < 1) {
            throw new IllegalArgumentException("NK_FEHLER_ANZAHL_WOHNUNGEN");
        }
        if (abrechnung.getAnzahlPersonen() == null || abrechnung.getAnzahlPersonen() < 1) {
            throw new IllegalArgumentException("NK_FEHLER_ANZAHL_PERSONEN");
        }
    }

    private void pruefeNichtAbgerechnet(NkAbrechnung abrechnung) {
        if (abrechnung.isAbgerechnet()) {
            throw new IllegalArgumentException("NK_FEHLER_ABGERECHNET");
        }
    }

    /**
     * Prüft {@code Σ Tage(i) <= Nenner} (FR-2).
     *
     * <p>Ist die Anzahl Wohnungen zu klein erfasst, überstiege die Summe der verteilten Beträge den
     * Totalbetrag — die Mieter zahlten gemeinsam mehr als angefallen ist. Der umgekehrte Fall ist
     * zulässig und genau der Leerstand.
     */
    private void pruefeNenner(NkAbrechnung abrechnung, List<NkMieterBasisDTO> mieter) {
        long tageImZeitraum = berechnungService.tageImZeitraum(
                abrechnung.getDatumVon(), abrechnung.getDatumBis());
        long nenner = (long) abrechnung.getAnzahlWohnungen() * tageImZeitraum;

        long summeTage = 0;
        for (NkMieterBasisDTO m : mieter) {
            summeTage += berechnungService.miettageImZeitraum(
                    m, abrechnung.getDatumVon(), abrechnung.getDatumBis()) * m.getAnzahlWohnungen();
        }

        if (summeTage > nenner) {
            // Als Klartext und nicht als Uebersetzungs-Key: Die Meldung muss beide Zahlen nennen,
            // und ein Key mit angehaengten Werten liesse sich im Frontend nicht mehr aufloesen.
            throw new IllegalArgumentException(
                    "Die Anzahl Wohnungen ist zu klein erfasst: Die Mieter belegen " + summeTage
                            + " Miettage, der Nenner erlaubt aber nur " + nenner + ".");
        }
    }

    /**
     * Prüft {@code Σ (Tage(i) x Personen(i)) <= Anzahl Personen x Tage} (FR-2).
     *
     * <p>Dieselbe Regel wie bei den Wohnungen, nur mit Köpfen gewichtet: Ist die Anzahl Personen zu
     * klein erfasst, überstiege die Summe der verteilten Beträge den Totalbetrag.
     *
     * <p><b>Nur wenn es überhaupt eine Position dieser Art gibt.</b> Ohne diese Einschränkung
     * blockierte eine erfasste Personenzahl das Speichern einer Abrechnung, die gar keine
     * Personenumlage enthält — die Zahl hätte dort keine Wirkung, wohl aber die Fehlermeldung.
     */
    private void pruefeNennerPerson(NkAbrechnung abrechnung, List<NkMieterBasisDTO> mieter,
                                    List<NkPositionDTO> positionen, List<NkPersonDTO> personen) {
        boolean hatPersonenumlage = positionen.stream()
                .anyMatch(p -> p.getArt() == NkPositionsart.UMLAGE_PERSON);
        if (!hatPersonenumlage) {
            return;
        }

        Map<Long, Integer> personenJeMieter = new HashMap<>();
        for (NkPersonDTO dto : personen) {
            if (dto.getMieterId() != null && dto.getAnzahlPersonen() != null) {
                personenJeMieter.put(dto.getMieterId(), dto.getAnzahlPersonen());
            }
        }

        long tageImZeitraum = berechnungService.tageImZeitraum(
                abrechnung.getDatumVon(), abrechnung.getDatumBis());
        long nenner = (long) abrechnung.getAnzahlPersonen() * tageImZeitraum;

        long summe = 0;
        for (NkMieterBasisDTO m : mieter) {
            long tage = berechnungService.miettageImZeitraum(
                    m, abrechnung.getDatumVon(), abrechnung.getDatumBis()) * m.getAnzahlWohnungen();
            summe += tage * personenJeMieter.getOrDefault(
                    m.getMieterId(), NkBerechnungService.PERSONEN_VORGABE);
        }

        if (summe > nenner) {
            // Klartext und kein Uebersetzungs-Key: Die Meldung muss beide Zahlen nennen.
            throw new IllegalArgumentException(
                    "Die Anzahl Personen ist zu klein erfasst: Die Mieter belegen " + summe
                            + " Personentage, der Nenner erlaubt aber nur " + nenner + ".");
        }
    }

    /**
     * Art-abhängige Pflichtfelder (FR-2). Dieselbe Regel steht als CHECK-Constraint in der
     * Datenbank; hier steht sie, damit statt eines Constraint-Fehlers eine lesbare Meldung kommt.
     */
    private void pruefePositionen(List<NkPositionDTO> positionen) {
        for (NkPositionDTO p : positionen) {
            if (p.getArt() == null) {
                throw new IllegalArgumentException("NK_FEHLER_POSITION_ART");
            }
            if (p.getBezeichnung() == null || p.getBezeichnung().isBlank()) {
                throw new IllegalArgumentException("NK_FEHLER_POSITION_BEZEICHNUNG");
            }
            switch (p.getArt()) {
                // Beide Umlagen brauchen dieselben Felder - nur der Verteilschluessel
                // unterscheidet sie. Wichtig, dass sie hier zusammen stehen: Der `default`-Zweig
                // wirft NK_FEHLER_POSITION_ART, eine fehlende Art faellt also erst beim Speichern
                // auf und nicht beim Rechnen.
                case UMLAGE, UMLAGE_PERSON -> {
                    if (p.getTotalbetrag() == null || p.getEinheit() == null) {
                        throw new IllegalArgumentException("NK_FEHLER_POSITION_UMLAGE");
                    }
                    p.setBetragProEinheit(null);
                    p.setProzentsatz(null);
                }
                case VERBRAUCH -> {
                    if (p.getBetragProEinheit() == null || p.getEinheit() == null) {
                        throw new IllegalArgumentException("NK_FEHLER_POSITION_VERBRAUCH");
                    }
                    p.setTotalbetrag(null);
                    p.setGesamtmenge(null);
                    p.setProzentsatz(null);
                }
                case ANTEIL -> {
                    if (p.getTotalbetrag() == null) {
                        throw new IllegalArgumentException("NK_FEHLER_POSITION_ANTEIL");
                    }
                    // Der Prozentsatz steht je Mieter, die Einheit ist immer Prozent - beides
                    // gehoert nicht an die Position und wird geleert.
                    p.setGesamtmenge(null);
                    p.setBetragProEinheit(null);
                    p.setProzentsatz(null);
                    p.setEinheit(null);
                }
                case ZUSCHLAG -> {
                    if (p.getProzentsatz() == null) {
                        throw new IllegalArgumentException("NK_FEHLER_POSITION_ZUSCHLAG");
                    }
                    if (p.getProzentsatz().signum() < 0
                            || p.getProzentsatz().compareTo(BigDecimal.valueOf(100)) > 0) {
                        throw new IllegalArgumentException("NK_FEHLER_PROZENTSATZ");
                    }
                    // Nicht zutreffende Felder werden geleert statt abgewiesen: Wer die Art einer
                    // Zeile wechselt, schickt sonst Reste der alten Art mit und liefe in den
                    // CHECK-Constraint, ohne dass die Maske etwas Falsches zeigte.
                    p.setTotalbetrag(null);
                    p.setGesamtmenge(null);
                    p.setBetragProEinheit(null);
                    p.setEinheit(null);
                }
                default -> throw new IllegalArgumentException("NK_FEHLER_POSITION_ART");
            }
        }
    }
}
