package pl.eventmap.event.app;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.eventmap.common.CursorCodec;
import pl.eventmap.common.CursorPage;
import pl.eventmap.event.api.EventDtos.EventDetail;
import pl.eventmap.event.api.EventDtos.EventSummary;
import pl.eventmap.event.api.EventDtos.ModerationItem;
import pl.eventmap.event.api.EventDtos.MyEventItem;
import pl.eventmap.event.api.EventDtos.OccurrenceView;
import pl.eventmap.event.api.EventDtos.PageResponse;
import pl.eventmap.event.api.EventDtos.VenueView;
import pl.eventmap.event.domain.Event;
import pl.eventmap.event.domain.EventOccurrence;
import pl.eventmap.event.domain.EventRepository;
import pl.eventmap.event.domain.EventStatus;
import pl.eventmap.event.domain.OccurrenceStatus;
import pl.eventmap.common.ApiException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class EventReadService {

    /**
     * Read model over the occurrence keyset. Deliberately raw SQL: the list is
     * the hottest query in the app and its shape is the API contract.
     */
    private static final String LIST_SQL = """
            SELECT e.id                AS event_id,
                   o.id                AS occurrence_id,
                   e.title,
                   e.cover_image_url,
                   o.starts_at,
                   ST_Y(v.location::geometry) AS lat,
                   ST_X(v.location::geometry) AS lon,
                   city.name           AS city_name,
                   (SELECT string_agg(c.slug, ',' ORDER BY c.slug)
                      FROM event_category ec
                      JOIN category c ON c.id = ec.category_id
                     WHERE ec.event_id = e.id) AS category_slugs
              FROM event e
              JOIN venue v ON v.id = e.venue_id
              JOIN city ON city.id = v.city_id
              JOIN event_occurrence o ON o.event_id = e.id
             WHERE e.status = 'PUBLISHED'
               AND o.status = 'SCHEDULED'
               AND o.starts_at >= :now
               AND (CAST(:fromTs AS timestamptz) IS NULL OR o.starts_at >= CAST(:fromTs AS timestamptz))
               AND (CAST(:toTs AS timestamptz) IS NULL OR o.starts_at <= CAST(:toTs AS timestamptz))
               AND (CAST(:citySlug AS text) IS NULL OR city.slug = CAST(:citySlug AS text))
               AND (CAST(:categorySlug AS text) IS NULL OR EXISTS (
                        SELECT 1
                          FROM event_category ec2
                          JOIN category c2 ON c2.id = ec2.category_id
                         WHERE ec2.event_id = e.id AND c2.slug = CAST(:categorySlug AS text)))
               AND (CAST(:cursorStart AS timestamptz) IS NULL
                    OR (o.starts_at, o.id) > (CAST(:cursorStart AS timestamptz), CAST(:cursorId AS uuid)))
             ORDER BY o.starts_at, o.id
             LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final EventRepository events;
    private final Clock clock;

    public EventReadService(NamedParameterJdbcTemplate jdbc, EventRepository events, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    public CursorPage<EventSummary> list(String citySlug, String categorySlug, Instant from, Instant to,
                                         String cursor, int limit) {
        CursorCodec.Position position = cursor == null ? null : CursorCodec.decode(cursor);

        var params = new MapSqlParameterSource()
                .addValue("now", toDatabase(clock.instant()))
                .addValue("fromTs", toDatabase(from))
                .addValue("toTs", toDatabase(to))
                .addValue("citySlug", citySlug)
                .addValue("categorySlug", categorySlug)
                .addValue("cursorStart", position == null ? null : toDatabase(position.startsAt()))
                .addValue("cursorId", position == null ? null : position.id())
                .addValue("limit", limit + 1);

        List<EventSummary> rows = jdbc.query(LIST_SQL, params, EventReadService::mapSummary);

        boolean hasMore = rows.size() > limit;
        List<EventSummary> items = hasMore ? List.copyOf(rows.subList(0, limit)) : rows;
        String nextCursor = null;
        if (hasMore) {
            EventSummary last = items.getLast();
            nextCursor = CursorCodec.encode(last.startsAt(), last.occurrenceId());
        }
        return new CursorPage<>(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public EventDetail detail(UUID eventId, boolean includeUnpublished) {
        Event event = events.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "Event does not exist"));

        if (!includeUnpublished && event.getStatus() != EventStatus.PUBLISHED) {
            throw ApiException.notFound("EVENT_NOT_FOUND", "Event does not exist");
        }
        return toDetail(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<ModerationItem> moderation(EventStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<Event> result = status == null
                ? events.findAllWithVenue(pageable)
                : events.findByStatus(status, pageable);

        List<ModerationItem> items = result.getContent().stream()
                .map(EventReadService::toModerationItem)
                .toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<MyEventItem> mine(UUID organizerId) {
        return events.findByOrganizerIdOrderByCreatedAtDesc(organizerId).stream()
                .map(event -> new MyEventItem(
                        event.getId(),
                        event.getTitle(),
                        event.getStatus().name(),
                        event.getRejectionReason(),
                        event.getCreatedAt(),
                        firstStartsAt(event)))
                .toList();
    }

    private static EventSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new EventSummary(
                rs.getObject("event_id", UUID.class),
                rs.getObject("occurrence_id", UUID.class),
                rs.getString("title"),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                rs.getString("city_name"),
                splitSlugs(rs.getString("category_slugs")),
                rs.getString("cover_image_url"));
    }

    private static EventDetail toDetail(Event event) {
        var venue = event.getVenue();
        return new EventDetail(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStatus().name(),
                event.getRejectionReason(),
                event.getCoverImageUrl(),
                event.getOrganizer().getDisplayName(),
                new VenueView(
                        venue.getId(),
                        venue.getName(),
                        venue.getAddress(),
                        venue.getCity().getName(),
                        venue.getLocation().getY(),
                        venue.getLocation().getX()),
                event.getCategories().stream().map(c -> c.getSlug()).sorted().toList(),
                event.getOccurrences().stream()
                        .filter(occurrence -> occurrence.getStatus() != OccurrenceStatus.CANCELLED)
                        .sorted(Comparator.comparing(EventOccurrence::getStartsAt))
                        .map(occurrence -> new OccurrenceView(
                                occurrence.getId(),
                                occurrence.getStartsAt(),
                                occurrence.getEndsAt(),
                                occurrence.getStatus().name()))
                        .toList(),
                event.getCreatedAt());
    }

    private static ModerationItem toModerationItem(Event event) {
        return new ModerationItem(
                event.getId(),
                event.getTitle(),
                event.getStatus().name(),
                event.getOrganizer().getEmail(),
                event.getVenue().getName(),
                event.getVenue().getCity().getName(),
                event.getCreatedAt(),
                firstStartsAt(event));
    }

    private static Instant firstStartsAt(Event event) {
        return event.getOccurrences().stream()
                .map(EventOccurrence::getStartsAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static OffsetDateTime toDatabase(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static List<String> splitSlugs(String slugs) {
        return slugs == null || slugs.isBlank() ? List.of() : List.of(slugs.split(","));
    }
}
