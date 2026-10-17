package com.clubplatform.activity.dto;

/** 개인 명단을 포함하지 않는 신청 확정·출석 확인 인원. */
public record ActivityAttendanceCounts(long currentParticipantCount, long presentParticipantCount) {
    public static final ActivityAttendanceCounts EMPTY = new ActivityAttendanceCounts(0, 0);
}
