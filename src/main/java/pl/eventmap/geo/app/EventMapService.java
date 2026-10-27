package pl.eventmap.geo.app;

import org.springframework.stereotype.Service;
import pl.eventmap.config.AppProperties;
import pl.eventmap.geo.api.MapDtos.MapResponse;

import java.time.Clock;

@Service
public class EventMapService {

    private final EventMapRepository repository;
    private final AppProperties properties;
    private final Clock clock;

    public EventMapService(EventMapRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public MapResponse viewport(BoundingBox bbox, int zoom) {
        int limit = properties.map().maxResults();

        if (zoom >= properties.map().clusterMaxZoom()) {
            var events = repository.findEvents(bbox, clock.instant(), limit);
            return MapResponse.ofEvents(events, events.size() == limit);
        }

        var clusters = repository.findClusters(bbox, clock.instant(), limit, gridStep(zoom));
        return MapResponse.ofClusters(clusters, clusters.size() == limit);
    }

    /**
     * Cluster cell size in degrees, ~1/8 of a tile wide. Roughly 2 km at the
     * clustering zoom limit, which reads well at city scale.
     */
    static double gridStep(int zoom) {
        return Math.max(0.002, 360.0 / Math.pow(2, zoom) / 8.0);
    }
}
