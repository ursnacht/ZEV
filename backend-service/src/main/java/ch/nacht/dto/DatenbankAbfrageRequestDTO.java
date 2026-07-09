package ch.nacht.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO für die generische Datenbank-Ansicht (nur zev_admin, read-only).
 * {@code where} ist optional (SQL-WHERE-Klausel ohne das Schlüsselwort WHERE).
 * {@code page}/{@code size} steuern die Pagination; Defaults/Grenzen werden im Service geklemmt.
 */
public class DatenbankAbfrageRequestDTO {

    @NotBlank
    private String tabelle;

    private String where;

    private Integer page;

    private Integer size;

    public DatenbankAbfrageRequestDTO() {
    }

    public String getTabelle() {
        return tabelle;
    }

    public void setTabelle(String tabelle) {
        this.tabelle = tabelle;
    }

    public String getWhere() {
        return where;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
