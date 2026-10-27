package pl.eventmap.event;

import org.junit.jupiter.api.Test;
import pl.eventmap.support.AbstractIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventLifecycleIT extends AbstractIntegrationTest {

    @Test
    void submittedEventIsModeratedThenVisibleOnTheMap() {
        String email = "organizer-" + UUID.randomUUID() + "@test.local";
        var registration = post("/api/v1/auth/register", """
                {"email":"%s","password":"password123","displayName":"Test Organizer"}"""
                .formatted(email), null);

        assertThat(registration.status()).isEqualTo(201);
        assertThat(registration.text("$.user.role")).isEqualTo("USER");
        String userToken = registration.text("$.accessToken");

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String title = "Integration Test Gig " + UUID.randomUUID();
        var created = post("/api/v1/events", createEventBody(title, start, start.plus(2, ChronoUnit.HOURS)), userToken);

        assertThat(created.status()).isEqualTo(201);
        String eventId = created.text("$.id");

        // PENDING is not part of the public read model, not even by id.
        assertThat(get("/api/v1/events/" + eventId, null).status()).isEqualTo(404);

        // A regular user cannot moderate.
        assertThat(post("/api/v1/admin/events/" + eventId + "/approve", "", userToken).status()).isEqualTo(403);

        String adminToken = login("admin@test.local", "admin-pass-123");

        var queue = get("/api/v1/admin/events?status=PENDING&size=100", adminToken);
        assertThat(queue.status()).isEqualTo(200);
        assertThat(queue.list("$.items[*].id")).contains(eventId);

        assertThat(post("/api/v1/admin/events/" + eventId + "/approve", "", adminToken).status()).isEqualTo(204);

        var detail = get("/api/v1/events/" + eventId, null);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.text("$.status")).isEqualTo("PUBLISHED");
        assertThat(detail.text("$.title")).isEqualTo(title);
        assertThat(detail.number("$.venue.lat").doubleValue()).isEqualTo(50.0614);
        assertThat(detail.number("$.venue.lon").doubleValue()).isEqualTo(19.9372);

        // The map viewport is what the frontend actually calls.
        var map = get("/api/v1/map/events?bbox=19.90,50.03,19.97,50.09&zoom=14", null);
        assertThat(map.status()).isEqualTo(200);
        assertThat(map.text("$.mode")).isEqualTo("events");
        assertThat(map.list("$.events[*].id")).contains(eventId);

        // ... and the cursor list, filtered by city.
        var list = get("/api/v1/events?city=krakow&limit=50", null);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.list("$.items[*].eventId")).contains(eventId);
    }

    @Test
    void anonymousSubmissionIsRejectedAsProblemJson() {
        var response = post("/api/v1/events", "{}", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.text("$.code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void invalidBodyIsRejectedWithFieldErrors() {
        String token = login("admin@test.local", "admin-pass-123");

        var response = post("/api/v1/admin/events", """
                {"title":"","occurrences":[],"categorySlugs":[]}""", token);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat(response.list("$.fieldErrors[*].field")).contains("title");
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String email = "refresh-" + UUID.randomUUID() + "@test.local";
        var registration = post("/api/v1/auth/register", """
                {"email":"%s","password":"password123","displayName":"Refresh Test"}"""
                .formatted(email), null);
        String refreshToken = registration.text("$.refreshToken");

        assertThat(get("/api/v1/me/events", refreshToken).status()).isEqualTo(401);

        var refreshed = post("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}""".formatted(refreshToken), null);
        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.text("$.accessToken")).isNotBlank();
    }

    private String login(String email, String password) {
        var response = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, password), null);
        assertThat(response.status()).isEqualTo(200);
        return response.text("$.accessToken");
    }

    private static String createEventBody(String title, Instant startsAt, Instant endsAt) {
        return """
                {
                  "title": "%s",
                  "description": "Created by an integration test",
                  "venue": {
                    "name": "Test Venue",
                    "address": "ul. Testowa 1",
                    "citySlug": "krakow",
                    "latitude": 50.0614,
                    "longitude": 19.9372
                  },
                  "occurrences": [
                    {"startsAt": "%s", "endsAt": "%s"}
                  ],
                  "categorySlugs": ["music"]
                }""".formatted(title, startsAt, endsAt);
    }
}
