package pl.eventmap.event.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventDtos {

    private EventDtos() {
    }

    public record VenueInput(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 300) String address,
            @NotBlank @Size(max = 80) String citySlug,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
    ) {
    }

    public record OccurrenceInput(
            @NotNull Instant startsAt,
            @NotNull Instant endsAt
    ) {
    }

    /**
     * Exactly one of {@code venueId} (an existing venue) or {@code venue}
     * (a new place to create) must be supplied; the service enforces it.
     */
    public record CreateEventRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            UUID venueId,
            @Valid VenueInput venue,
            @NotEmpty @Size(max = 20) List<@Valid OccurrenceInput> occurrences,
            @NotEmpty @Size(max = 6) List<@NotBlank String> categorySlugs,
            @Size(max = 500) String coverImageUrl
    ) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record VenueView(UUID id, String name, String address, String city, double lat, double lon) {
    }

    public record OccurrenceView(UUID id, Instant startsAt, Instant endsAt, String status) {
    }

    /** One row of the public list = one occurrence, not one event. */
    public record EventSummary(
            UUID eventId,
            UUID occurrenceId,
            String title,
            Instant startsAt,
            double lat,
            double lon,
            String city,
            List<String> categories,
            String coverImageUrl
    ) {
    }

    public record EventDetail(
            UUID id,
            String title,
            String description,
            String status,
            String rejectionReason,
            String coverImageUrl,
            String organizer,
            VenueView venue,
            List<String> categories,
            List<OccurrenceView> occurrences,
            Instant createdAt
    ) {
    }

    public record ModerationItem(
            UUID id,
            String title,
            String status,
            String organizerEmail,
            String venueName,
            String city,
            Instant createdAt,
            Instant firstStartsAt
    ) {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    }

    public record MyEventItem(
            UUID id,
            String title,
            String status,
            String rejectionReason,
            Instant createdAt,
            Instant firstStartsAt
    ) {
    }

    public record CreatedResponse(UUID id) {
    }
}
