package pl.eventmap.geo.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.eventmap.geo.api.MapDtos.MapResponse;
import pl.eventmap.geo.app.BoundingBox;
import pl.eventmap.geo.app.EventMapService;

/**
 * The endpoint the Next.js map calls on every moveend.
 *
 * <pre>
 * GET /api/v1/map/events?bbox=19.85,50.02,20.05,50.10&zoom=13
 *   zoom &gt;= app.map.cluster-max-zoom -> individual events
 *   zoom &lt;  app.map.cluster-max-zoom -> grid clusters with counts
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/map")
@Validated
public class MapController {

    private final EventMapService mapService;

    public MapController(EventMapService mapService) {
        this.mapService = mapService;
    }

    @GetMapping("/events")
    public MapResponse viewport(@RequestParam String bbox,
                                @RequestParam(defaultValue = "10") @Min(0) @Max(19) int zoom) {
        return mapService.viewport(BoundingBox.parse(bbox), zoom);
    }
}
