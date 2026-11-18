package app.lychee.handwriting

/** What the handwriting pad asks of a recogniser: Google's (the companion app) or PP-OCR. */
interface Recognizer {
    /**
     * The best characters for [strokes] (each x, y pairs in screen coordinates), best first,
     * with their scores (0..1); only those [allowed] lets through.
     */
    fun recognise(strokes: List<FloatArray>, limit: Int = 12, allowed: (String) -> Boolean = { true }): List<Pair<String, Float>>

    fun close()
}
