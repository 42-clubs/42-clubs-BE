package com.clubplatform.notice.dto;

import com.clubplatform.notice.entity.NoticeVisibility;
import jakarta.validation.constraints.*;

public record NoticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 50000) String content,
        NoticeVisibility visibility) {
    public NoticeRequest {
        if (visibility == null) visibility = NoticeVisibility.MEMBERS_ONLY;
    }
}
