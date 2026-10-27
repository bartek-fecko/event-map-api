package pl.eventmap.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.eventmap.auth.domain.User;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 120) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record UserView(UUID id, String email, String displayName, String role) {

        public static UserView of(User user) {
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
        }
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, UserView user) {
    }
}
