package com.clubplatform.notice.dto;

import jakarta.validation.constraints.*;

public record NoticeRequest(
        @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 50000) String content) {}
