package pl.eventmap.geo;

import org.junit.jupiter.api.Test;
import pl.eventmap.support.AbstractIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MapViewportIT extends AbstractIntegrationTest {

    // A patch of map no other test touches, so the counts are deterministic.
    private static final double BASE_LAT = 50.2000;
    private static final double BASE_LON = 19.7000;
    private static final String TIGHT_BBOX = "19.6990,50.1990,19.7020,50.2020";
    private static final String EMPTY_BBOX = "0.0000,0.0000,0.0010,0.0010";

    @Test
    void lowZoomReturnsClustersAndHighZoomReturnsEvents() {
        String adminToken = login();

        List<String> created = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            created.add(createPublishedEvent(adminToken, i));
        }

        var clusters = get("/api/v1/map/events?bbox=" + TIGHT_BBOX + "&zoom=10", null);
        assertThat(clusters.status()).isEqualTo(200);
        assertThat(clusters.text("$.mode")).isEqualTo("clusters");
        assertThat((Integer) clusters.number("$.clusters[0].count")).isEqualTo(3);
        assertThat(clusters.list("$.events[*]")).isEmpty();

        var events = get("/api/v1/map/events?bbox=" + TIGHT_BBOX + "&zoom=14", null);
        assertThat(events.status()).isEqualTo(200);
        assertThat(events.text("$.mode")).isEqualTo("events");
        assertThat(events.list("$.events[*].id")).containsAll(created);
    }

    @Test
    void viewportOutsideTheDataReturnsNothing() {
        var response = get("/api/v1/map/events?bbox=" + EMPTY_BBOX + "&zoom=14", null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.list("$.events[*]")).isEmpty();
        assertThat(response.bool("$.truncated")).isFalse();
    }

    @Test
    void malformedBboxIsAProblemDetail() {
        var response = get("/api/v1/map/events?bbox=19.9,50.1&zoom=12", null);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("$.code")).isEqualTo("INVALID_BBOX");
    }

    @Test
    void missingRequiredParameterIsAProblemDetail() {
        var response = get("/api/v1/map/events?zoom=12", null);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("$.code")).isEqualTo("MISSING_PARAMETER");
    }

    @Test
    void unknownResourceIsNotFoundNotAServerError() {
        var response = get("/media/does-not-exist.png", null);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.text("$.code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void cursorPaginationWalksForwardWithoutDuplicates() {
        String adminToken = login();
        createPublishedEvent(adminToken, 0);

        var first = get("/api/v1/events?city=krakow&limit=2", null);
        assertThat(first.status()).isEqualTo(200);
        List<String> firstPage = first.list("$.items[*].occurrenceId");
        String cursor = first.text("$.nextCursor");

        if (cursor != null) {
            var second = get("/api/v1/events?city=krakow&limit=2&cursor=" + cursor, null);
            assertThat(second.status()).isEqualTo(200);
            List<String> secondPage = second.list("$.items[*].occurrenceId");
            assertThat(secondPage).doesNotContainAnyElementsOf(firstPage);
        }
    }

    private String createPublishedEvent(String adminToken, int index) {
        String title = "Viewport Event " + index + " " + UUID.randomUUID();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        var response = post("/api/v1/admin/events", String.format(Locale.ROOT, """
                {
                  "title": "%s",
                  "venue": {
                    "name": "Viewport Venue %d",
                    "address": "Test 1",
                    "citySlug": "krakow",
                    "latitude": %.4f,
                    "longitude": %.4f
                  },
                  "occurrences": [{"startsAt": "%s", "endsAt": "%s"}],
                  "categorySlugs": ["tech"]
                }""", title, index, BASE_LAT + index * 0.0001, BASE_LON + index * 0.0001,
                start, start.plus(1, ChronoUnit.HOURS)), adminToken);

        assertThat(response.status()).isEqualTo(201);
        return response.text("$.id");
    }

    private String login() {
        var response = post("/api/v1/auth/login", """
                {"email":"admin@test.local","password":"admin-pass-123"}""", null);
        assertThat(response.status()).isEqualTo(200);
        return response.text("$.accessToken");
    }
}
