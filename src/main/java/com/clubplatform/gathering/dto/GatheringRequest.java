package com.clubplatform.gathering.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record GatheringRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 50000) String content,
        @NotNull @Valid SessionRequest session) {}
