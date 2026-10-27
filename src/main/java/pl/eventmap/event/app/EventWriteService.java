package pl.eventmap.event.app;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.eventmap.auth.app.AuthenticatedUser;
import pl.eventmap.auth.domain.User;
import pl.eventmap.auth.domain.UserRepository;
import pl.eventmap.category.domain.Category;
import pl.eventmap.category.domain.CategoryRepository;
import pl.eventmap.city.domain.City;
import pl.eventmap.city.domain.CityRepository;
import pl.eventmap.common.ApiException;
import pl.eventmap.event.api.EventDtos.CreateEventRequest;
import pl.eventmap.event.api.EventDtos.OccurrenceInput;
import pl.eventmap.event.api.EventDtos.VenueInput;
import pl.eventmap.event.domain.Event;
import pl.eventmap.event.domain.EventRepository;
import pl.eventmap.venue.domain.Venue;
import pl.eventmap.venue.domain.VenueRepository;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EventWriteService {

    /** WGS84. Coordinate order is (longitude, latitude) — JTS is x/y, not lat/lon. */
    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    private final EventRepository events;
    private final VenueRepository venues;
    private final CityRepository cities;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final Clock clock;

    public EventWriteService(EventRepository events, VenueRepository venues, CityRepository cities,
                             CategoryRepository categories, UserRepository users, Clock clock) {
        this.events = events;
        this.venues = venues;
        this.cities = cities;
        this.categories = categories;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public UUID submit(AuthenticatedUser actor, CreateEventRequest request) {
        Event event = assemble(actor, request);
        guardStatus(() -> event.submit());
        events.save(event);
        return event.getId();
    }

    @Transactional
    public UUID createPublished(AuthenticatedUser actor, CreateEventRequest request) {
        Event event = assemble(actor, request);
        guardStatus(event::publish);
        events.save(event);
        return event.getId();
    }

    @Transactional
    public void approve(UUID eventId) {
        Event event = require(eventId);
        guardStatus(event::publish);
    }

    @Transactional
    public void reject(UUID eventId, String reason) {
        Event event = require(eventId);
        guardStatus(() -> event.reject(reason));
    }

    private Event require(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "Event does not exist"));
    }

    private Event assemble(AuthenticatedUser actor, CreateEventRequest request) {
        User organizer = users.findById(actor.id())
                .orElseThrow(() -> ApiException.unauthorized("UNKNOWN_USER", "Account no longer exists"));

        Event event = Event.create(
                request.title(),
                request.description(),
                organizer,
                resolveVenue(request),
                request.coverImageUrl(),
                clock.instant());

        for (OccurrenceInput occurrence : request.occurrences()) {
            try {
                event.addOccurrence(occurrence.startsAt(), occurrence.endsAt());
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("INVALID_OCCURRENCE", ex.getMessage());
            }
        }

        attachCategories(event, request.categorySlugs());
        return event;
    }

    private Venue resolveVenue(CreateEventRequest request) {
        boolean hasExisting = request.venueId() != null;
        boolean hasNew = request.venue() != null;
        if (hasExisting == hasNew) {
            throw ApiException.badRequest("VENUE_REQUIRED",
                    "Provide exactly one of venueId or venue");
        }

        if (hasExisting) {
            return venues.findById(request.venueId())
                    .orElseThrow(() -> ApiException.notFound("VENUE_NOT_FOUND", "Venue does not exist"));
        }

        VenueInput input = request.venue();
        City city = cities.findBySlug(input.citySlug())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_CITY",
                        "No city with slug '%s'".formatted(input.citySlug())));

        Point point = WGS84.createPoint(new Coordinate(input.longitude(), input.latitude()));
        point.setSRID(4326);
        return venues.save(Venue.create(input.name(), input.address(), city, point, clock.instant()));
    }

    private void attachCategories(Event event, List<String> slugs) {
        Set<String> requested = new HashSet<>(slugs);
        List<Category> found = categories.findBySlugIn(requested);
        if (found.size() != requested.size()) {
            Set<String> missing = new HashSet<>(requested);
            found.forEach(category -> missing.remove(category.getSlug()));
            throw ApiException.badRequest("UNKNOWN_CATEGORY",
                    "Unknown categories: " + missing.stream().sorted().collect(Collectors.joining(", ")));
        }
        found.forEach(event::addCategory);
    }

    private void guardStatus(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException ex) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", ex.getMessage());
        }
    }
}
