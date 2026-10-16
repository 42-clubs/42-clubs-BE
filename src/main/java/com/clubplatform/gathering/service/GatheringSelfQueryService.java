package com.clubplatform.gathering.service;

import com.clubplatform.gathering.dto.MySession;
import com.clubplatform.gathering.repository.SessionParticipantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatheringSelfQueryService {
    private final SessionParticipantRepository participants;

    public List<MySession> findMine(long userId) {
        return participants.findMine(userId);
    }
}
