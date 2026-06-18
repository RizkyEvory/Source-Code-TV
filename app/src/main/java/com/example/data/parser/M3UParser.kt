package com.example.data.parser

import com.example.data.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

object M3UParser {

    /**
     * Parse stream or contents containing M3U format into list of Channel items.
     */
    fun parse(inputStream: InputStream, playlistId: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        var line: String? = reader.readLine()

        var name = ""
        var logoUrl: String? = null
        var group = "Uncategorized"
        var tvgName = ""
        
        var extInfIndex = 0

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                line = reader.readLine()
                continue
            }

            if (trimmed.startsWith("#EXTINF:")) {
                // Extract attributes
                val tvgIdAttr = extractAttribute(trimmed, "tvg-id") ?: ""
                tvgName = extractAttribute(trimmed, "tvg-name") ?: ""
                logoUrl = extractAttribute(trimmed, "tvg-logo")
                group = extractAttribute(trimmed, "group-title") ?: "Uncategorized"

                // Extract display name (typically the last part of #EXTINF line after the final comma)
                val commaIndex = trimmed.lastIndexOf(',')
                name = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    tvgName.ifEmpty { "Channel ${extInfIndex + 1}" }
                }

                if (name.isEmpty()) {
                    name = tvgName.ifEmpty { "Channel ${extInfIndex + 1}" }
                }
                extInfIndex++
            } else if (!trimmed.startsWith("#")) {
                // If we have a URL on a line
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtmp://") || trimmed.startsWith("rtsp://")) {
                    val finalName = if (name.isEmpty()) "Channel ${channels.size + 1}" else name
                    val channelId = UUID.nameUUIDFromBytes("$playlistId-$trimmed-${channels.size}".toByteArray()).toString()
                    channels.add(
                        Channel(
                            id = channelId,
                            name = finalName,
                            logoUrl = logoUrl?.replace("\"", "")?.replace("'", "")?.trim(),
                            group = group.replace("\"", "").replace("'", "").trim().ifEmpty { "Uncategorized" },
                            streamUrl = trimmed,
                            playlistId = playlistId,
                            isFavorite = false,
                            lastWatched = null,
                            sortOrder = channels.size
                        )
                    )
                    // Reset temp fields
                    name = ""
                    logoUrl = null
                    group = "Uncategorized"
                    tvgName = ""
                }
            }
            line = reader.readLine()
        }
        return channels
    }

    private fun extractAttribute(line: String, attributeName: String): String? {
        val marker = "$attributeName="
        val index = line.indexOf(marker)
        if (index == -1) return null

        val startQuote = index + marker.length
        if (startQuote >= line.length) return null

        val docQuote = line[startQuote]
        if (docQuote == '"' || docQuote == '\'') {
            val endQuote = line.indexOf(docQuote, startQuote + 1)
            if (endQuote != -1) {
                return line.substring(startQuote + 1, endQuote)
            }
        } else {
            // Unquoted string (until space or comma)
            var end = startQuote
            while (end < line.length && line[end] != ' ' && line[end] != ',') {
                end++
            }
            return line.substring(startQuote, end)
        }
        return null
    }
}
