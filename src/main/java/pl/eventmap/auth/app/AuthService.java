package pl.eventmap.auth.app;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.eventmap.auth.api.AuthDtos.RegisterRequest;
import pl.eventmap.auth.api.AuthDtos.TokenResponse;
import pl.eventmap.auth.api.AuthDtos.UserView;
import pl.eventmap.auth.domain.Role;
import pl.eventmap.auth.domain.User;
import pl.eventmap.auth.domain.UserRepository;
import pl.eventmap.common.ApiException;

import java.time.Clock;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = User.registered(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                Role.USER,
                clock.instant());
        return tokens(users.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(AuthService::invalidCredentials);

        // Always run the hash comparison so a missing account and a wrong password
        // take the same time and return the same error.
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return tokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        JwtService.TokenSubject subject;
        try {
            subject = jwtService.parseRefreshToken(refreshToken);
        } catch (RuntimeException ex) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is not valid");
        }

        User user = users.findById(subject.userId())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is not valid"));
        return tokens(user);
    }

    private TokenResponse tokens(User user) {
        return new TokenResponse(
                jwtService.accessToken(user),
                jwtService.refreshToken(user),
                jwtService.accessTtl().toSeconds(),
                UserView.of(user));
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    public User requireUser(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("UNKNOWN_USER", "Account no longer exists"));
    }
}
