package com.clubplatform.user.service;

import com.clubplatform.global.exception.ResourceNotFoundException;
import com.clubplatform.user.dto.AdminUserSummary;
import com.clubplatform.user.dto.UserDirectoryPage;
import com.clubplatform.user.dto.UserSummary;
import com.clubplatform.user.entity.User;
import com.clubplatform.user.entity.UserRole;
import com.clubplatform.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdministrationService {
    private final UserRepository users;

    public UserDirectoryPage search(
            long actorId, String intraLogin, String role, int page, int size) {
        var actor = requireUser(actorId);
        boolean superAdmin = actor.getRole() == UserRole.SUPER_ADMIN;
        if (!superAdmin && actor.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("운영 권한이 필요합니다.");
        }
        if (role != null && !superAdmin) {
            throw new AccessDeniedException("역할 필터는 최고 관리자만 사용할 수 있습니다.");
        }
        Specification<User> filter = (root, query, cb) -> cb.conjunction();
        if (intraLogin != null) {
            String pattern =
                    "%"
                            + intraLogin
                                    .strip()
                                    .toLowerCase(Locale.ROOT)
                                    .replace("\\", "\\\\")
                                    .replace("%", "\\%")
                                    .replace("_", "\\_")
                            + "%";
            filter =
                    filter.and(
                            (root, query, cb) ->
                                    cb.like(cb.lower(root.get("intraLogin")), pattern, '\\'));
        }
        if (role != null) {
            if (!"ADMIN".equals(role)) {
                throw new IllegalArgumentException("지원하지 않는 역할 필터입니다.");
            }
            filter = filter.and((root, query, cb) -> cb.equal(root.get("role"), UserRole.ADMIN));
        }
        var result = users.findAll(filter, PageRequest.of(page, size, Sort.by("id")));
        if (superAdmin) {
            return UserDirectoryPage.from(
                    result.map(
                            user ->
                                    new AdminUserSummary(
                                            user.getId(),
                                            user.getIntraLogin(),
                                            user.getDisplayName(),
                                            user.getRole())));
        }
        return UserDirectoryPage.from(result.map(UserAdministrationService::summary));
    }

    @Transactional
    public void changeAdminRole(long actorId, long userId, boolean granted) {
        requireSuperAdmin(actorId);
        var target =
                users.findForUpdate(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        target.changeAdminRole(granted);
    }

    public boolean isAdmin(long actorId) {
        var role = requireUser(actorId).getRole();
        return role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN;
    }

    public void requireAdmin(long actorId) {
        if (!isAdmin(actorId)) {
            throw new AccessDeniedException("운영 권한이 필요합니다.");
        }
    }

    public void requireSuperAdmin(long actorId) {
        if (requireUser(actorId).getRole() != UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("최고 관리자 권한이 필요합니다.");
        }
    }

    public void requireExisting(long userId) {
        requireUser(userId);
    }

    /** 다른 기능이 사용자 Repository에 직접 접근하지 않도록 일괄 조회를 제공한다. */
    public Map<Long, UserSummary> findSummaries(List<Long> ids) {
        return users.findAllById(ids).stream()
                .map(UserAdministrationService::summary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    private User requireUser(long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }

    private static UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getIntraLogin(), user.getDisplayName());
    }
}
