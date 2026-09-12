package com.mtgcompanion.app.data.artrecognition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Embeds a card-art image into the same 1280-dim, L2-normalized MobileNetV2 feature vector the
 * reference index ([ArtIndexReader]) was built from — same ImageNet-pretrained model (bundled as
 * an asset), same preprocessing (224x224, ImageNet mean/std normalization), so the two are
 * directly comparable. This is an off-the-shelf general-purpose visual-similarity feature
 * extractor, not a model trained on Magic cards specifically — its own semantic understanding of
 * images is what makes two photos of the same art land close together despite crop/lighting/angle
 * differences.
 */
class ArtEmbedder(context: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = context.assets.open("mobilenetv2.onnx").use { stream ->
        env.createSession(stream.readBytes(), OrtSession.SessionOptions())
    }

    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    fun embed(bitmap: Bitmap): FloatArray {
        val resized = if (bitmap.width == 224 && bitmap.height == 224) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        }
        val pixels = IntArray(224 * 224)
        resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)

        // NCHW float32, ImageNet-normalized — matches the Python build pipeline's preprocessing.
        val plane = 224 * 224
        val chw = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            chw[i] = (((p shr 16) and 0xFF) / 255f - mean[0]) / std[0]
            chw[plane + i] = (((p shr 8) and 0xFF) / 255f - mean[1]) / std[1]
            chw[2 * plane + i] = ((p and 0xFF) / 255f - mean[2]) / std[2]
        }
        if (resized !== bitmap) resized.recycle()

        val shape = longArrayOf(1, 3, 224, 224)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape).use { input ->
            session.run(mapOf("input" to input)).use { results ->
                // "464" is the GlobalAveragePool node's output — the pooled 1280-dim feature
                // vector before the model's own (unused here) ImageNet classification head.
                @Suppress("UNCHECKED_CAST")
                val raw = results.get("464").get().value as Array<FloatArray>
                val vec = raw[0]
                var normSq = 0f
                for (v in vec) normSq += v * v
                val norm = sqrt(normSq)
                return if (norm > 0f) FloatArray(vec.size) { vec[it] / norm } else vec
            }
        }
    }
}
