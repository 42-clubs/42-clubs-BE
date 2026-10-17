package com.clubplatform.activity.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** 생략한 항목만 null로 표현하며 수정 대상 외의 필드나 명시적 null을 받지 않는다. */
public record ActivityUpdateRequest(String title, String content) {
    private static final Set<String> FIELDS = Set.of("title", "content");

    public ActivityUpdateRequest {
        if (title == null && content == null)
            throw new IllegalArgumentException("수정할 제목 또는 본문을 보내 주세요.");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ActivityUpdateRequest from(JsonNode body) {
        if (body == null || !body.isObject())
            throw new IllegalArgumentException("활동 수정 요청은 JSON 객체여야 합니다.");
        body.fieldNames()
                .forEachRemaining(
                        field -> {
                            if (!FIELDS.contains(field))
                                throw new IllegalArgumentException("활동은 제목과 본문만 수정할 수 있습니다.");
                        });
        return new ActivityUpdateRequest(text(body, "title"), text(body, "content"));
    }

    private static String text(JsonNode body, String field) {
        if (!body.has(field)) return null;
        var value = body.get(field);
        if (!value.isTextual()) throw new IllegalArgumentException("제목과 본문은 null이 아닌 문자열이어야 합니다.");
        return value.textValue();
    }
}
