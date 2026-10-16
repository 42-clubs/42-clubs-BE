package com.clubplatform.gathering.dto;

import jakarta.validation.constraints.*;

public record AttendanceRequest(
        @NotNull @Pattern(regexp = "UNCHECKED|PRESENT") String attendanceStatus) {}
