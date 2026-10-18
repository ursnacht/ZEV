package ch.nacht.service;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MieterEinheitRepository;
import ch.nacht.repository.MieterRepository;
import ch.nacht.repository.OrganisationRepository;
import ch.nacht.repository.TarifRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Integrationstest: Eine mitgeschickte {@code id} bzw. {@code orgId} darf beim Speichern keinen
 * Datensatz eines fremden Mandanten treffen (OWASP A01, Security-Check vom 18.10.2026).
 *
 * <p><b>Warum ein Integrationstest und kein Unit-Test.</b> Die Luecke liegt im Zusammenspiel mit
 * JPA: {@code save} einer Entity mit gesetzter {@code id} wird zu {@code merge}, und {@code merge}
 * laedt den bestehenden Datensatz ueber den Primaerschluessel – dort greift der {@code @Filter}
 * nicht (vgl. {@code NkAbrechnungRepositoryIT#findByIdUmgehtDenMandantenfilter_bekannteLuecke}).
 * Mit einem gemockten Repository ist das unsichtbar.
 *
 * <p>Nachgelesen wird deshalb <b>ohne</b> Filter, direkt ueber den Primaerschluessel.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({EinheitService.class, TarifService.class, MieterService.class, HibernateFilterService.class})
class FremdeIdBeimSpeichernIT extends AbstractIntegrationTest {

    @Autowired
    private EinheitService einheitService;

    @Autowired
    private TarifService tarifService;

    @Autowired
    private MieterService mieterService;

    @Autowired
    private EinheitRepository einheitRepository;

    @Autowired
    private TarifRepository tarifRepository;

    @Autowired
    private MieterRepository mieterRepository;

    @Autowired
    private MieterEinheitRepository mieterEinheitRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    private Long TEST_ORG_ID;

    private Long FREMD_ORG_ID;

    @BeforeEach
    void setUp() {
        TEST_ORG_ID = organisation("Test Organisation", "c2c9ba74-de18-4491-9489-8185629edd93");
        FREMD_ORG_ID = organisation("Fremde Organisation", "9e7a3c15-8d24-4f60-b193-5a8c2e0d7f36");
        when(organizationContextService.getCurrentOrgId()).thenReturn(TEST_ORG_ID);
    }

    // ==================== Einheit ====================

    @Test
    void createEinheit_MitFremderId_LegtNeueEinheitAnUndLaesstFremdeUnveraendert() {
        Einheit fremde = einheitRepository.saveAndFlush(einheit(FREMD_ORG_ID, "Fremd"));
        entityManager.clear();

        Einheit angriff = new Einheit("Gekapert", EinheitTyp.CONSUMER);
        angriff.setId(fremde.getId());
        Einheit angelegt = einheitService.createEinheit(angriff);
        neuLaden();

        assertThat(angelegt.getId()).isNotEqualTo(fremde.getId());
        Einheit nachher = ungefiltert(Einheit.class, fremde.getId());
        assertThat(nachher.getOrgId()).isEqualTo(FREMD_ORG_ID);
        assertThat(nachher.getName()).isEqualTo("Fremd");
    }

    // ==================== Tarif ====================

    @Test
    void saveTarif_MitFremderId_WirdAbgewiesenUndFremderTarifBleibtUnveraendert() {
        Tarif fremder = tarifRepository.saveAndFlush(tarif(FREMD_ORG_ID, "Fremd"));
        entityManager.clear();

        Tarif angriff = tarif(FREMD_ORG_ID, "Gekapert");
        angriff.setId(fremder.getId());

        assertThatThrownBy(() -> tarifService.saveTarif(angriff))
                .isInstanceOf(IllegalArgumentException.class);
        neuLaden();

        Tarif nachher = ungefiltert(Tarif.class, fremder.getId());
        assertThat(nachher.getOrgId()).isEqualTo(FREMD_ORG_ID);
        assertThat(nachher.getBezeichnung()).isEqualTo("Fremd");
    }

    @Test
    void saveTarif_EigenerTarifMitFremderOrgId_BleibtImEigenenMandanten() {
        Tarif eigener = tarifRepository.saveAndFlush(tarif(TEST_ORG_ID, "Eigen"));
        entityManager.clear();

        Tarif aenderung = tarif(FREMD_ORG_ID, "Eigen geaendert");
        aenderung.setId(eigener.getId());
        tarifService.saveTarif(aenderung);
        neuLaden();

        Tarif nachher = ungefiltert(Tarif.class, eigener.getId());
        assertThat(nachher.getOrgId()).isEqualTo(TEST_ORG_ID);
        assertThat(nachher.getBezeichnung()).isEqualTo("Eigen geaendert");
    }

    // ==================== Mieter ====================

    @Test
    void saveMieter_MitFremderId_WirdAbgewiesenUndFremderMieterBleibtUnveraendert() {
        Long fremdeEinheitId = einheitRepository.saveAndFlush(einheit(FREMD_ORG_ID, "Fremd")).getId();
        Mieter fremder = mieterRepository.saveAndFlush(mieter(FREMD_ORG_ID, "Fremd", fremdeEinheitId));
        Long eigeneEinheitId = einheitRepository.saveAndFlush(einheit(TEST_ORG_ID, "Eigen")).getId();
        entityManager.clear();

        Mieter angriff = mieter(FREMD_ORG_ID, "Gekapert", eigeneEinheitId);
        angriff.setId(fremder.getId());

        assertThatThrownBy(() -> mieterService.saveMieter(angriff))
                .isInstanceOf(IllegalArgumentException.class);
        neuLaden();

        Mieter nachher = ungefiltert(Mieter.class, fremder.getId());
        assertThat(nachher.getOrgId()).isEqualTo(FREMD_ORG_ID);
        assertThat(nachher.getName()).isEqualTo("Fremd");
    }

    @Test
    void saveMieter_EigenerMieterMitFremderOrgId_BleibtImEigenenMandanten() {
        Long eigeneEinheitId = einheitRepository.saveAndFlush(einheit(TEST_ORG_ID, "Eigen")).getId();
        Mieter eigener = mieterRepository.saveAndFlush(mieter(TEST_ORG_ID, "Eigen", eigeneEinheitId));
        entityManager.clear();

        Mieter aenderung = mieter(FREMD_ORG_ID, "Eigen geaendert", eigeneEinheitId);
        aenderung.setId(eigener.getId());
        mieterService.saveMieter(aenderung);
        neuLaden();

        Mieter nachher = ungefiltert(Mieter.class, eigener.getId());
        assertThat(nachher.getOrgId()).isEqualTo(TEST_ORG_ID);
        assertThat(nachher.getName()).isEqualTo("Eigen geaendert");
    }

    @Test
    void saveMieter_MitFremderEinheit_WirdAbgewiesen() {
        Long fremdeEinheitId = einheitRepository.saveAndFlush(einheit(FREMD_ORG_ID, "Fremd")).getId();
        entityManager.clear();

        assertThatThrownBy(() -> mieterService.saveMieter(mieter(TEST_ORG_ID, "Neu", fremdeEinheitId)))
                .isInstanceOf(IllegalArgumentException.class);
        neuLaden();

        assertThat(mieterEinheitRepository.findAll()).isEmpty();
    }

    // ==================== Hilfsmethoden ====================

    /** Schreibt ausstehende Aenderungen in die DB und leert den Persistenzkontext. */
    private void neuLaden() {
        entityManager.flush();
        entityManager.clear();
    }

    /** Laedt ueber den Primaerschluessel und ohne Mandantenfilter – sieht also auch fremde Daten. */
    private <T> T ungefiltert(Class<T> typ, Long id) {
        entityManager.unwrap(Session.class).disableFilter("orgFilter");
        return entityManager.find(typ, id);
    }

    private Long organisation(String name, String keycloakOrgId) {
        Organisation org = new Organisation();
        org.setKeycloakOrgId(UUID.fromString(keycloakOrgId));
        org.setName(name);
        org.setErstelltAm(LocalDateTime.now());
        return organisationRepository.save(org).getId();
    }

    private Einheit einheit(Long orgId, String name) {
        Einheit einheit = new Einheit(name, EinheitTyp.CONSUMER);
        einheit.setOrgId(orgId);
        return einheit;
    }

    private Tarif tarif(Long orgId, String bezeichnung) {
        Tarif tarif = new Tarif(bezeichnung, TarifTyp.ZEV, new BigDecimal("0.20000"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        tarif.setOrgId(orgId);
        return tarif;
    }

    private Mieter mieter(Long orgId, String name, Long einheitId) {
        Mieter mieter = new Mieter(name, LocalDate.of(2024, 1, 1), einheitId);
        mieter.setOrgId(orgId);
        mieter.setStrasse("Teststrasse 1");
        mieter.setPlz("3000");
        mieter.setOrt("Bern");
        return mieter;
    }
}
