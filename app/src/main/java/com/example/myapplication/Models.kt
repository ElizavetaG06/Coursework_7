@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.example.myapplication

import kotlinx.serialization.Serializable

// Этот файл описывает структуру нашего JSON (схемы хореографии)
@Serializable
data class Scheme(
    val projectName: String,
    val videoPath: String,
    val createdAt: String,
    val settings: Settings,
    val dancers: List<Dancer>,
    val formations: List<Formation>
)

@Serializable
data class Settings(val speed: Float, val mirror: Boolean)

@Serializable
data class Dancer(
    val id: Int,
    val name: String,
    val color: String // Например, "#FF0000"
)

@Serializable
data class Formation(
    val id: Int,
    val name: String,
    val type: String,
    val startTime: Float,
    val endTime: Float,
    val points: List<Point>
)

@Serializable
data class Point(
    val dancerId: Int,
    val x: Float,
    val y: Float
)