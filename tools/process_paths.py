"""
Dev-machine build tool — NOT bundled into the app. Consumes a Switzerland OSM PBF
extract (e.g. https://download.geofabrik.de/europe/switzerland-latest.osm.pbf) and
emits switzerland_paths.db, a SQLite database of pre-segmented, grid-indexed
walking/biking/transport paths for the app's SegmentIndex to query on demand.

Usage:
    pip install osmium
    python process_paths.py switzerland-latest.osm.pbf switzerland_paths.db

The output schema mirrors io.github.pwlski04.swissstep.paths.SegmentIndex's runtime
grid-cell scheme exactly (see CELL_SIZE_DEGREES / cells_for_bbox), so the Kotlin
loader can page segments in by simple indexed-column range queries instead of
relying on SQLite's optional (not universally available on Android) R-tree module.
"""

import sqlite3
import sys

import osmium

CELL_SIZE_DEGREES = 0.001
BATCH_SIZE = 8000

wanted = {
    # walking:
    "path",
    "steps",
    "track",

    # streets:
    "living_street",
    "residential",
    "service",
    "unclassified",
    "tertiary",
    "tertiary_link",

    # bigger roads for transport:
    "secondary",
    "secondary_link",
    "primary",
    "primary_link",
    "trunk",
    "trunk_link",

    # tram tracks if mapped separately:
    "tram",
    "rail",
    "light_rail",
    "subway",
}

walkable_streets = {
    "footway",
    "path",
    "steps",
    "track",
    "living_street",
    "residential",
    "service",
    "unclassified",
    "tertiary",
    "tertiary_link",
}

drivable_streets = {
    "residential",
    "service",
    "unclassified",
    "tertiary",
    "tertiary_link",
    "secondary",
    "secondary_link",
    "primary",
    "primary_link",
    "trunk",
    "trunk_link",
}


def classify_path(tags, highway):
    walkable = highway in walkable_streets
    drivable = highway in drivable_streets

    # Explicit walking bans
    if tags.get("foot") == "no":
        walkable = False

    # Private access
    if tags.get("access") == "private":
        walkable = False
        drivable = False

    return walkable, drivable


def cell_index(lat, lon):
    return int(lat / CELL_SIZE_DEGREES), int(lon / CELL_SIZE_DEGREES)


def cells_for_bbox(lat1, lon1, lat2, lon2):
    """Every grid cell the segment's bounding box overlaps (not just the 4 corners —
    a segment whose bbox spans multiple cells must be registered in all of them, or
    a nearbySegments() query against a "middle" cell would silently miss it)."""
    x1, y1 = cell_index(min(lat1, lat2), min(lon1, lon2))
    x2, y2 = cell_index(max(lat1, lat2), max(lon1, lon2))
    for cx in range(x1, x2 + 1):
        for cy in range(y1, y2 + 1):
            yield cx, cy


def create_schema(conn):
    conn.executescript(
        """
        CREATE TABLE segments (
            id INTEGER PRIMARY KEY,
            start_lat REAL NOT NULL,
            start_lon REAL NOT NULL,
            end_lat REAL NOT NULL,
            end_lon REAL NOT NULL,
            highway TEXT NOT NULL,
            walkable INTEGER NOT NULL,
            drivable INTEGER NOT NULL
        );
        CREATE TABLE segment_cells (
            cell_x INTEGER NOT NULL,
            cell_y INTEGER NOT NULL,
            segment_id INTEGER NOT NULL,
            PRIMARY KEY (cell_x, cell_y, segment_id)
        ) WITHOUT ROWID;
        """
    )


class SegmentHandler(osmium.SimpleHandler):
    def __init__(self, conn):
        super().__init__()
        self.conn = conn
        self.next_segment_id = 0
        self.segment_rows = []
        self.cell_rows = []
        self.ways_seen = 0

    def way(self, w):
        highway = w.tags.get("highway")
        if highway not in wanted:
            # Railways (rail/tram/light_rail/subway) are tagged via the separate
            # "railway" key in OSM, not "highway".
            highway = w.tags.get("railway")
        if highway not in wanted:
            return

        walkable, drivable = classify_path(w.tags, highway)

        points = [(n.lat, n.lon) for n in w.nodes if n.location.valid()]
        if len(points) < 2:
            return

        for (lat1, lon1), (lat2, lon2) in zip(points, points[1:]):
            seg_id = self.next_segment_id
            self.next_segment_id += 1
            self.segment_rows.append(
                (seg_id, lat1, lon1, lat2, lon2, highway, int(walkable), int(drivable))
            )
            for cx, cy in cells_for_bbox(lat1, lon1, lat2, lon2):
                self.cell_rows.append((cx, cy, seg_id))

        self.ways_seen += 1
        if len(self.segment_rows) >= BATCH_SIZE:
            self.flush()

        if self.ways_seen % 50000 == 0:
            print(f"  ...{self.ways_seen} ways processed, {self.next_segment_id} segments so far")

    def flush(self):
        if not self.segment_rows:
            return
        self.conn.executemany(
            "INSERT INTO segments VALUES (?, ?, ?, ?, ?, ?, ?, ?)", self.segment_rows
        )
        self.conn.executemany(
            "INSERT INTO segment_cells VALUES (?, ?, ?)", self.cell_rows
        )
        self.conn.commit()
        self.segment_rows.clear()
        self.cell_rows.clear()


def main():
    if len(sys.argv) != 3:
        print(f"Usage: python {sys.argv[0]} <input.osm.pbf> <output.db>")
        sys.exit(1)

    pbf_path, db_path = sys.argv[1], sys.argv[2]

    conn = sqlite3.connect(db_path)
    create_schema(conn)

    handler = SegmentHandler(conn)
    print(f"Reading {pbf_path} ...")
    handler.apply_file(pbf_path, locations=True)
    handler.flush()
    conn.commit()

    print(f"Done: {db_path} ({handler.next_segment_id} segments)")
    conn.close()


if __name__ == "__main__":
    main()
