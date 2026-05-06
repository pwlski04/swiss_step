package com.example.stepMap_v10.map

import android.content.Context
import android.util.Log
import com.example.stepMap_v10.paths.LastMatchedPosition
import com.example.stepMap_v10.paths.Path
import com.example.stepMap_v10.paths.PathPoint
import com.example.stepMap_v10.paths.SegmentProgress
import com.example.stepMap_v10.paths.loadWalkedSegments
import com.example.stepMap_v10.paths.progressOnSegment
import com.example.stepMap_v10.paths.saveWalkedSegments
import com.example.stepMap_v10.tracking.MovementType
import com.example.stepMap_v10.tracking.isSlowerThanOrEqual

object PathRecorder {
    private var segmentIndex: SegmentGridIndex? = null
    private var lastMatchedPosition: LastMatchedPosition? = null

    private val partialProgress = mutableMapOf<String, SegmentProgress>()

    fun setSegmentIndex(index: SegmentGridIndex?) {
        segmentIndex = index

        Log.d(
            "StepByStep_v1.0_TAG",
            "PathRecorder segment index set: ${index != null}"
        )
    }

    fun reset() {
        lastMatchedPosition = null
        partialProgress.clear()
    }

    fun recordLocation(
        context: Context,
        point: PathPoint,
        movementType: MovementType
    ) {
        val index = segmentIndex

        if (index == null) {
            Log.d(
                "StepByStep_v1.0_TAG",
                "PathRecorder skipped: segmentIndex is null"
            )
            return
        }

        val walkedSegments = loadWalkedSegments(context)

        val currSegment = index.findClosest(
            point,
            maxDistanceMeters = 10.0
        ) ?: return

        if (movementType != MovementType.TRANSPORT && !currSegment.path.walkable) {
            return
        }

        if (movementType == MovementType.TRANSPORT && !currSegment.path.drivable) {
            return
        }

        val currentProgress = progressOnSegment(
            point = point,
            segment = currSegment
        )

        val currentPosition = LastMatchedPosition(
            pathId = currSegment.path.id,
            segmentIndex = currSegment.segmentIndex,
            progress = currentProgress
        )

        recordGapBetweenMatches(
            context = context,
            path = currSegment.path,
            from = lastMatchedPosition,
            to = currentPosition,
            walkedSegments = walkedSegments,
            movementType = movementType
        )

        lastMatchedPosition = currentPosition
    }
    private fun recordGapBetweenMatches(
        context: Context,
        path: Path,
        from: LastMatchedPosition?,
        to: LastMatchedPosition,
        walkedSegments: MutableMap<String, MovementType>,
        movementType: MovementType
    ) {
        if (from == null || from.pathId != to.pathId) {
            recordProgressOnSegment(
                path = path,
                segmentIndex = to.segmentIndex,
                minProgress = to.progress,
                maxProgress = to.progress,
                movementType = movementType
            )
            return
        }

        val fromSegment = from.segmentIndex
        val toSegment = to.segmentIndex

        if (fromSegment == toSegment) {
            recordProgressOnSegment(
                path = path,
                segmentIndex = toSegment,
                minProgress = minOf(from.progress, to.progress),
                maxProgress = maxOf(from.progress, to.progress),
                movementType = movementType
            )

            maybeCompleteSegment(
                context = context,
                path = path,
                segmentIndex = toSegment,
                walkedSegments = walkedSegments,
                movementType = movementType
            )

            return
        }

        if (toSegment > fromSegment) {
            recordProgressOnSegment(
                path = path,
                segmentIndex = fromSegment,
                minProgress = from.progress,
                maxProgress = 1.0,
                movementType = movementType
            )

            maybeCompleteSegment(
                context = context,
                path = path,
                segmentIndex = fromSegment,
                walkedSegments = walkedSegments,
                movementType = movementType
            )

            for (segmentIndex in fromSegment + 1 until toSegment) {
                completeSegmentOnly(
                    context = context,
                    path = path,
                    segmentIndex = segmentIndex,
                    walkedSegments = walkedSegments,
                    movementType = movementType
                )
            }

            recordProgressOnSegment(
                path = path,
                segmentIndex = toSegment,
                minProgress = 0.0,
                maxProgress = to.progress,
                movementType = movementType
            )

            maybeCompleteSegment(
                context = context,
                path = path,
                segmentIndex = toSegment,
                walkedSegments = walkedSegments,
                movementType = movementType
            )
        } else {
            recordProgressOnSegment(
                path = path,
                segmentIndex = fromSegment,
                minProgress = 0.0,
                maxProgress = from.progress,
                movementType = movementType
            )

            maybeCompleteSegment(
                context = context,
                path = path,
                segmentIndex = fromSegment,
                walkedSegments = walkedSegments,
                movementType = movementType
            )

            for (segmentIndex in toSegment + 1 until fromSegment) {
                completeSegmentOnly(
                    context = context,
                    path = path,
                    segmentIndex = segmentIndex,
                    walkedSegments = walkedSegments,
                    movementType = movementType
                )
            }

            recordProgressOnSegment(
                path = path,
                segmentIndex = toSegment,
                minProgress = to.progress,
                maxProgress = 1.0,
                movementType = movementType
            )

            maybeCompleteSegment(
                context = context,
                path = path,
                segmentIndex = toSegment,
                walkedSegments = walkedSegments,
                movementType = movementType
            )
        }
    }

