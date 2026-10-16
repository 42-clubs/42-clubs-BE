package com.clubplatform.activity.service;

import com.clubplatform.activity.dto.MyActivity;
import com.clubplatform.activity.repository.ActivityParticipantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivitySelfQueryService {
    private final ActivityParticipantRepository participants;

    public List<MyActivity> findMine(long userId) {
        return participants.findMine(userId);
    }
}
