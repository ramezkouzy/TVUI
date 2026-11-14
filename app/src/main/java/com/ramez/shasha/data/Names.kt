package com.ramez.shasha.data

/**
 * Light-touch cleanup of provider names for display. Deliberately conservative:
 * it removes clutter (country tags, quality tags, decorations, repeated series
 * names) but never rewrites the actual title.
 */
object Names {
    // "|AR| ", "[AR] ", "(EG) ", "AR - ", "AR: ", "AR| ", "AR-EG | " at the start
    private val countryPrefix = Regex(
        "^\\s*(?:[|\\[(]\\s*[A-Z]{2,3}(?:-[A-Z]{2,3})?\\s*[|\\])]\\s*[-:|]?\\s*|[A-Z]{2,3}(?:-[A-Z]{2,3})?\\s*[-:|]\\s+)+"
    )
    private val quality = Regex(
        "(?<![\\p{L}\\d])(4K|8K|UHD|FHD|HD|SD|HEVC|H\\.?265|H\\.?264|1080P|720P|60FPS|50FPS|HDR|RAW|VIP|BACKUP|MULTI|DUBBED|VOSTFR)(?![\\p{L}\\d])",
        RegexOption.IGNORE_CASE,
    )
    // superscripts/small caps (ᴴᴰ ⁴ᴷ), symbols providers sprinkle in (★ ◉ ✪ ▶ etc.)
    private val decorations = Regex("[\\u1D00-\\u1DBF\\u2070-\\u209F\\u02B0-\\u02FF\\u2600-\\u27BF\\u25A0-\\u25FF\\u2B00-\\u2BFF\\u2190-\\u21FF]")
    private val emptyBrackets = Regex("[\\[(]\\s*[\\])]")
    private val year = Regex("(?<!\\d)(19[3-9]\\d|20[0-4]\\d)(?!\\d)")
    private val yearInBrackets = Regex("[\\[(]\\s*(19[3-9]\\d|20[0-4]\\d)\\s*[\\])]")
    private val trailingYear = Regex("[\\s\\-|]+(19[3-9]\\d|20[0-4]\\d)\\s*$")
    private val spaces = Regex("\\s+")
    private val edgeJunk = Regex("^[\\s\\-|:._]+|[\\s\\-|:._]+$")
    private val smallWords = setOf("a", "an", "and", "as", "at", "by", "for", "in", "of", "on", "or", "the", "to", "with")

    private fun base(raw: String): String {
        var s = raw.replace(decorations, " ")
        s = s.replace(countryPrefix, "")
        s = s.replace(quality, " ")
        s = s.replace(emptyBrackets, " ")
        s = s.replace(spaces, " ").replace(edgeJunk, "")
        return titleCaseIfShouting(s)
    }

    /** "THE YACOUBIAN BUILDING" -> "The Yacoubian Building"; leaves mixed case and Arabic alone. */
    private fun titleCaseIfShouting(s: String): String {
        val latin = s.filter { it in 'A'..'Z' || it in 'a'..'z' }
        if (latin.length < 4 || latin.any { it.isLowerCase() }) return s
        return s.split(' ').mapIndexed { i, w ->
            val lower = w.lowercase()
            when {
                w.none { it in 'A'..'Z' } -> w                 // Arabic, numbers, symbols
                w.length <= 3 && lower !in smallWords -> w      // acronyms like MBC, TV
                i > 0 && lower in smallWords -> lower
                else -> lower.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
    }

    fun yearOf(raw: String): String = year.findAll(raw).lastOrNull()?.value ?: ""

    /** Movie or series title for display; the year is shown separately. */
    fun title(raw: String): String {
        var s = raw.replace(yearInBrackets, " ")
        s = base(s)
        s = s.replace(trailingYear, "")
        return s.replace(spaces, " ").replace(edgeJunk, "").ifBlank { raw.trim() }
    }

    fun channel(raw: String): String = base(raw).ifBlank { raw.trim() }

    fun category(raw: String): String = base(raw).ifBlank { raw.trim() }

    private val seasonEpisode = Regex("\\bS\\s?\\d{1,2}\\s?[-_. ]?E\\s?\\d{1,4}\\b", RegexOption.IGNORE_CASE)
    private val episodeWord = Regex("(?i:\\bep(?:isode)?\\.?\\s*\\d{1,4}\\b)|(?:الحلقة|حلقة)\\s*\\d{1,4}")
    private val seasonWord = Regex("(?i:\\bseason\\s*\\d{1,2}\\b)|(?:الموسم|موسم)\\s*\\S+")

    /**
     * Episode names often repeat the show name and the SxxEyy code
     * ("Bab Al Hara S01 E05"). Keep only a real episode title, or "Episode 5".
     */
    fun episode(raw: String, seriesTitle: String, number: Int): String {
        var s = base(raw)
        if (seriesTitle.isNotBlank()) s = s.replace(seriesTitle, " ", ignoreCase = true)
        s = s.replace(seasonEpisode, " ").replace(episodeWord, " ").replace(seasonWord, " ")
        s = s.replace(yearInBrackets, " ").replace(spaces, " ").replace(edgeJunk, "")
        val meaningful = s.any { it.isLetter() } && s.length > 1
        return if (meaningful) s else "Episode $number"
    }
}
