package pl.eventmap.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque cursor over the (starts_at, occurrence_id) keyset ordering.
 * Opaque on purpose: the client must not build cursors itself.
 */
public final class CursorCodec {

    public static final int MAX_LENGTH = 128;

    private CursorCodec() {
    }

    public record Position(Instant startsAt, UUID id) {
    }

    public static String encode(Instant startsAt, UUID id) {
        String raw = startsAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Position decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator <= 0) {
                throw new IllegalArgumentException("missing separator");
            }
            return new Position(
                    Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("INVALID_CURSOR", "Cursor is not valid");
        }
    }
}
