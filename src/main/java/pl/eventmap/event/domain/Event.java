package pl.eventmap.event.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import pl.eventmap.auth.domain.User;
import pl.eventmap.category.domain.Category;
import pl.eventmap.venue.domain.Venue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The event as a concept (title, description, venue). When it happens lives in
 * {@link EventOccurrence}; a one-off event has exactly one occurrence.
 */
@Entity
@Table(name = "event")
public class Event {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "event_category",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new LinkedHashSet<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<EventOccurrence> occurrences = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() {
    }

    public static Event create(String title, String description, User organizer, Venue venue,
                               String coverImageUrl, Instant now) {
        Event event = new Event();
        event.id = UUID.randomUUID();
        event.title = title.trim();
        event.description = description;
        event.organizer = organizer;
        event.venue = venue;
        event.coverImageUrl = coverImageUrl;
        event.status = EventStatus.DRAFT;
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    public void addOccurrence(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Occurrence must end after it starts");
        }
        occurrences.add(EventOccurrence.create(this, startsAt, endsAt));
        touch();
    }

    public void addCategory(Category category) {
        categories.add(category);
        touch();
    }

    public void submit() {
        requireStatus(EventStatus.DRAFT, "Only a draft can be submitted");
        status = EventStatus.PENDING;
        touch();
    }

    public void publish() {
        if (status == EventStatus.PUBLISHED || status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Event is already " + status.name().toLowerCase());
        }
        if (occurrences.isEmpty()) {
            throw new IllegalStateException("An event needs at least one occurrence");
        }
        status = EventStatus.PUBLISHED;
        rejectionReason = null;
        touch();
    }

    public void reject(String reason) {
        if (status != EventStatus.PENDING) {
            throw new IllegalStateException("Only a pending event can be rejected");
        }
        status = EventStatus.REJECTED;
        rejectionReason = reason;
        touch();
    }

    private void requireStatus(EventStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public EventStatus getStatus() {
        return status;
    }

    public User getOrganizer() {
        return organizer;
    }

    public Venue getVenue() {
        return venue;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public List<EventOccurrence> getOccurrences() {
        return occurrences;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
