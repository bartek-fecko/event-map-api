package pl.eventmap.geo.app;

import pl.eventmap.common.ApiException;

/**
 * Viewport of the map, WGS84 degrees. The antimeridian is not supported:
 * minLon must be less than maxLon.
 */
public record BoundingBox(double minLon, double minLat, double maxLon, double maxLat) {

    public static BoundingBox parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("INVALID_BBOX", "bbox is required: minLon,minLat,maxLon,maxLat");
        }
        String[] parts = raw.split(",");
        if (parts.length != 4) {
            throw ApiException.badRequest("INVALID_BBOX", "bbox must have 4 comma-separated numbers");
        }
        try {
            double minLon = Double.parseDouble(parts[0].trim());
            double minLat = Double.parseDouble(parts[1].trim());
            double maxLon = Double.parseDouble(parts[2].trim());
            double maxLat = Double.parseDouble(parts[3].trim());
            return of(minLon, minLat, maxLon, maxLat);
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("INVALID_BBOX", "bbox must contain numbers only");
        }
    }

    public static BoundingBox of(double minLon, double minLat, double maxLon, double maxLat) {
        if (minLon < -180 || maxLon > 180 || minLat < -90 || maxLat > 90) {
            throw ApiException.badRequest("INVALID_BBOX", "bbox coordinates are out of range");
        }
        if (minLon >= maxLon || minLat >= maxLat) {
            throw ApiException.badRequest("INVALID_BBOX", "bbox min values must be smaller than max values");
        }
        return new BoundingBox(minLon, minLat, maxLon, maxLat);
    }
}
