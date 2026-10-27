package pl.eventmap.seed;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.eventmap.auth.domain.User;
import pl.eventmap.auth.domain.UserRepository;
import pl.eventmap.category.domain.Category;
import pl.eventmap.category.domain.CategoryRepository;
import pl.eventmap.city.domain.City;
import pl.eventmap.city.domain.CityRepository;
import pl.eventmap.config.AppProperties;
import pl.eventmap.event.domain.Event;
import pl.eventmap.event.domain.EventRepository;
import pl.eventmap.venue.domain.Venue;
import pl.eventmap.venue.domain.VenueRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Development-only demo data, enabled by the local profile and never by
 * production (it is not a Flyway migration for exactly that reason).
 *
 * Real Kraków venues and plausible programming, so the map has something to
 * draw the first time the frontend runs.
 */
@Component
@Profile("local")
@Order(2)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private record Seed(String title, String description, String venue, List<String> categories,
                        int[] plusDays, int hour, int durationHours) {
    }

    private final VenueRepository venues;
    private final EventRepository events;
    private final CityRepository cities;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final AppProperties properties;
    private final Clock clock;

    public DevDataSeeder(VenueRepository venues, EventRepository events, CityRepository cities,
                         CategoryRepository categories, UserRepository users, AppProperties properties,
                         Clock clock) {
        this.venues = venues;
        this.events = events;
        this.cities = cities;
        this.categories = categories;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (venues.count() > 0) {
            log.info("Demo data already present, skipping seeding");
            return;
        }

        User organizer = users.findByEmailIgnoreCase(properties.admin().bootstrapEmail()).orElse(null);
        if (organizer == null) {
            log.warn("No administrator account, skipping demo data");
            return;
        }

        Map<String, City> cityBySlug = cities.findAll().stream()
                .collect(Collectors.toMap(City::getSlug, Function.identity()));
        Map<String, Category> categoryBySlug = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getSlug, Function.identity()));

        City krakow = cityBySlug.get("krakow");
        if (krakow == null) {
            log.warn("Kraków is missing from the city table, skipping demo data");
            return;
        }

        Map<String, Venue> venueByName = new LinkedHashMap<>();
        venueByName.put("Klub Studio", venue("Klub Studio", "ul. Budryka 4", 50.0672, 19.9065, krakow));
        venueByName.put("Tauron Arena", venue("Tauron Arena Kraków", "al. Pokoju 1", 50.0679, 19.9910, krakow));
        venueByName.put("ICE", venue("ICE Kraków", "ul. Marii Konopnickiej 17", 50.0478, 19.9315, krakow));
        venueByName.put("Forum", venue("Forum Przestrzenie", "ul. Marii Konopnickiej 28", 50.0455, 19.9345, krakow));
        venueByName.put("Plac Nowy", venue("Plac Nowy", "Kazimierz", 50.0515, 19.9448, krakow));
        venueByName.put("Teatr Ludowy", venue("Teatr Ludowy", "os. Teatralne 34", 50.0755, 20.0341, krakow));
        venueByName.put("Muzeum Narodowe", venue("Muzeum Narodowe", "al. 3 Maja 1", 50.0606, 19.9236, krakow));
        venueByName.put("Cheder", venue("Cheder", "ul. Józefa 36", 50.0499, 19.9467, krakow));
        venueByName.put("Bunkier Sztuki", venue("Bunkier Sztuki", "pl. Szczepański 3a", 50.0636, 19.9342, krakow));
        venueByName.put("Błonia", venue("Błonia Krakowskie", "Błonia", 50.0615, 19.9100, krakow));
        venueByName.put("Bazylika Mariacka", venue("Bazylika Mariacka", "pl. Mariacki 5", 50.0617, 19.9393, krakow));
        venueByName.put("Bulwar", venue("Bulwar Czerwieński", "Bulwar Czerwieński", 50.0540, 19.9350, krakow));

        List<Seed> seeds = List.of(
                new Seed("Kraków Jazz Autumn: Kowalski Quartet",
                        "Modern Polish jazz, one night only.", "Klub Studio", List.of("music"),
                        new int[]{2}, 20, 2),
                new Seed("AI in Production Meetup",
                        "Three short talks on shipping LLM features, then networking.", "Cheder", List.of("tech", "networking"),
                        new int[]{5}, 18, 3),
                new Seed("Vistula Half Marathon",
                        "21 km along the river, start and finish on Błonia.", "Błonia", List.of("sport"),
                        new int[]{10}, 9, 4),
                new Seed("Night of Museums: Late Opening",
                        "The permanent gallery stays open until midnight.", "Muzeum Narodowe", List.of("art"),
                        new int[]{14}, 18, 5),
                new Seed("Kazimierz Food Tour",
                        "Six stops, six tastings, one very full afternoon.", "Plac Nowy", List.of("food"),
                        new int[]{1}, 12, 4),
                new Seed("Hamlet",
                        "A new staging of the tragedy in Nowa Huta.", "Teatr Ludowy", List.of("theatre"),
                        new int[]{7}, 19, 3),
                new Seed("Founders Breakfast Kraków",
                        "Early coffee for people building companies.", "ICE", List.of("networking"),
                        new int[]{3}, 9, 2),
                new Seed("Bunker Sessions: Techno Night",
                        "Three rooms, six DJs, sunrise finish.", "Forum", List.of("nightlife", "music"),
                        new int[]{2}, 23, 7),
                new Seed("Polish Poster Art: A Retrospective",
                        "Fifty years of the Polish School of Posters.", "Bunkier Sztuki", List.of("art"),
                        new int[]{6}, 11, 8),
                new Seed("Startup Pitch Night",
                        "Eight teams, five minutes each, one winner.", "ICE", List.of("tech", "networking"),
                        new int[]{9}, 18, 4),
                new Seed("Sunday Organ Concert",
                        "Weekly recital on the great organ.", "Bazylika Mariacka", List.of("music"),
                        new int[]{4, 11, 18}, 15, 1),
                new Seed("Wianki: Midsummer on the Vistula",
                        "Open-air concert and the traditional wreath ceremony.", "Bulwar", List.of("music", "nightlife"),
                        new int[]{20}, 19, 6),
                new Seed("Board Game Night",
                        "Bring nothing, play everything. Beginners welcome.", "Cheder", List.of("networking"),
                        new int[]{3}, 18, 4),
                new Seed("Street Food Festival",
                        "Forty trucks from across Małopolska.", "Błonia", List.of("food"),
                        new int[]{8}, 12, 10),
                new Seed("Electronic Music Workshop",
                        "Modular synths, hands-on, limited to 20 people.", "Klub Studio", List.of("music", "tech"),
                        new int[]{13}, 17, 3),
                new Seed("Kraków Derby: Wisła vs Cracovia",
                        "The oldest rivalry in Polish football.", "Tauron Arena", List.of("sport"),
                        new int[]{16}, 17, 2),
                new Seed("Documentary Film Weekend",
                        "Four screenings and a Q&A with the directors.", "Muzeum Narodowe", List.of("theatre"),
                        new int[]{12, 13}, 17, 4),
                new Seed("Winter Market Opening",
                        "Pierogi, mulled wine and a brass band on the square.", "Plac Nowy", List.of("food", "music"),
                        new int[]{21}, 11, 9),
                new Seed("Design Systems Workshop",
                        "Tokens, components and docs that stay in sync.", "ICE", List.of("tech"),
                        new int[]{14}, 10, 6),
                new Seed("Sunrise Yoga on the River",
                        "Vinyasa for all levels, bring a mat.", "Bulwar", List.of("sport"),
                        new int[]{6, 13}, 7, 1));

        Instant now = clock.instant();
        for (Seed seed : seeds) {
            publish(organizer, venueByName, categoryBySlug, seed, now);
        }

        // One submission left in the moderation queue, so the admin flow is
        // visible immediately after a fresh start.
        Event pending = Event.create("Silent Disco on the Roof",
                "Three channels, wireless headphones, sunset above Kazimierz.",
                organizer, venueByName.get("Forum"), null, now);
        startDate(2, 21).ifPresent(start -> pending.addOccurrence(start, start.plus(Duration.ofHours(4))));
        pending.addCategory(categoryBySlug.get("nightlife"));
        pending.submit();
        events.save(pending);

        log.info("Seeded {} venues and {} events for local development", venues.count(), events.count());
    }

    private void publish(User organizer, Map<String, Venue> venueByName,
                         Map<String, Category> categoryBySlug, Seed seed, Instant now) {
        Event event = Event.create(seed.title(), seed.description(), organizer,
                venueByName.get(seed.venue()), null, now);

        for (int plusDays : seed.plusDays()) {
            Instant start = startDate(plusDays, seed.hour()).orElseThrow();
            event.addOccurrence(start, start.plus(Duration.ofHours(seed.durationHours())));
        }
        seed.categories().forEach(slug -> event.addCategory(categoryBySlug.get(slug)));
        event.submit();
        event.publish();
        events.save(event);
    }

    private java.util.Optional<Instant> startDate(int plusDays, int hour) {
        LocalDate date = LocalDate.now(clock).plusDays(plusDays);
        return java.util.Optional.of(date.atTime(hour, 0).atZone(WARSAW).toInstant());
    }

    private Venue venue(String name, String address, double lat, double lon, City city) {
        Point point = WGS84.createPoint(new Coordinate(lon, lat));
        point.setSRID(4326);
        return venues.save(Venue.create(name, address, city, point, clock.instant()));
    }
}
