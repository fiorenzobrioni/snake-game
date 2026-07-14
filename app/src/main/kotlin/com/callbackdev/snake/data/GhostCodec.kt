package com.callbackdev.snake.data

import android.util.Base64
import com.callbackdev.snake.game.GhostRun
import java.nio.ByteBuffer

/**
 * Turns a [GhostRun]'s pure-Kotlin integer encoding into the compact string
 * persisted in DataStore, and back. The trajectory is stored as the big-endian
 * bytes of its [GhostRun.toInts] array, Base64-encoded, so a multi-minute run
 * costs only a few KB. Decoding is defensive: any malformed or stale payload
 * yields null so a bad ghost can never crash a run.
 */
internal object GhostCodec {

    fun encode(run: GhostRun): String {
        val ints = run.toInts()
        val buffer = ByteBuffer.allocate(ints.size * Int.SIZE_BYTES)
        ints.forEach { buffer.putInt(it) }
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    fun decode(text: String): GhostRun? = runCatching {
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        if (bytes.isEmpty() || bytes.size % Int.SIZE_BYTES != 0) return null
        val buffer = ByteBuffer.wrap(bytes)
        val ints = IntArray(bytes.size / Int.SIZE_BYTES) { buffer.int }
        GhostRun.fromInts(ints)
    }.getOrNull()
}
