package com.diogotoporcov.authservice.token;

import com.diogotoporcov.authservice.config.JwtProperties;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties props;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties props) {
        this.jwtEncoder = jwtEncoder;
        this.props = props;
    }

    public TokenPair mintAccessToken(UUID userId, String email, UUID sessionId) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(exp)
                .subject(userId.toString())
                .audience(List.of(props.audience()))
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("sid", sessionId.toString())
                .build();

        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        long expiresInSeconds = props.accessTokenTtl().toSeconds();

        return new TokenPair(tokenValue, "Bearer", expiresInSeconds);
    }

    public record TokenPair(String accessToken, String tokenType, long expiresIn) {}
}
