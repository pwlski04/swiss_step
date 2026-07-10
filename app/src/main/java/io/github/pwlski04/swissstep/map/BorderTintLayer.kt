package io.github.pwlski04.swissstep.map

import android.content.Context
import android.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.FillRule
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class BorderPoint(val lat: Double, val lon: Double)

@Serializable
private data class BorderData(val rings: List<List<BorderPoint>>)

/*
Tints everything outside Switzerland's political border a solid gray. The border polygon is static
(built once, offline, by tools/process_border.py from a Nominatim/OSM boundary lookup), and once
zoom levels' projections are cached.
*/
class BorderTintLayer(context: Context, tintColor: Int = LIGHT_TINT) : Layer() {
    private val rings: List<List<LatLong>>      // ring = polygon loop

    private val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(tintColor)
        setStyle(Style.FILL)
    }

    private val projectedRingsCache = ConcurrentHashMap<Byte, List<List<Pair<Double, Double>>>>()

    init {
        val json = context.assets.open("switzerland_border.json").bufferedReader().use { it.readText() }
        val data = borderJson.decodeFromString<BorderData>(json)
        rings = data.rings.map { ring -> ring.map { LatLong(it.lat, it.lon) } }
    }

    fun setTintColor(tintColor: Int) {
        paint.setColor(tintColor)
        requestRedraw()
    }

    companion object {
        val LIGHT_TINT: Int = Color.argb(255, 180, 180, 180)
        val DARK_TINT: Int = Color.argb(255, 8, 8, 8)
        private val borderJson = Json { ignoreUnknownKeys = true }
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        val dm = displayModel ?: return
        val projectedRings = projectedRingsCache.getOrPut(zoomLevel) { projectRings(zoomLevel, dm.tileSize) }

        val path = AndroidGraphicFactory.INSTANCE.createPath()

        // Outer contour covering the whole viewport, so EVEN_ODD fill treats everything except the ring(s) added below as "inside" the tinted region.
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        path.moveTo(0f, 0f)
        path.lineTo(w, 0f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()

        for (ring in projectedRings) {
            if (ring.isEmpty()) continue
            val (firstX, firstY) = ring[0]
            path.moveTo((firstX - topLeftPoint.x).toFloat(), (firstY - topLeftPoint.y).toFloat())
            for (i in 1 until ring.size) {
                val (x, y) = ring[i]
                path.lineTo((x - topLeftPoint.x).toFloat(), (y - topLeftPoint.y).toFloat())
            }
            path.close()
        }

        path.setFillRule(FillRule.EVEN_ODD)
        canvas.drawPath(path, paint)
    }

    private fun projectRings(zoomLevel: Byte, tileSize: Int): List<List<Pair<Double, Double>>> {
        val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)
        return rings.map { ring ->
            ring.map { latLong ->
                Pair(
                    MercatorProjection.longitudeToPixelX(latLong.longitude, mapSize),
                    MercatorProjection.latitudeToPixelY(latLong.latitude, mapSize)
                )
            }
        }
    }
}
