package io.github.pwlski04.swissstep.chains

import io.github.pwlski04.swissstep.hiddenMovementTypes
import io.github.pwlski04.swissstep.paths.colorForMovementType
import io.github.pwlski04.swissstep.paths.strokeWidthComputer
import io.github.pwlski04.swissstep.tracking.MovementType
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.model.DisplayModel
import java.util.concurrent.ConcurrentHashMap


/* OVERLAY LAYER */

class PathOverlayLayer(private val pathStorage: PathStorage) : Layer() {
    var isDisplayed: Boolean = true
    private val paintCache = HashMap<Pair<MovementType, Byte>, Paint>()
    private val projectionCache =
        ConcurrentHashMap<Long, HashMap<Byte, List<Pair<Double, Double>>>>()

    var useCustomColors: Boolean = false


    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        /*
        Called by Mapsforge to render this layer for the current viewport. Draws each
        movement type's chains in a fixed layering order, re-projecting a chain's points
        to screen coordinates only when its zoom-level cache is missing or stale.
        */

        if (!isDisplayed) return

        val dm = displayModel ?: return
        val w = canvas.width.toDouble()
        val h = canvas.height.toDouble()

        for (movementType in DRAW_ORDER) {
            if(movementType in hiddenMovementTypes) continue

            val chains = synchronized(pathStorage) {
                pathStorage.chains[movementType]?.toList()
            } ?: continue
            if (chains.isEmpty()) continue
            val paint = getPaint(movementType, zoomLevel)

            for (chain in chains) {
                val zoomCache = projectionCache.getOrPut(chain.id) { HashMap() }
                if (chain.dirty || !zoomCache.containsKey(zoomLevel)) {
                    zoomCache.clear()
                    zoomCache[zoomLevel] = projectChain(chain, zoomLevel, dm)
                    chain.dirty = false
                }
                drawProjectedChain(zoomCache[zoomLevel]!!, topLeftPoint, canvas, paint, w, h)
            }
        }
    }

    private fun drawProjectedChain(
        mapPoints: List<Pair<Double, Double>>,
        topLeftPoint: Point,
        canvas: Canvas,
        paint: Paint,
        w: Double,
        h: Double
    ) {
        for (i in 0 until mapPoints.size - 1) {
            val (ax, ay) = mapPoints[i]
            val (bx, by) = mapPoints[i + 1]
            val x1 = ax - topLeftPoint.x
            val y1 = ay - topLeftPoint.y
            val x2 = bx - topLeftPoint.x
            val y2 = by - topLeftPoint.y
            if (!segmentIntersectsViewport(x1, y1, x2, y2, w, h)) continue
            canvas.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt(), paint)
        }
    }

    fun evictFromCache(chainId: Long) {
        /* Drops a chain's cached screen-space projection, e.g. after it's removed from storage or merged into another chain. */
        projectionCache.remove(chainId)
    }

    fun clearPaintCache(){
        paintCache.clear()
    }

    companion object {
        val DRAW_ORDER = listOf(
            MovementType.STILL,
            MovementType.TRANSPORT,
            MovementType.BIKING,
            MovementType.RUNNING,
            MovementType.WALKING,
        )
    }

    fun getPaint(movementType: MovementType, zoomLevel: Byte): Paint {
        /* Returns a cached Paint for this movement type/zoom combo, creating and caching one on first use. */
        return paintCache.getOrPut(Pair(movementType, zoomLevel)) {
            AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = colorForMovementType(movementType, useCustomColors)
                strokeWidth = strokeWidthComputer(zoomLevel.toFloat())
            }
        }
    }

    private fun projectChain(chain: PathChain, zoomLevel: Byte, dm: DisplayModel): List<Pair<Double, Double>> {
        /* Converts a chain's lat/lon points into pixel coordinates for the given zoom level, via Mercator projection. */
        val mapSize = MercatorProjection.getMapSize(zoomLevel, dm.tileSize)
        val points = synchronized(chain) { chain.points.toList() }
        return points.filterNotNull().map { latLong ->
            Pair(
                MercatorProjection.longitudeToPixelX(latLong.longitude, mapSize),
                MercatorProjection.latitudeToPixelY(latLong.latitude, mapSize)
            )
        }
    }

    private fun segmentIntersectsViewport(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        width: Double,
        height: Double
    ): Boolean {
        return maxOf(x1, x2) >= 0 && minOf(x1, x2) <= width &&
                maxOf(y1, y2) >= 0 && minOf(y1, y2) <= height
    }

    fun preProjectAll(chains: Map<MovementType, List<PathChain>>, zoomLevel: Byte) {
        /*
        Warms the projection cache for every chain at the given zoom level, so the first
        real draw() call at that zoom doesn't have to compute projections on demand.
        */

        val dm = displayModel ?: return  // not attached to map yet -> skip (thread-safe)
        for (chainList in chains.values) {
            for (chain in chainList) {
                val zoomCache = projectionCache.getOrPut(chain.id) { HashMap() }
                if (!zoomCache.containsKey(zoomLevel)) {
                    zoomCache[zoomLevel] = projectChain(chain, zoomLevel, dm)
                    chain.dirty = false
                }
            }
        }
    }
}