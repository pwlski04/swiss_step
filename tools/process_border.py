"""
Dev-machine build tool — NOT bundled into the app. Fetches Switzerland's real
political border from Nominatim (OpenStreetMap), simplifies it, and writes
switzerland_border.json for BorderTintLayer to draw a tint over everything
outside the border. Run once; the app only ever reads the static output file.

Usage:
    python process_border.py
"""

import json
import math
import urllib.request

NOMINATIM_URL = (
    "https://nominatim.openstreetmap.org/search"
    "?q=Switzerland&format=json&polygon_geojson=1&limit=1"
)
OUTPUT_PATH = "../app/src/main/assets/switzerland_border.json"
# Small, fixed tolerance (~4-5m) - this only drops near-redundant collinear
# points, it does not chase a target point count. A few thousand points is
# trivially cheap to project (cached per zoom) and draw, so there's no real
# reason to trade accuracy for a smaller file - a coarse tolerance here is
# very visible on-screen at street-level zoom, since the tint edge is exactly
# where users will be looking (right at the border).
SIMPLIFY_EPSILON_DEGREES = 0.00004
MAX_POINTS_SAFETY_CAP = 20000


def fetch_geojson():
    request = urllib.request.Request(
        NOMINATIM_URL,
        headers={"User-Agent": "swiss-step-dev-tool (one-time border fetch)"},
    )
    with urllib.request.urlopen(request) as response:
        results = json.load(response)
    if not results:
        raise RuntimeError("Nominatim returned no results for Switzerland")
    return results[0]["geojson"]


def flatten_rings(geojson):
    """Returns a flat list of rings (each a list of (lat, lon) tuples), regardless
    of whether the geometry is a Polygon or MultiPolygon. GeoJSON coordinates are
    [lon, lat] - swapped here to (lat, lon) for the rest of this script."""
    geom_type = geojson["type"]
    coordinates = geojson["coordinates"]

    if geom_type == "Polygon":
        polygons = [coordinates]
    elif geom_type == "MultiPolygon":
        polygons = coordinates
    else:
        raise RuntimeError(f"Unexpected geometry type: {geom_type}")

    rings = []
    for polygon in polygons:
        for ring in polygon:
            rings.append([(lat, lon) for lon, lat in ring])
    return rings


def perpendicular_distance(point, line_start, line_end):
    if line_start == line_end:
        return math.hypot(point[0] - line_start[0], point[1] - line_start[1])
    x, y = point
    x1, y1 = line_start
    x2, y2 = line_end
    numerator = abs((y2 - y1) * x - (x2 - x1) * y + x2 * y1 - y2 * x1)
    denominator = math.hypot(y2 - y1, x2 - x1)
    return numerator / denominator


def douglas_peucker(points, epsilon):
    if len(points) < 3:
        return points

    max_dist = 0.0
    max_index = 0
    for i in range(1, len(points) - 1):
        dist = perpendicular_distance(points[i], points[0], points[-1])
        if dist > max_dist:
            max_dist = dist
            max_index = i

    if max_dist > epsilon:
        left = douglas_peucker(points[: max_index + 1], epsilon)
        right = douglas_peucker(points[max_index:], epsilon)
        return left[:-1] + right
    return [points[0], points[-1]]


def simplify_ring(ring, epsilon):
    """Douglas-Peucker on a closed ring: an open polyline's start/end anchors would
    be degenerate here since a ring's first and last point are identical, so split
    the ring into two chains using two well-separated points as anchors, simplify
    each independently, then stitch back together."""
    points = ring[:-1] if ring[0] == ring[-1] else ring
    n = len(points)
    if n < 4:
        return ring

    mid = n // 2
    chain_a = points[: mid + 1]
    chain_b = points[mid:] + [points[0]]

    simplified_a = douglas_peucker(chain_a, epsilon)
    simplified_b = douglas_peucker(chain_b, epsilon)

    return simplified_a[:-1] + simplified_b[:-1]


def simplify_rings(rings, epsilon):
    return [simplify_ring(ring, epsilon) for ring in rings]


def total_points(rings):
    return sum(len(ring) for ring in rings)


def main():
    print("Fetching Switzerland border from Nominatim...")
    geojson = fetch_geojson()
    rings = flatten_rings(geojson)
    print(f"Fetched {len(rings)} ring(s), {total_points(rings)} raw points total")

    epsilon = SIMPLIFY_EPSILON_DEGREES
    simplified = simplify_rings(rings, epsilon)
    point_count = total_points(simplified)

    # Only loosen if something pathological happened (e.g. Nominatim returning
    # much higher-resolution data than expected) - this is a safety cap, not a
    # target to aim for, so accuracy is never sacrificed under normal conditions.
    attempts = 0
    while point_count > MAX_POINTS_SAFETY_CAP and attempts < 10:
        epsilon *= 1.5
        simplified = simplify_rings(rings, epsilon)
        point_count = total_points(simplified)
        attempts += 1

    print(f"Simplified to {len(simplified)} ring(s), {point_count} points total (epsilon={epsilon:.6f})")

    lats = [lat for ring in simplified for lat, lon in ring]
    lons = [lon for ring in simplified for lat, lon in ring]
    print(f"Bounding box: lat {min(lats):.4f}..{max(lats):.4f}, lon {min(lons):.4f}..{max(lons):.4f}")
    print("(Compare against SWITZERLAND_MIN/MAX_LAT/LON in MapUtils.kt: 45.8..47.9, 5.9..10.6)")

    output = {
        "rings": [
            [{"lat": round(lat, 5), "lon": round(lon, 5)} for lat, lon in ring]
            for ring in simplified
        ]
    }

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f)

    print(f"Done: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
