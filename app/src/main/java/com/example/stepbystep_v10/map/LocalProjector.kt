package com.example.stepbystep_v10.map

import kotlin.math.cos

class LocalProjector(originLat: Double) {
    private val latScale = 111_320.0
    private val lonScale = 111_320.0 * cos(Math.toRadians(originLat))

    fun x(lon: Double): Double = lon * lonScale
    fun y(lat: Double): Double = lat * latScale

    fun lat(y: Double): Double = y / latScale
    fun lon(x: Double): Double = x / lonScale
}