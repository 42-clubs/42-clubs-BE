package com.clubplatform.user.repository;

import com.clubplatform.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByIntraId(Long intraId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@Param("id") long id);

    /** 동일한 42 ID의 동시 최초 로그인에서도 계정을 하나만 생성하고 기존 역할은 유지한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
        INSERT INTO users (intra_id, intra_login, display_name, role, created_at, updated_at)
        VALUES (:intraId, :login, :name, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (intra_id) DO UPDATE
        SET intra_login = EXCLUDED.intra_login, display_name = EXCLUDED.display_name,
            updated_at = CURRENT_TIMESTAMP
        """,
            nativeQuery = true)
    void synchronize42Profile(
            @Param("intraId") long intraId,
            @Param("login") String login,
            @Param("name") String name);
}
