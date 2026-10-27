package pl.eventmap.city.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * The {@code boundary} column (geography MultiPolygon) is intentionally not
 * mapped: it is only ever used by PostGIS queries, never by JPA.
 */
@Entity
@Table(name = "city")
public class City {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    protected City() {
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }
}
