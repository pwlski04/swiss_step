import json

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

    #bigger roads for transport:
    "secondary",
    "secondary_link",
    "primary",
    "primary_link",
    "trunk",
    "trunk_link",

    # tram tracks if mapped separately:
    "tram",
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


""" DEFINITIONS """
def simplify_points(points, keep_every=2):
    if len(points) <= 2:
        return points

    simplified = [points[0]]

    for i in range(1, len(points) - 1):
        if i % keep_every == 0:
            simplified.append(points[i])

    simplified.append(points[-1])
    return simplified


def classify_path(props):
    highway = props.get("highway")

    walkable = highway in walkable_streets
    drivable = highway in drivable_streets

    """
    # Do not use mapped sidewalks as separate logical paths.
    # The street centerline should represent walking on either sidewalk.
    if highway == "footway" and props.get("footway") == "sidewalk":
        return None
    """

    # Explicit walking bans
    if props.get("foot") == "no":
        walkable = False

    # Private access
    if props.get("access") == "private":
        walkable = False
        drivable = False

    return walkable, drivable


""" MAIN CODE STARTS HERE """

with open("zurich.geojson", "r", encoding="utf-8") as f:
    data = json.load(f)

paths = []

for feature in data["features"]:
    props = feature.get("properties", {})
    highway = props.get("highway")

    if highway not in wanted:
        continue

    classification = classify_path(props)
    if classification is None:
            continue
    walkable, drivable = classification

    geom = feature.get("geometry", {})
    geom_type = geom.get("type")
    coords = geom.get("coordinates")

    if geom_type == "LineString":
        paths.append({
            "id": len(paths),
            "points": coords,
            "highway": highway,
            "walkable": walkable,
            "drivable": drivable
        })

    elif geom_type == "MultiLineString":
        for line in coords:
            paths.append({
                "id": len(paths),
                "points": line, # was coords,
                "highway": highway,
                "walkable": walkable,
                "drivable": drivable
            })

with open("utilized_paths_0.json", "w", encoding="utf-8") as f:
    json.dump(paths, f)
