package com.clubplatform.global.exception;

/** 서비스에서 조회 대상이 없음을 알리는 예외. */
public class ResourceNotFoundException extends RuntimeException {

    /** 조회 대상이 없다는 오류 메시지를 예외에 전달한다. */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
