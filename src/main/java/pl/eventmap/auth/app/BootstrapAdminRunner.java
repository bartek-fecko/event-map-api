package pl.eventmap.auth.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.eventmap.auth.domain.Role;
import pl.eventmap.auth.domain.User;
import pl.eventmap.auth.domain.UserRepository;
import pl.eventmap.config.AppProperties;

import java.time.Clock;

/**
 * Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD when no
 * account with that address exists. Idempotent, so it is safe on every boot.
 */
@Component
@Order(1)
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    public BootstrapAdminRunner(UserRepository users, PasswordEncoder passwordEncoder,
                                AppProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.admin().bootstrapEmail();
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }

        users.save(User.registered(
                email,
                passwordEncoder.encode(properties.admin().bootstrapPassword()),
                "Administrator",
                Role.ADMIN,
                clock.instant()));
        log.info("Bootstrapped administrator account {}", email);
    }
}
