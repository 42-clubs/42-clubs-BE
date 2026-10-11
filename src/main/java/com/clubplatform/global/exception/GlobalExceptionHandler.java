package com.clubplatform.global.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 서비스 예외와 Spring MVC 입력 오류를 HTTP 오류 응답으로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** 조회 대상 없음 예외를 공통 오류 본문과 404 상태로 반환한다. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("RESOURCE_NOT_FOUND", exception.getMessage()));
    }

    /** Spring MVC의 400 오류 본문을 통일하고 나머지 상태는 기본 처리에 위임한다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            body = new ApiErrorResponse("INVALID_REQUEST", "요청 값의 형식 또는 범위를 확인해 주세요.");
        }
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }
}
