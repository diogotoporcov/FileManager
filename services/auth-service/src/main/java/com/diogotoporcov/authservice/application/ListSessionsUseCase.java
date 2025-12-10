package com.diogotoporcov.authservice.application;

import com.diogotoporcov.authservice.api.dto.SessionResponse;
import com.diogotoporcov.authservice.session.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListSessionsUseCase {

    private final UserSessionRepository sessions;

    public ListSessionsUseCase(UserSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> execute(UUID userId, UUID currentSessionId) {
        return sessions.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(userId)
                .stream()
                .map(s -> new SessionResponse(
                        s.getId().toString(),
                        s.getDeviceName(),
                        s.getUserAgent(),
                        s.getIpAddress(),
                        s.getCreatedAt(),
                        s.getLastUsedAt(),
                        currentSessionId != null && currentSessionId.equals(s.getId())
                ))
                .toList();
    }
}
