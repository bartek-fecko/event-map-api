package pl.eventmap.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One concrete date/time of an event. The {@code time_range} column is a
 * GENERATED tsrange maintained by PostGIS/Postgres; JPA never writes it.
 */
@Entity
@Table(name = "event_occurrence")
public class EventOccurrence {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OccurrenceStatus status;

    protected EventOccurrence() {
    }

    static EventOccurrence create(Event event, Instant startsAt, Instant endsAt) {
        EventOccurrence occurrence = new EventOccurrence();
        occurrence.id = UUID.randomUUID();
        occurrence.event = event;
        occurrence.startsAt = startsAt;
        occurrence.endsAt = endsAt;
        occurrence.status = OccurrenceStatus.SCHEDULED;
        return occurrence;
    }

    public UUID getId() {
        return id;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public OccurrenceStatus getStatus() {
        return status;
    }

    public boolean isScheduled() {
        return status == OccurrenceStatus.SCHEDULED;
    }
}
