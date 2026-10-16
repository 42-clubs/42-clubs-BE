package com.clubplatform.activity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "activity_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long activityId;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 20)
    private String attendanceStatus;

    private Long attendanceCheckedBy;
    private Instant attendanceCheckedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public ActivityParticipant(long parentId, long userId, Instant now) {
        this.activityId = parentId;
        this.userId = userId;
        status = "CONFIRMED";
        attendanceStatus = "UNCHECKED";
        createdAt = now;
        updatedAt = now;
    }

    public void confirm(Instant now) {
        if ("CONFIRMED".equals(status))
            throw new com.clubplatform.global.exception.ConflictException(
                    "ALREADY_JOINED", "이미 참가 중입니다.");
        requireUnchecked();
        status = "CONFIRMED";
        updatedAt = now;
    }

    public void cancel(Instant now) {
        if ("CANCELLED".equals(status))
            throw new com.clubplatform.global.exception.ConflictException(
                    "ALREADY_CANCELLED", "이미 취소했습니다.");
        requireUnchecked();
        status = "CANCELLED";
        updatedAt = now;
    }

    private void requireUnchecked() {
        if (!"UNCHECKED".equals(attendanceStatus))
            throw new com.clubplatform.global.exception.ConflictException(
                    "ATTENDANCE_RECORDED", "출결이 기록된 참가 관계입니다.");
    }

    public void attendance(String value, long actorId, Instant now) {
        if (!"CONFIRMED".equals(status))
            throw new com.clubplatform.global.exception.ConflictException(
                    "PARTICIPANT_NOT_CONFIRMED", "확정 참가자만 출결을 기록할 수 있습니다.");
        if (!"PRESENT".equals(value) && !"UNCHECKED".equals(value))
            throw new IllegalArgumentException("지원하지 않는 출결입니다.");
        attendanceStatus = value;
        attendanceCheckedBy = "PRESENT".equals(value) ? actorId : null;
        attendanceCheckedAt = "PRESENT".equals(value) ? now : null;
        updatedAt = now;
    }
}
