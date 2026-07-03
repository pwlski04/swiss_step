package io.github.pwlski04.swissstep.tracking

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MovementClassifierTest {

    @Before
    fun setUp() {
        // A stable, larger averaging window makes the state machine's thresholds deterministic.
        TrackingLiveState.isForegroundTracking.value = true
    }

    @After
    fun tearDown() {
        TrackingLiveState.isForegroundTracking.value = false
    }

    @Test
    fun `a fresh classifier reports STILL for a stationary reading`() {
        val classifier = MovementClassifier()

        assertEquals(MovementType.STILL, classifier.classifySpeedKmh(0f))
    }

    @Test
    fun `sustained high speed readings escalate to TRANSPORT`() {
        val classifier = MovementClassifier()

        var lastResult = MovementType.STILL
        repeat(3) { lastResult = classifier.classifySpeedKmh(40f) }

        assertEquals(MovementType.TRANSPORT, lastResult)
    }

    @Test
    fun `TRANSPORT only downgrades after sustained walking speed readings, not a single one`() {
        val classifier = MovementClassifier()
        repeat(3) { classifier.classifySpeedKmh(40f) }

        val afterOneWalkingReading = classifier.classifySpeedKmh(3f)

        assertEquals(MovementType.TRANSPORT, afterOneWalkingReading)
    }

    @Test
    fun `TRANSPORT downgrades to WALKING once walking speed readings dominate the window`() {
        val classifier = MovementClassifier()
        repeat(3) { classifier.classifySpeedKmh(40f) }

        var lastResult = MovementType.TRANSPORT
        repeat(30) { lastResult = classifier.classifySpeedKmh(3f) }

        assertEquals(MovementType.WALKING, lastResult)
    }
}
