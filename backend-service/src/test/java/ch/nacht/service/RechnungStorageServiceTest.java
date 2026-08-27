package ch.nacht.service;

import ch.nacht.service.RechnungStorageService.Rechnungsart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Tests der fluechtigen PDF-Ablage
 * ({@code Specs/Nebenkosten/RechnungenGenerieren.md}, FR-5).
 *
 * <p>Diese Klasse hatte bisher keine Tests, obwohl drei Zusicherungen an ihr haengen: die Trennung
 * nach Mandant, die Trennung nach {@link Rechnungsart} und das Aufraeumen, das <b>nur die eigene
 * Art</b> treffen darf. Vorher loeschte {@code clearAll()} alle Ablagen des Mandanten — mit zwei
 * Ausloeseorten haette ein ZEV-Lauf die offenen NK-Downloads stumm auf {@code 404} gesetzt.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RechnungStorageServiceTest {

    private static final Long ORG_ID = 42L;
    private static final Long ANDERE_ORG_ID = 99L;

    private static final byte[] PDF_A = "%PDF-A".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_B = "%PDF-B".getBytes(StandardCharsets.US_ASCII);

    @Mock
    private OrganizationContextService organizationContextService;

    @InjectMocks
    private RechnungStorageService service;

    @BeforeEach
    void setUp() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
    }

    // ==================== Ablegen und Holen ====================

    @Test
    void store_ThenGet_ReturnsPdf() {
        service.store(Rechnungsart.NK, "12_45", PDF_A, "Nebenkosten_2026_Muster.pdf");

        Optional<byte[]> result = service.get(Rechnungsart.NK, "12_45");

        assertTrue(result.isPresent());
        assertEquals("%PDF-A", new String(result.get(), StandardCharsets.US_ASCII));
    }

    @Test
    void get_UnbekannterSchluessel_ReturnsEmpty() {
        assertTrue(service.get(Rechnungsart.NK, "gibtsnicht").isEmpty());
        assertFalse(service.exists(Rechnungsart.NK, "gibtsnicht"));
    }

    @Test
    void exists_NachDemAblegen_True() {
        service.store(Rechnungsart.ZEV, "Wohnung_1_10", PDF_A, "Wohnung_1_10.pdf");

        assertTrue(service.exists(Rechnungsart.ZEV, "Wohnung_1_10"));
    }

    // ==================== Trennung nach Rechnungsart ====================

    /**
     * Derselbe Schluessel in beiden Arten sind <b>zwei</b> Ablagen.
     *
     * <p>Das ist der Fall, den ein Praefix im Schluessel nicht getragen haette: Eine Einheit
     * „nk 12" mit {@code mieterId 45} wird von {@code sanitizeKey} zu {@code nk_12_45} — genau der
     * Form, die eine NK-Ablage mit Praefix gehabt haette. Die Art ist deshalb ein eigenes Segment
     * des internen Schluessels und nicht Teil des uebergebenen.
     */
    @Test
    void store_GleicherSchluesselVerschiedeneArt_BleibtGetrennt() {
        service.store(Rechnungsart.ZEV, "nk_12_45", PDF_A, "zev.pdf");
        service.store(Rechnungsart.NK, "nk_12_45", PDF_B, "nk.pdf");

        assertEquals("%PDF-A",
                new String(service.get(Rechnungsart.ZEV, "nk_12_45").orElseThrow(), StandardCharsets.US_ASCII));
        assertEquals("%PDF-B",
                new String(service.get(Rechnungsart.NK, "nk_12_45").orElseThrow(), StandardCharsets.US_ASCII));
    }

    @Test
    void get_FalscheArt_ReturnsEmpty() {
        service.store(Rechnungsart.NK, "12_45", PDF_A, "nk.pdf");

        assertTrue(service.get(Rechnungsart.ZEV, "12_45").isEmpty());
    }

    // ==================== Aufraeumen je Art ====================

    @Test
    void clearArt_ZEV_LaesstNKStehen() {
        service.store(Rechnungsart.ZEV, "Wohnung_1_10", PDF_A, "zev.pdf");
        service.store(Rechnungsart.NK, "12_45", PDF_B, "nk.pdf");

        service.clearArt(Rechnungsart.ZEV);

        assertFalse(service.exists(Rechnungsart.ZEV, "Wohnung_1_10"));
        assertTrue(service.exists(Rechnungsart.NK, "12_45"),
                "Ein ZEV-Lauf darf die offenen NK-Downloads nicht mitnehmen");
    }

    @Test
    void clearArt_NK_LaesstZEVStehen() {
        service.store(Rechnungsart.ZEV, "Wohnung_1_10", PDF_A, "zev.pdf");
        service.store(Rechnungsart.NK, "12_45", PDF_B, "nk.pdf");

        service.clearArt(Rechnungsart.NK);

        assertTrue(service.exists(Rechnungsart.ZEV, "Wohnung_1_10"));
        assertFalse(service.exists(Rechnungsart.NK, "12_45"));
    }

    @Test
    void clearArt_LeereAblage_WirftNicht() {
        service.clearArt(Rechnungsart.NK);

        assertFalse(service.exists(Rechnungsart.NK, "12_45"));
    }

    // ==================== Trennung nach Mandant ====================

    /**
     * Ein fremder Mandant erreicht die Ablage nicht, auch nicht mit geratenem Schluessel — der
     * interne Schluessel traegt die {@code org_id} voran.
     */
    @Test
    void get_AndererMandant_ReturnsEmpty() {
        service.store(Rechnungsart.NK, "12_45", PDF_A, "nk.pdf");

        when(organizationContextService.getCurrentOrgId()).thenReturn(ANDERE_ORG_ID);

        assertTrue(service.get(Rechnungsart.NK, "12_45").isEmpty());
    }

    @Test
    void clearArt_AndererMandant_LaesstFremdeAblageStehen() {
        service.store(Rechnungsart.NK, "12_45", PDF_A, "nk.pdf");

        when(organizationContextService.getCurrentOrgId()).thenReturn(ANDERE_ORG_ID);
        service.clearArt(Rechnungsart.NK);

        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        assertTrue(service.exists(Rechnungsart.NK, "12_45"));
    }

    // ==================== Dateiname ====================

    /**
     * Der gespeicherte Name kommt zurueck, nicht der aus dem Schluessel abgeleitete.
     *
     * <p>Fuer NK ist das der Kern: Der Schluessel besteht aus zwei IDs, ein daraus abgeleiteter
     * Name waere {@code 12_45.pdf} — beim Mieter im Download-Ordner nicht wiederzuerkennen.
     */
    @Test
    void getFilename_MitArt_LiefertGespeichertenNamen() {
        service.store(Rechnungsart.NK, "12_45", PDF_A, "Nebenkosten_2026_Max_Muster.pdf");

        assertEquals("Nebenkosten_2026_Max_Muster.pdf", service.getFilename(Rechnungsart.NK, "12_45"));
        assertNotEquals("12_45.pdf", service.getFilename(Rechnungsart.NK, "12_45"));
    }

    @Test
    void getFilename_UnbekannterSchluessel_FaelltAufAbleitungZurueck() {
        assertEquals("12_45.pdf", service.getFilename(Rechnungsart.NK, "12_45"));
    }

    @Test
    void getFilename_OhneArt_LeitetAusSchluesselAb() {
        assertEquals("Wohnung_1.pdf", service.getFilename("Wohnung 1"));
    }

    // ==================== sanitizeKey ====================

    @Test
    void sanitizeKey_ErsetztAlleLeerzeichen() {
        // Belegt die Kollisionsgefahr, gegen die die Rechnungsart schuetzt:
        // "nk 12 45" wird zu genau dem Schluessel, den ein NK-Praefix ergeben haette.
        assertEquals("nk_12_45", service.sanitizeKey("nk 12 45"));
    }

    @Test
    void sanitizeKey_EntferntSonderzeichenAberBehaeltUmlaute() {
        // Sonderzeichen fallen weg, sie werden nicht ersetzt - nur Leerzeichen werden zu "_".
        assertEquals("GrosseWohnung-3", service.sanitizeKey("Grosse/Wohnung-3!"));
        assertEquals("Küche", service.sanitizeKey("Küche"));
    }

    @Test
    void sanitizeKey_Null_LeererString() {
        assertEquals("", service.sanitizeKey(null));
    }

    // ==================== Ablauf ====================

    @Test
    void cleanupExpired_FrischeAblage_BleibtErhalten() {
        service.store(Rechnungsart.NK, "12_45", PDF_A, "nk.pdf");

        service.cleanupExpired();

        assertTrue(service.exists(Rechnungsart.NK, "12_45"));
    }
}
