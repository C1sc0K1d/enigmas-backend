package br.com.enigmas.backend.terminal.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CommandRequest(
    @NotBlank @Size(max = 80) String computerId,
    @NotBlank @Size(max = 2000) String text,
    @NotNull UUID requestId,
    @PositiveOrZero long revision,
    @NotNull UUID serverSessionId) {}
