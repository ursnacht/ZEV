package ch.nacht.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests der Feldvalidierung von {@link Tarifposition} (Spec Ladestromtarif.md, NFR-2):
 * {@code menge >= 0}, {@code quartal} zwischen 1 und 4, plausibles {@code jahr} sowie die
 * Längenbegrenzungen von Quell-Referenz und Bemerkung.
 */
public class TarifpositionTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private Tarifposition gueltigePosition() {
        Mieter mieter = new Mieter("Max Muster", LocalDate.of(2026, 1, 1), 1L);
        mieter.setId(1L);

        Tarif tarif = new Tarif("Ladestrom", TarifTyp.LADESTROM, new BigDecimal("0.35000"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        tarif.setId(10L);

        return new Tarifposition(mieter, tarif, 2026, 3, new BigDecimal("120.500"));
    }

    private Set<ConstraintViolation<Tarifposition>> validate(Tarifposition position) {
        return validator.validate(position);
    }

    @Test
    void validPosition_HasNoViolations() {
        assertTrue(validate(gueltigePosition()).isEmpty());
    }

    @Test
    void newPosition_DefaultsToErfassungsartManuell() {
        assertEquals(Erfassungsart.MANUELL, gueltigePosition().getErfassungsart());
    }

    // ==================== menge ====================

    @Test
    void mengeZero_IsValid() {
        Tarifposition position = gueltigePosition();
        position.setMenge(BigDecimal.ZERO);

        assertTrue(validate(position).isEmpty());
    }

    @Test
    void mengeNegative_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setMenge(new BigDecimal("-0.001"));

        Set<ConstraintViolation<Tarifposition>> violations = validate(position);

        assertEquals(1, violations.size());
        assertEquals("menge", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void mengeNull_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setMenge(null);

        assertFalse(validate(position).isEmpty());
    }

    // ==================== quartal ====================

    @Test
    void quartalBoundaries_AreValid() {
        for (int quartal = 1; quartal <= 4; quartal++) {
            Tarifposition position = gueltigePosition();
            position.setQuartal(quartal);
            assertTrue(validate(position).isEmpty(), "Quartal " + quartal + " muss zulässig sein");
        }
    }

    @Test
    void quartalZero_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setQuartal(0);

        assertFalse(validate(position).isEmpty());
    }

    @Test
    void quartalFive_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setQuartal(5);

        Set<ConstraintViolation<Tarifposition>> violations = validate(position);

        assertEquals(1, violations.size());
        assertEquals("quartal", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void quartalNull_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setQuartal(null);

        assertFalse(validate(position).isEmpty());
    }

    // ==================== jahr ====================

    @Test
    void jahrBoundaries_AreValid() {
        Tarifposition unten = gueltigePosition();
        unten.setJahr(2000);
        assertTrue(validate(unten).isEmpty());

        Tarifposition oben = gueltigePosition();
        oben.setJahr(2100);
        assertTrue(validate(oben).isEmpty());
    }

    @Test
    void jahrBefore2000_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setJahr(1999);

        assertFalse(validate(position).isEmpty());
    }

    @Test
    void jahrAfter2100_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setJahr(2101);

        assertFalse(validate(position).isEmpty());
    }

    // ==================== Referenzen ====================

    @Test
    void mieterNull_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setMieter(null);

        assertFalse(validate(position).isEmpty());
    }

    @Test
    void tarifNull_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setTarif(null);

        assertFalse(validate(position).isEmpty());
    }

    // ==================== Textfelder ====================

    @Test
    void quellReferenzAtMaxLength_IsValid() {
        Tarifposition position = gueltigePosition();
        position.setQuellReferenz("x".repeat(64));

        assertTrue(validate(position).isEmpty());
    }

    @Test
    void quellReferenzTooLong_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setQuellReferenz("x".repeat(65));

        assertFalse(validate(position).isEmpty());
    }

    @Test
    void bemerkungTooLong_IsRejected() {
        Tarifposition position = gueltigePosition();
        position.setBemerkung("x".repeat(201));

        assertFalse(validate(position).isEmpty());
    }

    @Test
    void quellReferenzAndBemerkungNull_AreOptional() {
        Tarifposition position = gueltigePosition();
        position.setQuellReferenz(null);
        position.setBemerkung(null);

        assertTrue(validate(position).isEmpty());
    }

    // ==================== toString ====================

    @Test
    void toString_ContainsIdsInsteadOfLazyProxies() {
        Tarifposition position = gueltigePosition();
        position.setId(7L);
        position.setOrgId(1L);

        String text = position.toString();

        assertTrue(text.contains("id=7"));
        assertTrue(text.contains("mieter=1"));
        assertTrue(text.contains("tarif=10"));
        assertTrue(text.contains("erfassungsart=MANUELL"));
    }
}
