package com.example.lldc.util

object LrcFormatUtil {

    /**
     * Converts a QRC format (word-by-word timestamps in brackets) to the desired enhanced format.
     * Example Input:  `[00:01.250]我[00:01.441]不[00:01.644]为[00:02.245]`
     * Example Output: `[00:01.250]<00:01.250>我<00:01.441>不<00:01.644>为<00:02.245>`
     */
    fun convertToEnhancedLrc(lrcContent: String): String {
        val lines = lrcContent.lines()
        val result = StringBuilder()
        
        // Regex to capture a timestamp `[mm:ss.xxx]` and the following text part that doesn't contain `[`
        val qrcRegex = Regex("""\[(\d{2}:\d{2}\.\d{2,3})\]([^\[]*)""")
        // Regex for metadata tags like [ti:text] or [offset:0]
        val metaTagRegex = Regex("""\[([a-z]{2,8}):(.*)\]""")

        for (line in lines) {
            // Keep metadata lines as they are.
            if (metaTagRegex.matches(line)) {
                result.append(line).append('\n')
                continue
            }

            val matches = qrcRegex.findAll(line).toList()
            if (matches.isEmpty()) {
                result.append(line).append('\n') // Keep lines without timestamps (e.g., empty lines)
                continue
            }

            // The first timestamp in a QRC line is the line's start time.
            val lineStartTime = matches.first().groupValues[1]
            val enhancedLine = StringBuilder()
            enhancedLine.append("[$lineStartTime]")

            // Process all timestamp-text pairs in the line.
            for (match in matches) {
                val charTime = match.groupValues[1]
                val text = match.groupValues[2]

                // Append the timestamp.
                enhancedLine.append("<$charTime>")
                // Append the text if it exists. This ensures the final timestamp of the line is kept.
                if (text.isNotEmpty()) {
                    enhancedLine.append(text)
                }
            }
            
            result.append(enhancedLine.toString()).append('\n')
        }

        return result.toString().trim()
    }

    /**
     * Parses a time value string "mm:ss.xxx" into total seconds.
     */
    private fun parseTimeValue(timeValue: String): Double {
        val timePartsRegex = Regex("""(\d{2}):(\d{2})\.(\d{2,3})""")
        val match = timePartsRegex.find(timeValue) ?: return 0.0
        val (minutes, seconds, fractions) = match.destructured
        // Handle both 2-digit (centiseconds) and 3-digit (milliseconds) fractions.
        val fractionValue = if (fractions.length == 2) fractions.toDouble() * 10 else fractions.toDouble()
        return minutes.toDouble() * 60 + seconds.toDouble() + fractionValue / 1000.0
    }

    /**
     * Formats total seconds into a time value string "mm:ss.xxx".
     */
    private fun formatTimeValue(timeInSeconds: Double): String {
        val time = if (timeInSeconds < 0) 0.0 else timeInSeconds
        val minutes = (time / 60).toInt()
        val seconds = (time % 60).toInt()
        val millis = ((time - (minutes * 60) - seconds) * 1000).toInt()
        return String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }

    /**
     * Applies a time offset to an LRC string, handling both `[ ]` and `< >` timestamp formats.
     */
    fun applyOffsetToLrc(lrcContent: String, offsetInMillis: Int): String {
        if (offsetInMillis == 0) return lrcContent
        val offsetInSeconds = offsetInMillis / 1000.0

        // This regex finds any timestamp, capturing the bracket type and the time value inside.
        val timestampRegex = Regex("""([<\[])(\d{2}:\d{2}\.\d{2,3})([>\]])""")

        return timestampRegex.replace(lrcContent) { matchResult ->
            val (openingBracket, timeValue, closingBracket) = matchResult.destructured
            val originalTime = parseTimeValue(timeValue)
            val newTime = originalTime + offsetInSeconds
            val newTimeValue = formatTimeValue(newTime)
            "$openingBracket$newTimeValue$closingBracket"
        }
    }
} 