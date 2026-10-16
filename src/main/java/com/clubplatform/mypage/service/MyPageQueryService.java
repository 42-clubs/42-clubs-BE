package com.clubplatform.mypage.service;

import com.clubplatform.activity.service.ActivitySelfQueryService;
import com.clubplatform.club.service.ClubSelfQueryService;
import com.clubplatform.gathering.dto.MySession;
import com.clubplatform.gathering.service.GatheringSelfQueryService;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.mypage.dto.*;
import com.clubplatform.mypage.dto.MyParticipationGroup.Item;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageQueryService {
    private final ActivitySelfQueryService activities;
    private final GatheringSelfQueryService gatherings;
    private final ClubSelfQueryService clubs;
    private final Clock clock;

    public PageResponse<MyParticipationGroup> participations(
            long userId, ParticipationPeriod period, int page, int size) {
        var now = clock.instant();
        var mine =
                activities.findMine(userId).stream()
                        .filter(a -> period.includes(a.startsAt(), now))
                        .toList();
        var names = clubs.names(mine.stream().map(a -> a.clubId()).distinct().toList());
        var groups = new ArrayList<MyParticipationGroup>();
        mine.stream()
                .collect(Collectors.groupingBy(a -> a.clubId()))
                .forEach(
                        (id, rows) ->
                                groups.add(
                                        new MyParticipationGroup(
                                                "CLUB",
                                                id,
                                                names.get(id),
                                                rows.stream()
                                                        .map(
                                                                a ->
                                                                        new Item(
                                                                                a.activityId(),
                                                                                null,
                                                                                a.title(),
                                                                                a.startsAt(),
                                                                                null,
                                                                                a.status(),
                                                                                a
                                                                                        .attendanceStatus(),
                                                                                a.organizerId()
                                                                                        == userId))
                                                        .toList())));
        var sessions =
                gatherings.findMine(userId).stream()
                        .filter(s -> period.includes(s.startsAt(), now))
                        .toList();
        groups.addAll(sessionGroups(userId, sessions));
        return page(groups, period == ParticipationPeriod.PAST, page, size);
    }

    public PageResponse<MyParticipationGroup> gatherings(
            long userId, GatheringScope scope, int page, int size) {
        var now = clock.instant();
        var mine = gatherings.findMine(userId);
        var activeIds =
                mine.stream()
                        .filter(s -> s.endsAt().isAfter(now))
                        .map(s -> s.gatheringId())
                        .collect(Collectors.toSet());
        var rows =
                mine.stream()
                        .filter(
                                s ->
                                        scope == GatheringScope.ALL
                                                || activeIds.contains(s.gatheringId()))
                        .toList();
        return page(sessionGroups(userId, rows), false, page, size);
    }

    private List<MyParticipationGroup> sessionGroups(long userId, List<MySession> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(s -> s.gatheringId()))
                .entrySet()
                .stream()
                .map(
                        e ->
                                new MyParticipationGroup(
                                        "GATHERING",
                                        e.getKey(),
                                        e.getValue().get(0).gatheringTitle(),
                                        e.getValue().stream()
                                                .map(
                                                        s ->
                                                                new Item(
                                                                        null,
                                                                        s.sessionId(),
                                                                        s.title(),
                                                                        s.startsAt(),
                                                                        s.endsAt(),
                                                                        s.status(),
                                                                        s.attendanceStatus(),
                                                                        s.organizerId() == userId))
                                                .toList()))
                .toList();
    }

    private PageResponse<MyParticipationGroup> page(
            List<MyParticipationGroup> groups, boolean past, int page, int size) {
        Comparator<Item> order =
                Comparator.comparing(Item::startsAt)
                        .thenComparing(
                                i -> i.activityId() != null ? i.activityId() : i.sessionId());
        if (past) order = order.reversed();
        final Comparator<Item> itemOrder = order;
        Comparator<MyParticipationGroup> groupOrder =
                Comparator.comparing(g -> g.items().get(0).startsAt());
        if (past) groupOrder = groupOrder.reversed();
        groupOrder =
                groupOrder
                        .thenComparing(MyParticipationGroup::type)
                        .thenComparingLong(MyParticipationGroup::groupId);
        var sorted =
                groups.stream()
                        .map(
                                g ->
                                        new MyParticipationGroup(
                                                g.type(),
                                                g.groupId(),
                                                g.name(),
                                                g.items().stream().sorted(itemOrder).toList()))
                        .sorted(groupOrder)
                        .toList();
        var pageable = PageRequest.of(page, size);
        int start = (int) Math.min(pageable.getOffset(), sorted.size());
        return PageResponse.from(
                new PageImpl<>(
                        sorted.subList(start, Math.min(start + size, sorted.size())),
                        pageable,
                        sorted.size()));
    }
}
