package com.clubplatform.announcement.dto;

import jakarta.validation.constraints.*;

public record AnnouncementRequest(
        @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 50000) String content) {}
