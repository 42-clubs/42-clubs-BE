package com.clubplatform.global.exception;

/** API 오류의 식별 코드와 사용자 안내 메시지. */
public record ApiErrorResponse(String code, String message) {}
