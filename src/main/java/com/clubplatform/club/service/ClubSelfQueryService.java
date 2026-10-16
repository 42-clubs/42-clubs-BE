package com.clubplatform.club.service;

import com.clubplatform.club.dto.*;
import com.clubplatform.club.entity.*;
import com.clubplatform.club.repository.*;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.user.service.UserAdministrationService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubSelfQueryService {
    private final ClubRepository clubs;
    private final ClubMemberRepository members;
    private final ClubManagerRepository managers;
    private final UserAdministrationService users;

    public PageResponse<MyClubResponse> list(long userId, int page, int size) {
        var memberIds =
                members.findByUserIdAndStatus(userId, "ACTIVE").stream()
                        .map(ClubMember::getClubId)
                        .collect(Collectors.toSet());
        var managerIds =
                managers.findByUserId(userId).stream()
                        .map(ClubManager::getClubId)
                        .collect(Collectors.toSet());
        var ids = new HashSet<>(memberIds);
        ids.addAll(managerIds);
        boolean admin = users.isAdmin(userId);
        var rows =
                clubs.findAllById(ids).stream()
                        .sorted(Comparator.comparing(Club::getId))
                        .map(
                                c ->
                                        new MyClubResponse(
                                                c.getId(),
                                                c.getName(),
                                                c.getOperationStatus(),
                                                memberIds.contains(c.getId()),
                                                managerIds.contains(c.getId()),
                                                ClubPermissions.of(
                                                        memberIds.contains(c.getId()),
                                                        managerIds.contains(c.getId()),
                                                        admin,
                                                        c.getOperationStatus())))
                        .toList();
        var pageable = PageRequest.of(page, size);
        int start = (int) Math.min(pageable.getOffset(), rows.size());
        return PageResponse.from(
                new PageImpl<>(
                        rows.subList(start, Math.min(start + size, rows.size())),
                        pageable,
                        rows.size()));
    }

    public Map<Long, String> names(Collection<Long> ids) {
        return clubs.findAllById(ids).stream()
                .collect(Collectors.toMap(Club::getId, Club::getName));
    }
}
