package com.clubplatform.club.service;

import com.clubplatform.club.dto.ClubDetailResponse;
import com.clubplatform.club.dto.ClubListResponse;
import com.clubplatform.club.dto.ClubSummaryResponse;
import com.clubplatform.club.entity.Club;
import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.global.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 읽기 전용 트랜잭션에서 동아리를 조회하고 공개 응답을 구성한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubQueryService {

    private final ClubRepository clubRepository;
    private final Clock clock;

    /** 운영 상태로 필터링한 동아리를 ID순으로 페이지 조회하고 모집 상태를 계산한다. */
    public ClubListResponse findClubs(OperationStatus operationStatus, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<Club> clubs =
                operationStatus == null
                        ? clubRepository.findByOperationStatusNot(
                                OperationStatus.ARCHIVED, pageable)
                        : clubRepository.findByOperationStatus(operationStatus, pageable);
        LocalDate today = LocalDate.now(clock);
        return ClubListResponse.from(clubs.map(club -> ClubSummaryResponse.from(club, today)));
    }

    /** 동아리 상세와 모집 안내를 반환하며 대상이 없으면 예외를 발생시킨다. */
    public ClubDetailResponse findClub(long clubId) {
        Club club =
                clubRepository
                        .findById(clubId)
                        .orElseThrow(() -> new ResourceNotFoundException("동아리를 찾을 수 없습니다."));
        return ClubDetailResponse.from(club, LocalDate.now(clock));
    }
}
