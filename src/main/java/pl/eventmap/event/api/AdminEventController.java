package pl.eventmap.event.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.eventmap.auth.app.AuthenticatedUser;
import pl.eventmap.event.api.EventDtos.CreateEventRequest;
import pl.eventmap.event.api.EventDtos.CreatedResponse;
import pl.eventmap.event.api.EventDtos.EventDetail;
import pl.eventmap.event.api.EventDtos.ModerationItem;
import pl.eventmap.event.api.EventDtos.PageResponse;
import pl.eventmap.event.api.EventDtos.RejectRequest;
import pl.eventmap.event.app.EventReadService;
import pl.eventmap.event.app.EventWriteService;
import pl.eventmap.event.domain.EventStatus;

import java.util.UUID;

/** Back office. Guarded by {@code hasRole('ADMIN')} in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/events")
@Validated
public class AdminEventController {

    private final EventReadService readService;
    private final EventWriteService writeService;

    public AdminEventController(EventReadService readService, EventWriteService writeService) {
        this.readService = readService;
        this.writeService = writeService;
    }

    @GetMapping
    public PageResponse<ModerationItem> queue(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return readService.moderation(status, page, size);
    }

    @GetMapping("/{id}")
    public EventDetail detail(@PathVariable UUID id) {
        return readService.detail(id, true);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse create(@AuthenticationPrincipal AuthenticatedUser admin,
                                  @Valid @RequestBody CreateEventRequest request) {
        return new CreatedResponse(writeService.createPublished(admin, request));
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable UUID id) {
        writeService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        writeService.reject(id, request.reason());
    }
}
