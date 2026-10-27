package pl.eventmap.auth.app;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import pl.eventmap.auth.domain.Role;
import pl.eventmap.auth.domain.User;
import pl.eventmap.config.AppProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Stateless HS256 tokens. Access and refresh tokens are distinguished by a
 * {@code typ} claim, so a refresh token can never be replayed as an access token.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    public record TokenSubject(UUID userId, String email, Role role) {
    }

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        // HmacSHA256 requires >= 256 bits; a short secret fails here, at startup.
        this.key = Keys.hmacShaKeyFor(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = properties.security().accessTokenTtl();
        this.refreshTtl = properties.security().refreshTokenTtl();
        this.clock = clock;
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public String accessToken(User user) {
        return issue(user, TYPE_ACCESS, accessTtl);
    }

    public String refreshToken(User user) {
        return issue(user, TYPE_REFRESH, refreshTtl);
    }

    public TokenSubject parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public TokenSubject parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    private String issue(User user, String type, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private TokenSubject parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(type)) {
            throw new JwtException("Unexpected token type");
        }

        return new TokenSubject(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }
}
