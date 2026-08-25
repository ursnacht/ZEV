package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MieterEinheitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class EinheitService {

    /** Bilanz-Typen (Netzanschluss): je Mandant höchstens eine Einheit pro Typ. */
    private static final Set<EinheitTyp> BILANZ_TYPEN = Set.of(EinheitTyp.BEZUG, EinheitTyp.RUECKLIEFERUNG);

    private final EinheitRepository einheitRepository;
    private final MieterEinheitRepository mieterEinheitRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public EinheitService(EinheitRepository einheitRepository,
                          MieterEinheitRepository mieterEinheitRepository,
                          OrganizationContextService organizationContextService,
                          HibernateFilterService hibernateFilterService) {
        this.einheitRepository = einheitRepository;
        this.mieterEinheitRepository = mieterEinheitRepository;
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
        if (BILANZ_TYPEN.contains(einheit.getTyp()) && einheitRepository.existsByTyp(einheit.getTyp())) {
            throw new IllegalStateException("EINHEIT_BILANZ_TYP_EXISTIERT");
        }
        pruefeLadestationMesspunkt(einheit, -1L);
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
        if (BILANZ_TYPEN.contains(einheit.getTyp()) && einheitRepository.existsByTypAndIdNot(einheit.getTyp(), id)) {
            throw new IllegalStateException("EINHEIT_BILANZ_TYP_EXISTIERT");
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
        einheitRepository.deleteById(id);
        return true;
    }
}
