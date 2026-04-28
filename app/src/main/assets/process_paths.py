import json

wanted = {
    "footway",
    "path",
    "pedestrian",
    "steps",
    "living_street",
    "track",
}

with open("zurich.geojson", "r", encoding="utf-8") as f:
    data = json.load(f)

paths = []

for feature in data["features"]:
    props = feature.get("properties", {})
    highway = props.get("highway")

    if highway not in wanted:
        continue

    geom = feature.get("geometry", {})
    geom_type = geom.get("type")
    coords = geom.get("coordinates")

    if geom_type == "LineString":
        paths.append({
            "id": len(paths),
            "points": coords
        })

    elif geom_type == "MultiLineString":
        for line in coords:
            paths.append({
                "id": len(paths),
                "points": line
            })

with open("walking_paths.json", "w", encoding="utf-8") as f:
    json.dump(paths, f)
