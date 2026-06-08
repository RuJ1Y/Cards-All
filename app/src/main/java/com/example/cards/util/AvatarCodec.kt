package com.example.cards.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView

object AvatarCodec {

    fun encodeJpeg(bytes: ByteArray): String {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decode(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun setAvatarOrDefault(imageView: ImageView, base64: String?) {
        val bmp = decode(base64)
        if (bmp != null) {
            imageView.setImageBitmap(bmp)
        } else {
            imageView.setImageResource(com.example.cards.R.drawable.head)
        }
    }

    fun readBytes(contentResolver: android.content.ContentResolver, uri: Uri): ByteArray? {
        return runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    }
}
