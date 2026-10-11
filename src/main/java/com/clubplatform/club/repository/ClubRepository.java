package com.clubplatform.club.repository;

import com.clubplatform.club.entity.Club;
import com.clubplatform.club.entity.OperationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 동아리 엔티티의 영속성 접근과 운영 상태별 페이지 조회를 제공한다. */
public interface ClubRepository extends JpaRepository<Club, Long> {

    /** 지정한 운영 상태의 동아리를 페이지 조건에 맞게 조회한다. */
    Page<Club> findByOperationStatus(OperationStatus operationStatus, Pageable pageable);

    /** 지정한 운영 상태를 제외한 동아리를 페이지 조건에 맞게 조회한다. */
    Page<Club> findByOperationStatusNot(OperationStatus operationStatus, Pageable pageable);
}
