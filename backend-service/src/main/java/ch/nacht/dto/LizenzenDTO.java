package ch.nacht.dto;

import java.util.List;

public record LizenzenDTO(
    String name,
    String version,
    String license,
    String publisher,
    String url,
    List<LizenzenHashDTO> hashes
) {}
