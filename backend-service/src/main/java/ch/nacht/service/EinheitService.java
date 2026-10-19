package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import ch.nacht.repository.MieterEinheitRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

@Service
public class EinheitService {

    /**
     * Typen, von denen es je Mandant höchstens <b>eine</b> Einheit geben darf — samt der Meldung,
     * die der Benutzer dann sieht.
     *
     * <p>Die Bilanz-Typen messen den Netzanschluss, der Speicher die eine Batterie der Anlage. In
     * beiden Fällen wäre eine zweite Einheit keine zusätzliche Messstelle, sondern eine
     * Doppelzählung. Eigene Meldung je Fall: „Bilanz-Typ existiert bereits" hülfe bei einem
     * Speicher nicht weiter.
     */
    private static final Map<EinheitTyp, String> EINMALIG_JE_MANDANT = Map.of(
            EinheitTyp.BEZUG, "EINHEIT_BILANZ_TYP_EXISTIERT",
            EinheitTyp.RUECKLIEFERUNG, "EINHEIT_BILANZ_TYP_EXISTIERT",
            EinheitTyp.SPEICHER, "EINHEIT_SPEICHER_EXISTIERT");

    private final EinheitRepository einheitRepository;
    private final MieterEinheitRepository mieterEinheitRepository;
    private final MesswerteRepository messwerteRepository;
    private final ZaehlerRohdatenRepository zaehlerRohdatenRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public EinheitService(EinheitRepository einheitRepository,
                          MieterEinheitRepository mieterEinheitRepository,
                          MesswerteRepository messwerteRepository,
                          ZaehlerRohdatenRepository zaehlerRohdatenRepository,
                          OrganizationContextService organizationContextService,
                          HibernateFilterService hibernateFilterService) {
        this.einheitRepository = einheitRepository;
        this.mieterEinheitRepository = mieterEinheitRepository;
        this.messwerteRepository = messwerteRepository;
        this.zaehlerRohdatenRepository = zaehlerRohdatenRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    @Transactional(readOnly = true)
    public List<Einheit> getAllEinheiten() {
        hibernateFilterService.enableOrgFilter();
        return einheitRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Einheit> getEinheitById(Long id) {
        hibernateFilterService.enableOrgFilter();
        return einheitRepository.findFirstById(id);
    }

    @Transactional
    public Einheit createEinheit(Einheit einheit) {
        hibernateFilterService.enableOrgFilter();
        String fehlerKey = EINMALIG_JE_MANDANT.get(einheit.getTyp());
        if (fehlerKey != null && einheitRepository.existsByTyp(einheit.getTyp())) {
            throw new IllegalStateException(fehlerKey);
        }
        pruefeLadestationMesspunkt(einheit, -1L);
        // Eine mitgeschickte ID machte aus dem save ein merge, das den Datensatz ungefiltert
        // ueber den Primaerschluessel laedt - auch den eines fremden Mandanten (FremdeIdBeimSpeichernIT).
        einheit.setId(null);
        einheit.setOrgId(organizationContextService.getCurrentOrgId());
        return einheitRepository.save(einheit);
    }

    @Transactional
    public Optional<Einheit> updateEinheit(Long id, Einheit einheit) {
        hibernateFilterService.enableOrgFilter();
        Optional<Einheit> existingEinheit = einheitRepository.findFirstById(id);
        if (existingEinheit.isEmpty()) {
            return Optional.empty();
        }
        String fehlerKey = EINMALIG_JE_MANDANT.get(einheit.getTyp());
        if (fehlerKey != null && einheitRepository.existsByTypAndIdNot(einheit.getTyp(), id)) {
            throw new IllegalStateException(fehlerKey);
        }
        pruefeLadestationMesspunkt(einheit, id);
        einheit.setId(id);
        einheit.setOrgId(existingEinheit.get().getOrgId());
        return Optional.of(einheitRepository.save(einheit));
    }

    /**
     * Normalisiert einen leeren Messpunkt auf {@code null} und prüft bei Ladestationen die
     * Eindeutigkeit der RFID. Ohne Eindeutigkeit könnte der spätere Import aus dem Lademanagement
     * eine Menge nicht eindeutig einer Einheit zuordnen. Leere Eingaben dürfen nicht miteinander
     * kollidieren, und die Bilanz-Typen teilen sich einen Messpunkt bewusst — die Prüfung greift
     * deshalb nur für {@code LADESTATION}.
     *
     * @param einheit Zu prüfende Einheit
     * @param excludeId Eigene ID beim Update, {@code -1} beim Anlegen
     * @throws IllegalStateException wenn die RFID bereits einer anderen Ladestation gehört
     */
    private void pruefeLadestationMesspunkt(Einheit einheit, Long excludeId) {
        if (einheit.getMesspunkt() != null && einheit.getMesspunkt().isBlank()) {
            einheit.setMesspunkt(null);
        }
        if (einheit.getTyp() == EinheitTyp.LADESTATION
                && einheit.getMesspunkt() != null
                && einheitRepository.existsLadestationWithMesspunkt(einheit.getMesspunkt(), excludeId)) {
            throw new IllegalStateException("EINHEIT_MESSPUNKT_EXISTIERT");
        }
    }

    @Transactional
    public boolean deleteEinheit(Long id) {
        hibernateFilterService.enableOrgFilter();
        if (!einheitRepository.existsById(id)) {
            return false;
        }
        // Ohne diese Pruefung entstuende ein Mieter ohne Einheit - die Regel "mindestens eine
        // Zuordnung" griffe nur im Mieter-Formular. Der FK weist das Loeschen zwar ohnehin ab
        // (ON DELETE RESTRICT), aber als DataIntegrityViolationException ohne verwertbare
        // Meldung (Specs/Ladestationen.md FR-2).
        long zugeordneteMieter = mieterEinheitRepository.countByEinheitId(id);
        if (zugeordneteMieter > 0) {
            throw new IllegalStateException(
                    "Einheit kann nicht gelöscht werden: " + zugeordneteMieter + " Mieter zugeordnet");
        }
        // messwerte und zaehler_rohdaten verweisen ohne ON DELETE auf die Einheit: Der FK wiese das
        // Loeschen ab, aber nur als 409 mit allgemeinem Datenbanktext (Specs/Einheitenverwaltung.md, FR-5).
        if (messwerteRepository.existsByEinheitId(id) || zaehlerRohdatenRepository.existsByEinheitId(id)) {
            throw new IllegalStateException("EINHEIT_HAT_MESSWERTE");
        }
        einheitRepository.deleteById(id);
        return true;
    }
}
