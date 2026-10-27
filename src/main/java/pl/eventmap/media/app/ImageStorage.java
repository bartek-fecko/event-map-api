package pl.eventmap.media.app;

import org.springframework.web.multipart.MultipartFile;

/**
 * Storage seam. The local implementation writes to a Docker volume; swapping in
 * S3-compatible storage later is one class and one config key.
 */
public interface ImageStorage {

    /** @return public URL, relative to the API host (e.g. /media/2026/10/uuid.jpg) */
    String store(MultipartFile file);
}
