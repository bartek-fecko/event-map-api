package pl.eventmap.category.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.eventmap.category.domain.Category;
import pl.eventmap.category.domain.CategoryRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    public record CategoryView(UUID id, String slug, String name, String color, String icon) {
        static CategoryView of(Category category) {
            return new CategoryView(category.getId(), category.getSlug(), category.getName(),
                    category.getColor(), category.getIcon());
        }
    }

    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<CategoryView> all() {
        return categories.findAllByOrderByNameAsc().stream().map(CategoryView::of).toList();
    }
}
