package pl.eventmap.city.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.eventmap.city.domain.City;
import pl.eventmap.city.domain.CityRepository;

import java.util.Comparator;
import java.util.List;

/** Powers the "pick a city" control and the map's initial viewport. */
@RestController
@RequestMapping("/api/v1/cities")
public class CityController {

    public record CityView(String slug, String name) {
        static CityView of(City city) {
            return new CityView(city.getSlug(), city.getName());
        }
    }

    private final CityRepository cities;

    public CityController(CityRepository cities) {
        this.cities = cities;
    }

    @GetMapping
    public List<CityView> all() {
        return cities.findAll().stream()
                .sorted(Comparator.comparing(City::getName))
                .map(CityView::of)
                .toList();
    }
}
