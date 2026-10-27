package pl.eventmap.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Typed application configuration. Bound once at startup and validated, so a
 * missing secret crashes the boot instead of failing at the first request.
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull Auth auth,
        @Valid @NotNull Admin admin,
        @Valid @NotNull Media media,
        @Valid @NotNull MapSettings map,
        @Valid @NotNull Web web
) {

    public record Security(
            @NotBlank String jwtSecret,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl
    ) {
    }

    public record Auth(@Min(1) int rateLimitPerMinute) {
    }

    public record Admin(
            @NotBlank String bootstrapEmail,
            @NotBlank String bootstrapPassword
    ) {
    }

    public record Media(
            @NotNull Path storageDir,
            @NotBlank String publicBaseUrl
    ) {
    }

    public record MapSettings(
            @Min(1) @Max(2000) int maxResults,
            @Min(1) @Max(19) int clusterMaxZoom
    ) {
    }

    public record Web(@NotEmpty List<String> allowedOrigins) {
    }
}
