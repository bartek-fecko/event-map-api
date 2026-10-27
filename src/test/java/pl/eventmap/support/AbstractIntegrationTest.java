package pl.eventmap.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Real PostGIS, real HTTP, real JWT — no mocked geo layer. The container is
 * static so every test class in the JVM shares one instance (~1 startup).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final GenericContainer<?> POSTGIS =
            new GenericContainer<>(DockerImageName.parse("postgis/postgis:17-3.5"))
                    .withEnv("POSTGRES_DB", "eventmap")
                    .withEnv("POSTGRES_USER", "eventmap")
                    .withEnv("POSTGRES_PASSWORD", "eventmap")
                    .withExposedPorts(5432)
                    .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        POSTGIS.start();
    }

    @DynamicPropertySource
    static void postgisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/eventmap"
                .formatted(POSTGIS.getHost(), POSTGIS.getMappedPort(5432)));
    }

    @Autowired
    protected Environment environment;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    protected record ApiResponse(int status, String body, String contentType) {

        public String text(String pointer) {
            return JsonPath.read(body, pointer);
        }

        public Number number(String pointer) {
            return JsonPath.read(body, pointer);
        }

        public Boolean bool(String pointer) {
            return JsonPath.read(body, pointer);
        }

        public List<String> list(String pointer) {
            return JsonPath.read(body, pointer);
        }
    }

    protected String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port");
    }

    protected ApiResponse get(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    protected ApiResponse post(String path, String json, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private ApiResponse send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ApiResponse(response.statusCode(), response.body(),
                    response.headers().firstValue("Content-Type").orElse(""));
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP call failed: " + request.uri(), ex);
        }
    }
}
