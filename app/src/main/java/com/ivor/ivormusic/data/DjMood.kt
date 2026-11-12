package com.ivor.ivormusic.data

/*
 * The DJ's color, read from the words in a mix's title.
 *
 * The DJ names every mix it builds ("Sunny Day Drive", "Midnight Rain"),
 * and those names say what the mix feels like. The DJ's tile on Home and
 * its page take a color from the first word that names a mood, so a
 * sunny mix is a yellow-orange block and a rainy one a blue one. A title
 * with no mood word in it has no hue here, and the caller falls back to
 * the first track's cover.
 *
 * Hues are HCT hue angles, the ones Rondo's colors are built from.
 */

private val MOODS: List<Pair<Double, Set<String>>> = listOf(
    // Sunlight: yellow-orange.
    65.0 to setOf(
        "sun", "sunny", "sunshine", "summer", "golden", "gold", "bright", "morning", "sunrise",
        "beach", "happy", "joy", "daylight",
    ),
    // Heat and energy: red-orange.
    32.0 to setOf("fire", "heat", "hot", "hype", "energy", "party", "workout", "gym", "rage", "pump", "blaze", "fuel"),
    // Evening warmth: amber.
    50.0 to setOf("autumn", "fall", "sunset", "dusk", "cozy", "coffee", "warm", "amber", "honey", "golden-hour"),
    // Love: pink.
    0.0 to setOf("love", "romance", "romantic", "heart", "hearts", "crush", "sweet", "valentine", "kiss"),
    // Water and cold: blue.
    250.0 to setOf(
        "rain", "rainy", "storm", "stormy", "blue", "blues", "sad", "melancholy", "ocean", "sea", "wave",
        "waves", "water", "cold", "winter", "ice", "snow", "tears",
    ),
    // Night: indigo.
    285.0 to setOf(
        "night", "nights", "midnight", "late", "moon", "moonlight", "dream", "dreams", "dreamy", "sleep",
        "dark", "nocturnal", "stars", "starlight",
    ),
    // Ease: teal.
    190.0 to setOf(
        "chill", "chilled", "calm", "lofi", "relax", "relaxing", "focus", "study", "zen", "mellow", "soft",
        "easy", "breeze", "unwind", "slow",
    ),
    // Outdoors: green.
    140.0 to setOf("forest", "nature", "green", "spring", "garden", "earth", "acoustic", "folk", "fresh", "road", "trip"),
    // Lights and synths: magenta.
    330.0 to setOf("neon", "electric", "synth", "synthwave", "cyber", "retro", "disco", "club", "rave"),
    // Groove: purple.
    305.0 to setOf("groove", "grooves", "funk", "funky", "soul", "jazz", "velvet", "smooth", "rnb"),
)

/**
 * The hue of the first mood word in [title], reading left to right, or
 * null when no word in it names a mood. "Lo-fi" and "lo fi" read as
 * "lofi", and "R&B" as "rnb".
 */
fun djMoodHue(title: String?): Double? {
    if (title.isNullOrBlank()) return null
    val words = title.lowercase()
        .replace("lo-fi", "lofi").replace("lo fi", "lofi")
        .replace("r&b", "rnb").replace("golden hour", "golden-hour")
        .split(Regex("[^a-z-]+"))
        .filter { it.isNotEmpty() }
    for (word in words) {
        MOODS.firstOrNull { (_, names) -> word in names }?.let { return it.first }
    }
    return null
}
