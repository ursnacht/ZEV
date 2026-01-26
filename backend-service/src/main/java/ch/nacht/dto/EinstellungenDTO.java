package ch.nacht.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for tenant-specific settings.
 */
public class EinstellungenDTO {

    private Long id;

    @Valid
    @NotNull(message = "Rechnung configuration is required")
    private RechnungKonfigurationDTO rechnung;

    public EinstellungenDTO() {
    }

    public EinstellungenDTO(Long id, RechnungKonfigurationDTO rechnung) {
        this.id = id;
        this.rechnung = rechnung;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RechnungKonfigurationDTO getRechnung() {
        return rechnung;
    }

    public void setRechnung(RechnungKonfigurationDTO rechnung) {
        this.rechnung = rechnung;
    }
}
