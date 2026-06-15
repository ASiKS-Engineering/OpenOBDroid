package com.openobdroid.app

data class LinFrame(
    val name: String,
    val id: Int,
    val publisher: String,
    val size: Int,
    val signals: List<LinSignalMapping>
)

data class LinSignalMapping(
    val signalName: String,
    val offset: Int
)

data class LinSignal(
    val name: String,
    val size: Int,
    val defaultValue: Int,
    val publisher: String,
    val subscribers: List<String>
)

data class LdfData(
    val protocolVersion: String = "",
    val speed: Double = 19.2,
    val master: String = "",
    val slaves: List<String> = emptyList(),
    val signals: Map<String, LinSignal> = emptyMap(),
    val frames: Map<Int, LinFrame> = emptyMap()
)
