package com.diogotoporcov.authservice.token;

import com.diogotoporcov.authservice.config.JwtProperties;
import com.diogotoporcov.authservice.error.InvalidRefreshTokenException;
import com.diogotoporcov.authservice.error.RefreshTokenReuseDetectedException;
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

    private final RefreshTokenRepository repo;
    private final RefreshTokenHasher hasher;
    private final JwtProperties props;

    public RefreshTokenService(RefreshTokenRepository repo, RefreshTokenHasher hasher, JwtProperties props) {
        this.repo = repo;
        this.hasher = hasher;
        this.props = props;
    }

    @Transactional
    public String issueNew(UUID userId) {
        UUID tokenId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String secret = randomSecret();
        String secretHash = hasher.sha256Hex(secret);

        Instant exp = Instant.now().plus(props.refreshTokenTtl());
        repo.save(new RefreshToken(tokenId, userId, familyId, secretHash, exp));

        return format(tokenId, secret);
    }

    @Transactional
    public RotationResult rotate(String presentedRefreshToken) {
        Parsed parsed = parse(presentedRefreshToken);

        RefreshToken current = repo.findById(parsed.tokenId())
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();

        if (current.isExpired(now) || current.isRevoked()) {
            if (current.hasBeenReplaced()) {
                repo.revokeFamily(current.getFamilyId(), now);
                throw new RefreshTokenReuseDetectedException();
            }
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

        repo.save(new RefreshToken(newTokenId, current.getUserId(), current.getFamilyId(), newHash, newExp));

        current.replaceWith(newTokenId, now);
        repo.save(current);

        return new RotationResult(current.getUserId(), format(newTokenId, newSecret));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repo.revokeAllForUser(userId, Instant.now());
    }

    @Transactional
    public void revokeAllForUserIfExists(UUID userId) {
        repo.revokeAllForUser(userId, Instant.now());
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

    public record RotationResult(UUID userId, String newRefreshToken) {}
}
