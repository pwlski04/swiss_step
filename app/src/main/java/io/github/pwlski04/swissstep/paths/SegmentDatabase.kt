package io.github.pwlski04.swissstep.paths

import android.database.sqlite.SQLiteDatabase

data class SegmentRow(
    val id: Long,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val highway: String,
    val walkable: Boolean,
    val drivable: Boolean
)

/*
Abstraction over "give me every segment touching this cell range", so SegmentIndex
can be unit-tested against a plain in-memory fake without needing a real SQLite
file or an Android runtime.
*/
interface SegmentSource {
    fun querySegmentsInCellRange(
        minCellX: Int, maxCellX: Int,
        minCellY: Int, maxCellY: Int
    ): List<SegmentRow>
}

/*
Read-only query layer over the bundled switzerland_paths.db (built offline by
tools/process_paths.py). Segments are pre-split and pre-indexed by the same
grid-cell scheme SegmentIndex uses at runtime, so windowed loading is just a
range query over indexed integer columns — no SQLite R-tree module required
(its availability isn't guaranteed across all Android OEM builds).
*/
class SegmentDatabase(dbPath: String) : SegmentSource {

    private val db: SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

    override fun querySegmentsInCellRange(
        minCellX: Int, maxCellX: Int,
        minCellY: Int, maxCellY: Int
    ): List<SegmentRow> {
        /*
        A segment spanning multiple cells has one row per cell in segment_cells, so
        the join can return the same segment id more than once - dedupe here rather
        than let the caller construct a second Segment instance for an already-seen
        row (SegmentIndex relies on there being exactly one Segment object per id).
        */
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT s.id, s.start_lat, s.start_lon, s.end_lat, s.end_lon,
                   s.highway, s.walkable, s.drivable
            FROM segment_cells sc
            JOIN segments s ON s.id = sc.segment_id
            WHERE sc.cell_x BETWEEN ? AND ? AND sc.cell_y BETWEEN ? AND ?
            """.trimIndent(),
            arrayOf(minCellX.toString(), maxCellX.toString(), minCellY.toString(), maxCellY.toString())
        )

        val result = mutableListOf<SegmentRow>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(
                    SegmentRow(
                        id = it.getLong(0),
                        startLat = it.getDouble(1),
                        startLon = it.getDouble(2),
                        endLat = it.getDouble(3),
                        endLon = it.getDouble(4),
                        highway = it.getString(5),
                        walkable = it.getInt(6) != 0,
                        drivable = it.getInt(7) != 0
                    )
                )
            }
        }
        return result
    }

    fun close() {
        db.close()
    }
}
