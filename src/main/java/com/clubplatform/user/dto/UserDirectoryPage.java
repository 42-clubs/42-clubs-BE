package com.clubplatform.user.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record UserDirectoryPage(
        List<?> content, int page, int size, long totalElements, int totalPages) {
    public static UserDirectoryPage from(Page<?> users) {
        return new UserDirectoryPage(
                users.getContent(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages());
    }
}
