package ch.nacht.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for AI-based unit matching by filename.
 */
public class EinheitMatchRequestDTO {

    @NotBlank
    private String filename;

    public EinheitMatchRequestDTO() {
    }

    public EinheitMatchRequestDTO(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
