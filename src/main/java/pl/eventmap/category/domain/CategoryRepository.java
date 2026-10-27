package pl.eventmap.category.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findBySlugIn(Collection<String> slugs);

    Optional<Category> findBySlug(String slug);

    List<Category> findAllByOrderByNameAsc();
}
