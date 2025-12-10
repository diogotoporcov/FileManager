package com.diogotoporcov.authservice.token;

import com.diogotoporcov.authservice.api.RequestContextExtractor.SessionContext;
import com.diogotoporcov.authservice.config.JwtProperties;
import com.diogotoporcov.authservice.error.InvalidRefreshTokenException;
import com.diogotoporcov.authservice.error.RefreshTokenReuseDetectedException;
import com.diogotoporcov.authservice.error.SessionNotFoundException;
import com.diogotoporcov.authservice.session.entity.UserSession;
import com.diogotoporcov.authservice.session.repository.UserSessionRepository;
import com.diogotoporcov.authservice.token.entity.RefreshToken;
import com.diogotoporcov.authservice.token.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final UserSessionRepository sessions;
    private final RefreshTokenHasher hasher;
    private final JwtProperties props;

    public RefreshTokenService(
            RefreshTokenRepository tokens,
            UserSessionRepository sessions,
            RefreshTokenHasher hasher,
            JwtProperties props
    ) {
        this.tokens = tokens;
        this.sessions = sessions;
        this.hasher = hasher;
        this.props = props;
    }

    @Transactional
    public IssueResult issueNew(UUID userId, SessionContext ctx) {
        UUID sessionId = UUID.randomUUID();
        UserSession session = new UserSession(
                sessionId,
                userId,
                ctx.deviceName(),
                ctx.userAgent(),
                ctx.ipAddress()
        );
        sessions.save(session);

        UUID tokenId = UUID.randomUUID();
        String secret = randomSecret();
        String secretHash = hasher.sha256Hex(secret);

        Instant exp = Instant.now().plus(props.refreshTokenTtl());
        tokens.save(new RefreshToken(tokenId, userId, sessionId, secretHash, exp));

        return new IssueResult(sessionId, format(tokenId, secret));
    }

    @Transactional
    public RotationResult rotate(String presentedRefreshToken, SessionContext ctx) {
        Parsed parsed = parse(presentedRefreshToken);

        RefreshToken current = tokens.findById(parsed.tokenId())
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();

        if (current.isRevoked() || current.isExpired(now)) {
            if (current.hasBeenReplaced()) {
                revokeSessionById(current.getSessionId(), now);
                tokens.revokeAllForSession(current.getSessionId(), now);
                throw new RefreshTokenReuseDetectedException();
            }
            throw new InvalidRefreshTokenException();
        }

        UserSession session = sessions.findById(current.getSessionId())
                .orElseThrow(SessionNotFoundException::new);

        if (session.isRevoked()) {
            throw new InvalidRefreshTokenException();
        }

        String presentedHash = hasher.sha256Hex(parsed.secret());
        if (!hasher.constantTimeEquals(presentedHash, current.getSecretHash())) {
            throw new InvalidRefreshTokenException();
        }

        UUID newTokenId = UUID.randomUUID();
        String newSecret = randomSecret();
        String newHash = hasher.sha256Hex(newSecret);
        Instant newExp = now.plus(props.refreshTokenTtl());

        tokens.save(new RefreshToken(newTokenId, current.getUserId(), current.getSessionId(), newHash, newExp));

        current.replaceWith(newTokenId, now);
        tokens.save(current);

        session.touch(ctx.userAgent(), ctx.ipAddress());
        sessions.save(session);

        return new RotationResult(current.getUserId(), current.getSessionId(), format(newTokenId, newSecret));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        Instant now = Instant.now();
        sessions.revokeAllForUser(userId, now);
        tokens.revokeAllForUser(userId, now);
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        Instant now = Instant.now();

        UserSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!session.isRevoked()) {
            session.revoke(now);
            sessions.save(session);
        }

        tokens.revokeAllForSession(sessionId, now);
    }

    private void revokeSessionById(UUID sessionId, Instant now) {
        sessions.findById(sessionId).ifPresent(s -> {
            if (!s.isRevoked()) {
                s.revoke(now);
                sessions.save(s);
            }
        });
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String format(UUID tokenId, String secret) {
        return tokenId + "." + secret;
    }

    private static Parsed parse(String value) {
        if (value == null || value.isBlank()) throw new InvalidRefreshTokenException();
        String[] parts = value.split("\\.", 2);
        if (parts.length != 2) throw new InvalidRefreshTokenException();
        try {
            UUID id = UUID.fromString(parts[0]);
            String secret = parts[1];
            if (secret.isBlank()) throw new InvalidRefreshTokenException();
            return new Parsed(id, secret);
        } catch (IllegalArgumentException e) {
            throw new InvalidRefreshTokenException();
        }
    }

    private record Parsed(UUID tokenId, String secret) {}

    public record IssueResult(UUID sessionId, String refreshToken) {}
    public record RotationResult(UUID userId, UUID sessionId, String newRefreshToken) {}
}
