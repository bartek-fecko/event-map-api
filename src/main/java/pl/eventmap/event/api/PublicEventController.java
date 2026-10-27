package pl.eventmap.event.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.eventmap.common.CursorCodec;
import pl.eventmap.common.CursorPage;
import pl.eventmap.event.api.EventDtos.EventDetail;
import pl.eventmap.event.api.EventDtos.EventSummary;
import pl.eventmap.event.app.EventReadService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@Validated
public class PublicEventController {

    private final EventReadService readService;

    public PublicEventController(EventReadService readService) {
        this.readService = readService;
    }

    @GetMapping
    public CursorPage<EventSummary> list(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @Size(max = CursorCodec.MAX_LENGTH) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {

        return readService.list(city, category, from, to, cursor, limit);
    }

    @GetMapping("/{id}")
    public EventDetail detail(@PathVariable UUID id) {
        return readService.detail(id, false);
    }
}
