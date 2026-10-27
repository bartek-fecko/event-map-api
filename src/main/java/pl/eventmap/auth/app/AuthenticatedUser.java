package pl.eventmap.auth.app;

import pl.eventmap.auth.domain.Role;

import java.util.UUID;

/** The authenticated principal placed in the SecurityContext by the JWT filter. */
public record AuthenticatedUser(UUID id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
