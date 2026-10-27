package pl.eventmap.media.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.eventmap.media.app.ImageStorage;

@RestController
@RequestMapping("/api/v1/admin/media")
public class MediaController {

    public record ImageResponse(String url) {
    }

    private final ImageStorage storage;

    public MediaController(ImageStorage storage) {
        this.storage = storage;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageResponse upload(@RequestPart("file") MultipartFile file) {
        return new ImageResponse(storage.store(file));
    }
}
