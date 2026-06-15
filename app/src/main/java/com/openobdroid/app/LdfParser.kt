package com.openobdroid.app

import java.io.File

class LdfParser {
    fun parse(content: String): LdfData {
        var protocolVersion = ""
        var speed = 19.2
        var master = ""
        val slaves = mutableListOf<String>()
        val signals = mutableMapOf<String, LinSignal>()
        val frames = mutableMapOf<Int, LinFrame>()

        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }

        var currentSection = ""
        
        for (line in lines) {
            if (line.startsWith("Protocol_version")) {
                protocolVersion = line.split("\"")[1]
                continue
            }
            if (line.startsWith("Speed")) {
                speed = line.split(":")[1].split("kbps")[0].trim().toDoubleOrNull() ?: 19.2
                continue
            }
            
            if (line.endsWith("{")) {
                currentSection = line.split("{")[0].trim()
                continue
            }
            if (line == "}") {
                currentSection = ""
                continue
            }

            when (currentSection) {
                "Nodes" -> {
                    if (line.startsWith("Master:")) {
                        master = line.split(":")[1].split(",")[0].trim()
                    } else if (line.startsWith("Slaves:")) {
                        slaves.addAll(line.split(":")[1].split(",").map { it.replace(";", "").trim() })
                    }
                }
                "Signals" -> {
                    // Format: SignalName: Size, DefaultValue, Publisher, Subscriber1, ...;
                    val parts = line.split(":")
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val data = parts[1].replace(";", "").split(",")
                        if (data.size >= 3) {
                            val size = data[0].trim().toIntOrNull() ?: 0
                            val defaultValue = data[1].trim().toIntOrNull() ?: 0
                            val publisher = data[2].trim()
                            val subscribers = data.drop(3).map { it.trim() }
                            signals[name] = LinSignal(name, size, defaultValue, publisher, subscribers)
                        }
                    }
                }
                "Frames" -> {
                    // Format: FrameName: ID, Publisher, Size { ... }
                    // Simple parser for single line frames or start of block
                    if (line.contains(":")) {
                        val parts = line.split(":")
                        val name = parts[0].trim()
                        val headerParts = parts[1].split("{")[0].trim().split(",")
                        if (headerParts.size >= 3) {
                            val id = headerParts[0].trim().let { if (it.startsWith("0x")) it.substring(2).toInt(16) else it.toInt() }
                            val publisher = headerParts[1].trim()
                            val size = headerParts[2].trim().toInt()
                            frames[id] = LinFrame(name, id, publisher, size, emptyList()) // signals parsed later if needed
                        }
                    }
                }
            }
        }

        return LdfData(protocolVersion, speed, master, slaves, signals, frames)
    }
}
