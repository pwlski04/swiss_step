package com.example.stepmap_v10.tracking

import android.location.Location
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class MovementType { STILL, WALKING, RUNNING, BIKING, TRANSPORT }

fun MovementType.slowRanking(): Int {
    return when (this){
        MovementType.STILL -> 1000
        MovementType.WALKING -> 0
        MovementType.RUNNING -> 1
        MovementType.BIKING -> 2
        MovementType.TRANSPORT -> 3
    }
}

@Serializable
class MovementClassifier {
    /* TODO OPTIONAL: Use machine learning to classify movement */

    @Transient
    private val recentSpeedsKmh = ArrayDeque<Float>()

    private var currentType: MovementType = MovementType.STILL

    private var walkingLikeCount = 0
    private var transportLikeCount = 0
    private var bikingLikeCount = 0


    @Transient
    private var lastLocation: Location? = null

    fun classify(location: Location): MovementType {
        val speedKmh = location.speed * 3.6f

        recentSpeedsKmh.addLast(speedKmh)

        if (TrackingLiveState.isForegroundTracking.value && recentSpeedsKmh.size > 10 || !TrackingLiveState.isForegroundTracking.value && recentSpeedsKmh.size > 4) {
            recentSpeedsKmh.removeFirst()
        }

        val averageSpeedKmh = recentSpeedsKmh.average().toFloat()
        val maxSpeedKmh = recentSpeedsKmh.maxOrNull() ?: speedKmh

        val instantType = classifyInstantly(averageSpeedKmh, maxSpeedKmh)

        updateState(instantType)

        return currentType
    }


    private fun classifyInstantly(averageSpeedKmh: Float, maxSpeedKmh: Float): MovementType {
        return when {
            maxSpeedKmh >= 30f -> MovementType.TRANSPORT
            averageSpeedKmh >= 18f -> MovementType.TRANSPORT
            averageSpeedKmh >= 10f -> MovementType.BIKING
            averageSpeedKmh >= 7f -> MovementType.RUNNING
            averageSpeedKmh >= 1f -> MovementType.WALKING
            else -> MovementType.STILL
        }
    }

    private fun updateState(instantType: MovementType) {
        when (instantType) {
            MovementType.TRANSPORT -> {
                transportLikeCount++
                walkingLikeCount = 0
                bikingLikeCount = 0
            }

            MovementType.BIKING -> {
                bikingLikeCount++
                walkingLikeCount = 0
                transportLikeCount = 0
            }

            MovementType.WALKING -> {
                walkingLikeCount++
                transportLikeCount = 0
                bikingLikeCount = 0
            }

            MovementType.RUNNING -> {
                walkingLikeCount = 0
                transportLikeCount = 0
                bikingLikeCount = 0
            }

            MovementType.STILL -> {
                /* DO NOT RESET, BECAUSE STILL DOESN'T MEAN NOT MOVING */
            }
        }

        when (currentType) {
            MovementType.TRANSPORT -> {
                /* Stay in transport until we see several walking-like updates. 10 updates = about 30 seconds. */
                if (walkingLikeCount >= 10) {
                    currentType = MovementType.WALKING
                    walkingLikeCount = 0
                }
            }

            MovementType.BIKING -> {
                if (walkingLikeCount >= 8) {
                    currentType = MovementType.WALKING
                    walkingLikeCount = 0
                }

                if (transportLikeCount >= 3) {
                    currentType = MovementType.TRANSPORT
                    transportLikeCount = 0
                }
            }

            else -> {
                if (transportLikeCount >= 3) {
                    currentType = MovementType.TRANSPORT
                    transportLikeCount = 0
                } else if (bikingLikeCount >= 4) {
                    currentType = MovementType.BIKING
                    bikingLikeCount = 0
                } else if (walkingLikeCount >= 3) {
                    currentType = MovementType.WALKING
                    walkingLikeCount = 0
                } else if (instantType == MovementType.STILL) {
                    currentType = MovementType.STILL
                }
            }
        }
    }
}