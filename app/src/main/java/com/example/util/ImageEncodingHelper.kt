package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageEncodingHelper {

    private const val MAX_AVATAR_PX = 160
    private const val JPEG_QUALITY = 75

    fun encodeProfilePhotoToBase64(context: Context, uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            var sampleSize = 1
            while (max(srcWidth / sampleSize, srcHeight / sampleSize) > MAX_AVATAR_PX * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val side = min(decoded.width, decoded.height)
            val xOffset = (decoded.width - side) / 2
            val yOffset = (decoded.height - side) / 2
            val cropped = Bitmap.createBitmap(decoded, xOffset, yOffset, side, side)
            val scaled = if (side != MAX_AVATAR_PX) {
                Bitmap.createScaledBitmap(cropped, MAX_AVATAR_PX, MAX_AVATAR_PX, true)
            } else {
                cropped
            }

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            val bytes = out.toByteArray()

            if (cropped !== decoded) cropped.recycle()
            if (scaled !== cropped) scaled.recycle()
            decoded.recycle()

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
