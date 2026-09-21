package com.mtgcompanion.app.ui.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.mtgcompanion.app.data.CardIndex
import com.mtgcompanion.app.data.IndexMatch
import java.io.File
import java.nio.FloatBuffer

/**
 * Knowing a card by sight: the flattened card (FlatCard) run through the image model on the phone,
 * and its fingerprint looked up in the card index (data/CardIndex.kt). Built from the two downloaded
 * files — see CardIndexRepository. Mirrors the web app's src/scan/cardRecognizer.ts.
 */
class CardRecognizer(modelFile: File, indexFile: File) {
    private val env = OrtEnvironment.getEnvironment()
    // ORT reads the model straight off disk; the index is read into memory whole (~15 MB).
    private val session: OrtSession = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
    val index = CardIndex(indexFile.readBytes())

    /** What the index makes of a flattened card. */
    data class Recognized(
        /** The nearest printings to the card's look, over the whole index, best first. */
        val anywhere: List<IndexMatch>,
        /** The nearest among [name]'s printings, when a name was given. */
        val named: List<IndexMatch>
    )

    /**
     * The flattened card fingerprinted — through each of its likeliest outlines, as edge and as
     * frame — and looked up: over the whole index, and among the printings of [name] if the title
     * was read.
     */
    fun recognize(flat: FlatCard, name: String? = null): Recognized {
        val size = index.inputSize
        val count = flat.modelInputCount
        val looks = OnnxTensor.createTensor(env, FloatBuffer.wrap(flat.modelInputs(size)), longArrayOf(count.toLong(), 3, size.toLong(), size.toLong())).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { out ->
                @Suppress("UNCHECKED_CAST")
                val features = out.get(0).value as Array<FloatArray>
                features.map { index.fingerprint(it) }
            }
        }
        val rows = name?.let { index.rowsNamed(it) }
        return Recognized(
            anywhere = index.nearest(looks, 8),
            named = if (rows != null && rows.isNotEmpty()) index.nearest(looks, 8, rows) else emptyList()
        )
    }

    fun close() = session.close()
}
