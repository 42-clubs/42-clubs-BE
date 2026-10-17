package com.clubplatform.user.service;

import com.clubplatform.global.exception.ResourceNotFoundException;
import com.clubplatform.user.dto.MeResponse;
import com.clubplatform.user.dto.UserIdentity;
import com.clubplatform.user.entity.User;
import com.clubplatform.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAccountService {
    private final UserRepository userRepository;

    @Transactional
    public UserIdentity synchronize42Profile(long intraId, String login, String displayName) {
        return synchronize42Profile(intraId, login, displayName, null);
    }

    @Transactional
    public UserIdentity synchronize42Profile(
            long intraId, String login, String displayName, String profileImageUrl) {
        // 엔티티 생성 규칙을 공유하며 외부 응답이 DB 제약을 위반하지 않도록 검증한다.
        new User(intraId, login, displayName);
        userRepository.synchronize42Profile(intraId, login, displayName, profileImageUrl);
        return identity(userRepository.findByIntraId(intraId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Optional<UserIdentity> findIdentity(long userId) {
        return userRepository.findById(userId).map(UserAccountService::identity);
    }

    @Transactional(readOnly = true)
    public MeResponse findMe(long userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        return new MeResponse(
                user.getId(),
                user.getIntraLogin(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getProfileImageUrl());
    }

    private static UserIdentity identity(User user) {
        return new UserIdentity(user.getId(), user.getRole().name());
    }
}
