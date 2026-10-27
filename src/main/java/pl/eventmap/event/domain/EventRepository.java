package pl.eventmap.event.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByOrganizerIdOrderByCreatedAtDesc(UUID organizerId);

    @EntityGraph(attributePaths = {"venue", "venue.city", "organizer"})
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"venue", "venue.city", "organizer"})
    @Query("select e from Event e order by e.createdAt desc")
    Page<Event> findAllWithVenue(Pageable pageable);
}
