package pl.rozgladacz.ekosystempunkty.vision

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.RecognitionCell
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

class CardClassifier(context: Context) : AutoCloseable {
    private val labels = context.assets.open("card_labels.txt").bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() }.map(CardType::valueOf).toList()
    }
    private val interpreter: Interpreter? = runCatching {
        val descriptor = context.assets.openFd(MODEL_NAME)
        val model = FileInputStream(descriptor.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            descriptor.startOffset,
            descriptor.declaredLength,
        )
        Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })
    }.getOrNull()

    fun classify(bitmap: Bitmap): RecognitionCell {
        val model = interpreter ?: return RecognitionCell(null, 0f)
        val inputTensor = model.getInputTensor(0)
        val shape = inputTensor.shape()
        require(shape.size == 4 && shape[1] == INPUT_SIZE && shape[2] == INPUT_SIZE)
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val input = inputBuffer(resized, inputTensor.dataType())
        if (resized !== bitmap) resized.recycle()
        val outputTensor = model.getOutputTensor(0)
        val probabilities = if (outputTensor.dataType() == DataType.FLOAT32) {
            val output = Array(1) { FloatArray(labels.size) }
            model.run(input, output)
            output[0]
        } else {
            val output = ByteBuffer.allocateDirect(labels.size).order(ByteOrder.nativeOrder())
            model.run(input, output)
            output.rewind()
            val parameters = outputTensor.quantizationParams()
            FloatArray(labels.size) {
                val raw = output.get().let { value ->
                    if (outputTensor.dataType() == DataType.UINT8) value.toInt() and 0xff else value.toInt()
                }
                (raw - parameters.zeroPoint) * parameters.scale
            }
        }
        val bestIndex = probabilities.indices.maxBy { probabilities[it] }
        val confidence = probabilities[bestIndex].coerceIn(0f, 1f)
        return RecognitionCell(labels[bestIndex].takeIf { confidence >= ACCEPT_THRESHOLD }, confidence)
    }

    private fun inputBuffer(bitmap: Bitmap, dataType: DataType): ByteBuffer {
        val bytesPerChannel = if (dataType == DataType.FLOAT32) 4 else 1
        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())
        val parameters = interpreter?.getInputTensor(0)?.quantizationParams()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        pixels.forEach { color ->
            val channels = intArrayOf(color shr 16 and 0xff, color shr 8 and 0xff, color and 0xff)
            channels.forEach { value ->
                if (dataType == DataType.FLOAT32) {
                    buffer.putFloat(value.toFloat())
                } else {
                    val quantized = ((value / requireNotNull(parameters).scale) + parameters.zeroPoint).roundToInt()
                    buffer.put(quantized.coerceIn(if (dataType == DataType.UINT8) 0 else -128, if (dataType == DataType.UINT8) 255 else 127).toByte())
                }
            }
        }
        return buffer.rewind() as ByteBuffer
    }

    override fun close() {
        interpreter?.close()
    }

    companion object {
        private const val MODEL_NAME = "card_classifier.tflite"
        private const val INPUT_SIZE = 224
        private const val ACCEPT_THRESHOLD = 0.85f
    }
}

