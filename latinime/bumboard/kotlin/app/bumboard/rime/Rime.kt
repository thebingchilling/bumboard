package app.bumboard.rime

/** A Rime candidate: the text to commit and Rime's comment (BumBoard schemas put the reading there). */
data class Candidate(val text: String, val comment: String)

/** What Rime is composing: the preedit shown in the text field and the candidates for it. */
data class Composition(
    val preedit: String,
    val cursor: Int,
    val highlighted: Int,
    val candidates: List<Candidate>,
)

/** Thin Kotlin face of librime, backed by libbumrime.so (bumboard/cpp/bum_rime.cpp). */
object Rime {
    init {
        System.loadLibrary("bumrime")
    }

    fun start(sharedDir: String, userDir: String, fullCheck: Boolean) =
        nativeStart(sharedDir, userDir, fullCheck)

    fun createSession(): Long = nativeCreateSession()
    fun destroySession(session: Long) = nativeDestroySession(session)
    fun selectSchema(session: Long, schema: String): Boolean = nativeSelectSchema(session, schema)
    fun setOption(session: Long, option: String, value: Boolean) = nativeSetOption(session, option, value)
    fun processKey(session: Long, keycode: Int, mask: Int = 0): Boolean = nativeProcessKey(session, keycode, mask)
    fun clearComposition(session: Long) = nativeClearComposition(session)
    fun commitComposition(session: Long): Boolean = nativeCommitComposition(session)
    fun selectCandidate(session: Long, index: Int): Boolean = nativeSelectCandidate(session, index)
    fun commit(session: Long): String? = nativeGetCommit(session)
    fun input(session: Long): String = nativeGetInput(session) ?: ""

    fun composition(session: Long, limit: Int = 100): Composition? {
        val raw = nativeGetContext(session, limit) ?: return null
        val candidates = (3 until raw.size step 2).map { Candidate(raw[it] ?: "", raw[it + 1] ?: "") }
        return Composition(
            preedit = raw[0] ?: "",
            cursor = raw[1]?.toIntOrNull() ?: 0,
            highlighted = raw[2]?.toIntOrNull() ?: 0,
            candidates = candidates,
        )
    }

    @JvmStatic private external fun nativeStart(sharedDir: String, userDir: String, fullCheck: Boolean)
    @JvmStatic private external fun nativeStop()
    @JvmStatic private external fun nativeCreateSession(): Long
    @JvmStatic private external fun nativeDestroySession(session: Long)
    @JvmStatic private external fun nativeSelectSchema(session: Long, schema: String): Boolean
    @JvmStatic private external fun nativeSetOption(session: Long, option: String, value: Boolean)
    @JvmStatic private external fun nativeProcessKey(session: Long, keycode: Int, mask: Int): Boolean
    @JvmStatic private external fun nativeClearComposition(session: Long)
    @JvmStatic private external fun nativeCommitComposition(session: Long): Boolean
    @JvmStatic private external fun nativeSelectCandidate(session: Long, index: Int): Boolean
    @JvmStatic private external fun nativeGetCommit(session: Long): String?
    @JvmStatic private external fun nativeGetInput(session: Long): String?
    @JvmStatic private external fun nativeGetContext(session: Long, limit: Int): Array<String?>?
}
