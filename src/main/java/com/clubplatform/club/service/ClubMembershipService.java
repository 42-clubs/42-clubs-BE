package com.clubplatform.club.service;

import com.clubplatform.club.dto.MembershipResponse;
import com.clubplatform.club.dto.MembershipResponse.ClubMembership;
import com.clubplatform.club.entity.*;
import com.clubplatform.club.repository.*;
import com.clubplatform.global.exception.*;
import com.clubplatform.user.dto.UserDirectoryPage;
import com.clubplatform.user.service.UserAdministrationService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubMembershipService {
    private final ClubMemberRepository members;
    private final ClubRepository clubs;
    private final UserAdministrationService users;
    private final Clock clock;
    private final ClubPermissionService permissions;

    @Transactional
    public void register(long actorId, long clubId, long userId) {
        users.requireAdmin(actorId);
        users.requireExisting(userId);
        var club =
                clubs.findForUpdate(clubId)
                        .orElseThrow(() -> new ResourceNotFoundException("동아리를 찾을 수 없습니다."));
        if (club.getOperationStatus() != OperationStatus.ACTIVE)
            throw new ConflictException("CLUB_INACTIVE", "운영 중인 동아리에만 등록할 수 있습니다.");
        var existing = members.findByUserIdAndClubId(userId, clubId);
        if (existing.isPresent()) existing.get().register(actorId, clock.instant());
        else members.save(new ClubMember(userId, clubId, actorId, clock.instant()));
    }

    public MembershipResponse memberships(long actorId, long userId) {
        users.requireAdmin(actorId);
        users.requireExisting(userId);
        var ids =
                members.findByUserIdAndStatus(userId, "ACTIVE").stream()
                        .map(ClubMember::getClubId)
                        .toList();
        var result =
                clubs.findAllById(ids).stream()
                        .sorted(java.util.Comparator.comparing(Club::getId))
                        .map(
                                c ->
                                        new ClubMembership(
                                                c.getId(), c.getName(), c.getOperationStatus()))
                        .toList();
        return new MembershipResponse(
                userId,
                result,
                result.stream().filter(c -> c.operationStatus() == OperationStatus.ACTIVE).count());
    }

    public UserDirectoryPage roster(long actorId, long clubId, int page, int size) {
        if (!clubs.existsById(clubId)) throw new ResourceNotFoundException("동아리를 찾을 수 없습니다.");
        if (!permissions.permissions(actorId, clubId).canViewMembers())
            throw new AccessDeniedException("해당 동아리 부원만 조회할 수 있습니다.");
        var rows =
                members.findByClubIdAndStatus(
                        clubId, "ACTIVE", PageRequest.of(page, size, Sort.by("userId")));
        var summaries = users.findSummaries(rows.stream().map(ClubMember::getUserId).toList());
        return UserDirectoryPage.from(rows.map(m -> summaries.get(m.getUserId())));
    }
}
