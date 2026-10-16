package com.clubplatform.activity.controller;

import com.clubplatform.activity.storage.StorageUnavailableException;
import com.clubplatform.global.exception.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class PhotoExceptionHandler {
    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> unavailable(StorageUnavailableException e) {
        return ResponseEntity.status(503)
                .body(new ApiErrorResponse("STORAGE_UNAVAILABLE", e.getMessage()));
    }
}
