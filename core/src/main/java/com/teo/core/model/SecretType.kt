package com.teo.core.model

/**
 * The kinds of protection code a parent can choose. All three are hashed the same way
 * (PinHasher) — a pattern is just its dot sequence encoded as text, see [PatternCodec].
 */
enum class SecretType {
    PIN, PASSWORD, PATTERN;

    /** Null when [value] is acceptable as a new code of this type, else why it isn't. */
    fun validate(value: String): String? = when (this) {
        PIN -> when {
            !value.all(Char::isDigit) -> "Только цифры"
            value.length < MIN_PIN_LENGTH -> "Не меньше $MIN_PIN_LENGTH цифр"
            else -> null
        }
        PASSWORD -> if (value.length < MIN_PASSWORD_LENGTH) "Не меньше $MIN_PASSWORD_LENGTH символов" else null
        PATTERN -> if (PatternCodec.decode(value).size < MIN_PATTERN_DOTS) "Соедините не меньше $MIN_PATTERN_DOTS точек" else null
    }

    companion object {
        const val MIN_PIN_LENGTH = 6
        const val MAX_PIN_LENGTH = 12
        const val MIN_PASSWORD_LENGTH = 6
        const val MIN_PATTERN_DOTS = 4

        fun from(name: String?): SecretType = entries.firstOrNull { it.name == name } ?: PIN
    }
}

/** A pattern as text: the 0..8 indices of the 3×3 grid in the order they were joined, e.g. "0-4-8". */
object PatternCodec {
    fun encode(dots: List<Int>): String = dots.joinToString("-")
    fun decode(value: String): List<Int> = value.split("-").mapNotNull { it.toIntOrNull() }
}
