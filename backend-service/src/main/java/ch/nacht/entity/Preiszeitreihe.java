package ch.nacht.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein Viertelstundenwert der dynamischen Einspeisepreise (Specs/Preiszeitreihe.md).
 *
 * <p><b>Ohne {@code orgId} und ohne {@code @Filter}</b> — begruendete Ausnahme von der
 * Mandantenregel: Die Preise der BKW sind fuer alle Mandanten identisch, eine Kopie je Mandant
 * waere redundant, und der taegliche Abruf-Job hat keinen Mandantenkontext (anders als etwa die
 * Aggregation, die ihre {@code org_id} aus den verarbeiteten Einheiten zieht). Geschuetzt ist der
 * Zugriff ueber die Permission {@code tarife:manage} am Controller (FR-2, NFR-2). Die ArchUnit-Regel
 * {@code everyEntityMustHaveOrgId} fuehrt diese Klasse deshalb namentlich als Ausnahme.
 *
 * <p><b>Zeitstempel in UTC</b>, verbatim aus der Quelle. Lokale Zeit waere kein tauglicher
 * Eindeutigkeitsschluessel: In der Nacht der Zeitumstellung auf Winterzeit tritt die Stunde
 * 02:00–03:00 zweimal auf, vier Viertelstundenwerte wuerden vier andere ueberschreiben. Die
 * Umrechnung nach Europe/Zurich passiert erst bei der Ausgabe.
 */
@Entity
@Table(name = "preiszeitreihe", schema = "zev", uniqueConstraints = {
    // Spiegelt den Constraint aus V129. Notwendig, nicht dekorativ: Das native Upsert nennt
    // ON CONFLICT (zeit_von), und in einer von Hibernate erzeugten Schema-Variante (Tests mit
    // ddl-auto) gaebe es den Constraint sonst nicht - jeder Upsert scheiterte dort mit
    // "no unique or exclusion constraint matching the ON CONFLICT specification".
    @UniqueConstraint(name = "uq_preiszeitreihe_zeit_von", columnNames = {"zeit_von"})
})
// Ebenfalls aus V129 gespiegelt, aus demselben Grund: In einem von Hibernate erzeugten Schema
// (Tests mit ddl-auto) gaebe es die Pruefungen sonst nicht, und ein Test koennte eine Zusicherung
// bestaetigen, die produktiv gar nicht von der Datenbank kommt.
@Check(name = "ck_preiszeitreihe_intervall", constraints = "zeit_von < zeit_bis")
public class Preiszeitreihe {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "preiszeitreihe_seq")
    @SequenceGenerator(name = "preiszeitreihe_seq", sequenceName = "zev.preiszeitreihe_seq",
            allocationSize = 1)
    private Long id;

    @NotNull(message = "Zeit von is required")
    @Column(name = "zeit_von", nullable = false)
    private LocalDateTime zeitVon;

    @NotNull(message = "Zeit bis is required")
    @Column(name = "zeit_bis", nullable = false)
    private LocalDateTime zeitBis;

    /**
      * Einspeisepreis in CHF/kWh — <b>darf 0 und negativ sein</b>.
      *
      * <p>Kein Vorzeichen-Wächter, und das ist der Punkt: Bei Überangebot (viel Sonne, wenig Last)
      * kostet das Einspeisen Geld statt Ertrag zu bringen. Eine Prüfung auf {@code >= 0} liesse den
      * Abruf genau in jenen Stunden scheitern, die für eine Steuerung am interessantesten sind.
      * V132 hat den entsprechenden CHECK-Constraint aus V129 wieder entfernt.
      */
    @NotNull(message = "Preis is required")
    @Column(name = "preis", precision = 10, scale = 5, nullable = false)
    private BigDecimal preis;

    /**
     * {@code publication_timestamp} der Quelle (UTC) — <b>optional</b>. Fehlt der Wert in der
     * Antwort, bleibt das Feld leer und die Preise werden trotzdem gespeichert: Ein Pflichtfeld
     * haette 96 fehlerfreie Preise verworfen, weil ein Metadatum fehlt, und ein ersatzweise
     * eingesetzter Abrufzeitpunkt waere eine erfundene Herkunftsangabe.
     */
    @Column(name = "publikation")
    private LocalDateTime publikation;

    @Column(name = "aktualisiert_am", nullable = false)
    private LocalDateTime aktualisiertAm;

    public Preiszeitreihe() {
    }

    public Preiszeitreihe(LocalDateTime zeitVon, LocalDateTime zeitBis, BigDecimal preis,
                          LocalDateTime publikation) {
        this.zeitVon = zeitVon;
        this.zeitBis = zeitBis;
        this.preis = preis;
        this.publikation = publikation;
    }

    /**
     * Setzt {@code aktualisiertAm}, wenn ueber JPA gespeichert wird.
     *
     * <p>Der Normalfall ist das native Upsert, das {@code now()} selbst schreibt. Ueber
     * {@code save()} gaebe es ohne diesen Haken eine NOT-NULL-Verletzung — die Spalte hat einen
     * Default in der Datenbank, aber Hibernate schickt beim Insert eine explizite {@code null}.
     */
    @PrePersist
    @PreUpdate
    void setzeAktualisierungszeitpunkt() {
        this.aktualisiertAm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getZeitVon() {
        return zeitVon;
    }

    public void setZeitVon(LocalDateTime zeitVon) {
        this.zeitVon = zeitVon;
    }

    public LocalDateTime getZeitBis() {
        return zeitBis;
    }

    public void setZeitBis(LocalDateTime zeitBis) {
        this.zeitBis = zeitBis;
    }

    public BigDecimal getPreis() {
        return preis;
    }

    public void setPreis(BigDecimal preis) {
        this.preis = preis;
    }

    public LocalDateTime getPublikation() {
        return publikation;
    }

    public void setPublikation(LocalDateTime publikation) {
        this.publikation = publikation;
    }

    public LocalDateTime getAktualisiertAm() {
        return aktualisiertAm;
    }

    public void setAktualisiertAm(LocalDateTime aktualisiertAm) {
        this.aktualisiertAm = aktualisiertAm;
    }
}
