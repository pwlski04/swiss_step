package com.example.stepMap_v10.paths

object PathFunctions {
    private val points = mutableListOf<PathPoint>()

    fun addPoint(point: PathPoint){
        points.add(point)
    }

    fun getAllPoints(): List<PathPoint>{
        return points.toList()
    }

    fun clear(){
        points.clear()
    }
}