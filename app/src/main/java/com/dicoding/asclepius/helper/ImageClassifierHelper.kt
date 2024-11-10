package com.dicoding.asclepius.helper

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.IOException

class ImageClassifierHelper(private val context: Context) {

    private var imageClassifier: ImageClassifier? = null

    init {
        try {
            setupImageClassifier()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun setupImageClassifier() {
        val optionsBuilder = ImageClassifier.ImageClassifierOptions.builder()
            .setScoreThreshold(0.2f)
            .setMaxResults(3)

        val baseOptionsBuilder = BaseOptions.builder()
            .setNumThreads(4)
        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        try {
            imageClassifier = ImageClassifier.createFromFileAndOptions(
                context,
                "cancer_classification.tflite",
                optionsBuilder.build()
            )
        } catch (e: IOException) {
            Log.e("ImageClassifierHelper", "Model loading failed: ${e.message}")
            throw RuntimeException("Model file not found or cannot be loaded.", e)
        }
    }

    fun classifyStaticImage(imageUri: Uri): String {
        return try {
            val bitmap = convertUriToBitmap(imageUri)
            val resizedBitmap = resizeBitmap(bitmap, 224, 224)
            val rotatedBitmap = rotateBitmapIfNeeded(resizedBitmap, imageUri)
            classifyImage(rotatedBitmap)
        } catch (e: Exception) {
            "Error in classification: ${e.localizedMessage}"
        }
    }

    private fun convertUriToBitmap(imageUri: Uri): Bitmap {
        return try {
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
        } catch (e: Exception) {
            throw RuntimeException("Error converting URI to Bitmap: ${e.localizedMessage}", e)
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, imageUri: Uri): Bitmap {
        return try {
            val exif = ExifInterface(context.contentResolver.openInputStream(imageUri)!!)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun classifyImage(bitmap: Bitmap): String {
        return try {
            val tensorImage = TensorImage.fromBitmap(bitmap)

            // Image processing (resize and cast) before classification
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                .add(CastOp(org.tensorflow.lite.DataType.FLOAT32))
                .add(NormalizeOp(0f, 1f))
                .build()

            val processedImage = imageProcessor.process(tensorImage)

            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setOrientation(ImageProcessingOptions.Orientation.RIGHT_TOP)
                .build()

            val inferenceTime = SystemClock.uptimeMillis()
            val results = imageClassifier?.classify(processedImage, imageProcessingOptions)
            val timeTaken = SystemClock.uptimeMillis() - inferenceTime

            // Log the inference time and raw output for debugging
            Log.d("Classification", "Inference time: $timeTaken ms")
            if (results == null || results.isEmpty()) {
                Log.e("Classification", "No results found!")
                return "Error in classification: No results"
            }

            val topResult = results[0].categories[0]
            Log.d("Classification Results", "Top result: ${topResult.label} with confidence ${topResult.score * 100}%")

            "${topResult.label} ${topResult.score * 100}%"
        } catch (e: Exception) {
            "Error in classification: ${e.localizedMessage}"
        }
    }

    fun close() {
        imageClassifier?.close()
    }
}