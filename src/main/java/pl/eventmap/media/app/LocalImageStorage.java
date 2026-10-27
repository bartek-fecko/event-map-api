package pl.eventmap.media.app;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.eventmap.common.ApiException;
import pl.eventmap.config.AppProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class LocalImageStorage implements ImageStorage {

    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final Path root;
    private final String publicBaseUrl;

    public LocalImageStorage(AppProperties properties) {
        this.root = properties.media().storageDir().toAbsolutePath().normalize();
        this.publicBaseUrl = properties.media().publicBaseUrl();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create media directory " + root, ex);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Uploaded file is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest("UNSUPPORTED_MEDIA_TYPE", "Only JPEG, PNG and WebP images are accepted");
        }

        String key = YearMonth.now(ZoneOffset.UTC).format(MONTH_PATH)
                + "/" + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("INVALID_PATH", "Invalid upload path");
        }

        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED", "Could not store the image");
        }

        return publicBaseUrl + "/" + key;
    }
}
