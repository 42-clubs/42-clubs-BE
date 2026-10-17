package com.clubplatform.club.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** null은 생략한 필드만 나타내며 명시적 null·지원하지 않는 필드는 역직렬화에서 거절한다. */
public record ClubUpdateRequest(String name, String summary, String description) {
    private static final Set<String> FIELDS = Set.of("name", "summary", "description");

    public ClubUpdateRequest {
        if (name == null && summary == null && description == null)
            throw new IllegalArgumentException("수정할 동아리 정보를 하나 이상 보내 주세요.");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ClubUpdateRequest from(JsonNode body) {
        if (body == null || !body.isObject())
            throw new IllegalArgumentException("동아리 수정 요청은 JSON 객체여야 합니다.");
        body.fieldNames()
                .forEachRemaining(
                        field -> {
                            if (!FIELDS.contains(field))
                                throw new IllegalArgumentException("지원하지 않는 동아리 수정 항목입니다.");
                        });
        return new ClubUpdateRequest(
                text(body, "name"), text(body, "summary"), text(body, "description"));
    }

    private static String text(JsonNode body, String field) {
        if (!body.has(field)) return null;
        var value = body.get(field);
        if (!value.isTextual()) throw new IllegalArgumentException("동아리 정보는 null이 아닌 문자열이어야 합니다.");
        return value.textValue();
    }
}
