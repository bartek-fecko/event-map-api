package pl.eventmap.geo.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MapDtos {

    private MapDtos() {
    }

    public record MapEventPoint(
            UUID id,
            String title,
            Instant startsAt,
            double lat,
            double lon,
            String coverImageUrl
    ) {
    }

    /** A grid cell with at least one upcoming event. */
    public record MapCluster(double lat, double lon, long count) {
    }

    /**
     * Exactly one of {@code events} / {@code clusters} is populated:
     * {@code mode} says which, so the client never guesses.
     */
    public record MapResponse(
            String mode,
            List<MapEventPoint> events,
            List<MapCluster> clusters,
            boolean truncated
    ) {
        public static MapResponse ofEvents(List<MapEventPoint> events, boolean truncated) {
            return new MapResponse("events", events, List.of(), truncated);
        }

        public static MapResponse ofClusters(List<MapCluster> clusters, boolean truncated) {
            return new MapResponse("clusters", List.of(), clusters, truncated);
        }
    }
}
