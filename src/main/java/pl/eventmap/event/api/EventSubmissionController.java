package pl.eventmap.event.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.eventmap.auth.app.AuthenticatedUser;
import pl.eventmap.event.api.EventDtos.CreateEventRequest;
import pl.eventmap.event.api.EventDtos.CreatedResponse;
import pl.eventmap.event.api.EventDtos.MyEventItem;
import pl.eventmap.event.app.EventReadService;
import pl.eventmap.event.app.EventWriteService;

import java.util.List;

/**
 * Organizer surface: submit an event (goes to PENDING for moderation) and
 * follow the status of your own submissions.
 */
@RestController
@RequestMapping("/api/v1")
public class EventSubmissionController {

    private final EventWriteService writeService;
    private final EventReadService readService;

    public EventSubmissionController(EventWriteService writeService, EventReadService readService) {
        this.writeService = writeService;
        this.readService = readService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse submit(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CreateEventRequest request) {
        return new CreatedResponse(writeService.submit(user, request));
    }

    @GetMapping("/me/events")
    public List<MyEventItem> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return readService.mine(user.id());
    }
}