    private fun recordProgressOnSegment(
        path: Path,
        segmentIndex: Int,
        minProgress: Double,
        maxProgress: Double,
        movementType: MovementType
    ) {
        val segmentId = "${path.id}:$segmentIndex"

        val oldProgress = partialProgress[segmentId]

        val safeMin = minProgress.coerceIn(0.0, 1.0)
        val safeMax = maxProgress.coerceIn(0.0, 1.0)

        val newProgress =
            if (oldProgress == null) {
                SegmentProgress(
                    minProgress = minOf(safeMin, safeMax),
                    maxProgress = maxOf(safeMin, safeMax),
                    movementType = movementType
                )
            } else {
                SegmentProgress(
                    minProgress = minOf(oldProgress.minProgress, safeMin, safeMax),
                    maxProgress = maxOf(oldProgress.maxProgress, safeMin, safeMax),
                    movementType = movementType
                )
            }

        partialProgress[segmentId] = newProgress
    }

    private fun maybeCompleteSegment(
        context: Context,
        path: Path,
        segmentIndex: Int,
        walkedSegments: MutableMap<String, MovementType>,
        movementType: MovementType
    ) {
        val segmentId = "${path.id}:$segmentIndex"
        val progress = partialProgress[segmentId] ?: return

        val isAlmostComplete =
            progress.minProgress <= 0.05 &&
                    progress.maxProgress >= 0.95

        if (!isAlmostComplete) return

        completeSegmentOnly(
            context = context,
            path = path,
            segmentIndex = segmentIndex,
            walkedSegments = walkedSegments,
            movementType = movementType
        )

        partialProgress.remove(segmentId)
    }

    private fun completeSegmentOnly(
        context: Context,
        path: Path,
        segmentIndex: Int,
        walkedSegments: MutableMap<String, MovementType>,
        movementType: MovementType
    ) {
        val segmentId = "${path.id}:$segmentIndex"

        val oldMovementType = walkedSegments[segmentId]

        if (oldMovementType != null) {
            val newTypeIsSlower = isSlowerThanOrEqual(
                movementType,
                oldMovementType
            )

            if (!newTypeIsSlower) {
                return
            }
        }

        walkedSegments[segmentId] = movementType
        saveWalkedSegments(context, walkedSegments)

        Log.d(
            "StepByStep_v1.0_TAG",
            "Saved walked segment: $segmentId movement=$movementType"
        )
    }
}