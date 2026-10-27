package pl.eventmap.venue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;
import pl.eventmap.city.domain.City;

import java.time.Instant;
import java.util.UUID;

/**
 * A physical place. The location column is geography(Point,4326) in PostGIS,
 * so distance maths is in metres without casting.
 */
@Entity
@Table(name = "venue")
public class Venue {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 300)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(name = "location", nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Venue() {
    }

    public static Venue create(String name, String address, City city, Point location, Instant now) {
        Venue venue = new Venue();
        venue.id = UUID.randomUUID();
        venue.name = name.trim();
        venue.address = address.trim();
        venue.city = city;
        venue.location = location;
        venue.createdAt = now;
        return venue;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public City getCity() {
        return city;
    }

    public Point getLocation() {
        return location;
    }
}
