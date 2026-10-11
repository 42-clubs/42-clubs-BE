package com.clubplatform.user.dto;

/** 인증 기능과 협력할 때 엔티티 대신 전달하는 내부 사용자 식별 정보. */
public record UserIdentity(long id, String role) {}
