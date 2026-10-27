package pl.eventmap.geo.app;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.eventmap.geo.api.MapDtos.MapCluster;
import pl.eventmap.geo.api.MapDtos.MapEventPoint;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The map read model. All geo maths runs inside PostGIS:
 * the viewport filter uses the GiST index on venue.location, and
 * low-zoom clustering is a ST_SnapToGrid aggregate rather than a client loop.
 */
@Repository
public class EventMapRepository {

    private static final String VIEWPORT_FILTER = """
              AND v.location && CAST(ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326) AS geography)
              AND ST_Intersects(v.location::geometry,
                                ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
            """;

    /** One row per event, timed at its earliest upcoming occurrence in the viewport. */
    private static final String EVENTS_SQL = """
            SELECT e.id,
                   e.title,
                   e.cover_image_url,
                   min(o.starts_at) AS starts_at,
                   ST_Y(v.location::geometry) AS lat,
                   ST_X(v.location::geometry) AS lon
              FROM event e
              JOIN venue v ON v.id = e.venue_id
              JOIN event_occurrence o ON o.event_id = e.id
             WHERE e.status = 'PUBLISHED'
               AND o.status = 'SCHEDULED'
               AND o.starts_at >= :now
            """ + VIEWPORT_FILTER + """
             GROUP BY e.id, e.title, e.cover_image_url, v.location
             ORDER BY starts_at, e.id
             LIMIT :limit
            """;

    /** Events bucketed onto a zoom-dependent grid, ordered by density. */
    private static final String CLUSTERS_SQL = """
            SELECT ST_Y(ST_Centroid(cell)) AS lat,
                   ST_X(ST_Centroid(cell)) AS lon,
                   count(*)                AS event_count
              FROM (SELECT ST_SnapToGrid(v.location::geometry, :step) AS cell
                      FROM event e
                      JOIN venue v ON v.id = e.venue_id
                      JOIN event_occurrence o ON o.event_id = e.id
                     WHERE e.status = 'PUBLISHED'
                       AND o.status = 'SCHEDULED'
                       AND o.starts_at >= :now
            """ + VIEWPORT_FILTER + """
                     GROUP BY e.id, v.location) cells
             GROUP BY cell
             ORDER BY event_count DESC, cell
             LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public EventMapRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MapEventPoint> findEvents(BoundingBox bbox, Instant now, int limit) {
        var params = viewport(bbox, now).addValue("limit", limit);
        return jdbc.query(EVENTS_SQL, params, (rs, rowNum) -> new MapEventPoint(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("title"),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                rs.getString("cover_image_url")));
    }

    public List<MapCluster> findClusters(BoundingBox bbox, Instant now, int limit, double stepDegrees) {
        var params = viewport(bbox, now)
                .addValue("limit", limit)
                .addValue("step", stepDegrees);
        return jdbc.query(CLUSTERS_SQL, params, (rs, rowNum) -> new MapCluster(
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                rs.getLong("event_count")));
    }

    private static MapSqlParameterSource viewport(BoundingBox bbox, Instant now) {
        return new MapSqlParameterSource()
                .addValue("minLon", bbox.minLon())
                .addValue("minLat", bbox.minLat())
                .addValue("maxLon", bbox.maxLon())
                .addValue("maxLat", bbox.maxLat())
                .addValue("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }
}
