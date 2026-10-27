package pl.eventmap.common;

import java.util.List;

/**
 * Keyset pagination envelope. {@code nextCursor} is null on the last page.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {
}
