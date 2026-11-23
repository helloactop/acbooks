package app.acbooks.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

internal data class PendingAvatar(val version: Long, val bytes: ByteArray)

/**
 * One local avatar for offline use. A pending local selection wins over remote updates.
 * A remote avatar is associated with the verified server origin and account UUID.
 */
internal class ProfileAvatarStore(private val context: Context) {
    private val lock = Any()
    private val preferences = context.getSharedPreferences("profile_avatar", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "profile")
    private val file = File(directory, "avatar.jpg")

    fun image(): ImageBitmap? = synchronized(lock) {
        if (!file.isFile) null else BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }

    fun isPending(): Boolean = synchronized(lock) { preferences.getBoolean("pending", false) }

    fun pending(): PendingAvatar? = synchronized(lock) {
        if (!preferences.getBoolean("pending", false)) return@synchronized null
        if (!file.isFile) throw IOException("Pending avatar is missing")
        PendingAvatar(preferences.getLong("localVersion", 0L), file.readBytes())
    }

    fun select(source: Uri): ImageBitmap {
        val bytes = normalize(source)
        return synchronized(lock) {
            replace(bytes)
            val revision = preferences.getLong("localVersion", 0L) + 1L
            if (!preferences.edit().putLong("localVersion", revision).putBoolean("pending", true).commit()) {
                throw IOException("Could not mark the avatar for sync")
            }
            checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).asImageBitmap()
        }
    }

    fun needsRemote(scope: String, version: Long?): Boolean = synchronized(lock) {
        !preferences.getBoolean("pending", false) && (
            preferences.getString("scope", null) != scope ||
                preferences.getString("serverVersion", null) != version?.toString() ||
                (version != null && (!file.isFile || BitmapFactory.decodeFile(file.absolutePath) == null)) ||
                (version == null && file.exists())
            )
    }

    fun acceptRemote(scope: String, version: Long?, bytes: ByteArray?) = synchronized(lock) {
        if (preferences.getBoolean("pending", false)) return@synchronized
        if (bytes == null) {
            if (file.exists() && !file.delete()) throw IOException("Could not clear the previous avatar")
        } else {
            if (bytes.size > MAX_AVATAR_BYTES || bytes.isEmpty()) throw IOException("Invalid server avatar")
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
            if (dimensions.outWidth != 512 || dimensions.outHeight != 512) {
                throw IOException("Unexpected server avatar dimensions")
            }
            replace(bytes)
        }
        if (!preferences.edit().putString("scope", scope).putString("serverVersion", version?.toString()).commit()) {
            throw IOException("Could not save the server avatar revision")
        }
    }

    fun markUploaded(pendingVersion: Long, scope: String, serverVersion: Long?): Boolean = synchronized(lock) {
        if (!preferences.getBoolean("pending", false) ||
            preferences.getLong("localVersion", 0L) != pendingVersion
        ) return@synchronized false
        if (!preferences.edit().putBoolean("pending", false).putString("scope", scope)
                .putString("serverVersion", serverVersion?.toString()).commit()
        ) throw IOException("Could not confirm the avatar upload")
        true
    }

    private fun replace(bytes: ByteArray) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create the avatar directory")
        val staging = File(directory, "avatar.tmp")
        try {
            staging.writeBytes(bytes)
            if (!staging.renameTo(file)) throw IOException("Could not replace the avatar")
        } finally {
            staging.delete()
        }
    }

    private fun normalize(uri: Uri): ByteArray {
        // ImageDecoder honors source orientation; downsample large photos before center cropping.
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width > 0 && height > 0) { "Invalid avatar dimensions" }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1.0, 2048.0 / maxOf(width, height))
            if (scale < 1.0) {
                decoder.setTargetSize(
                    maxOf(1, (width * scale).roundToInt()),
                    maxOf(1, (height * scale).roundToInt()),
                )
            }
        }
        val result = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        try {
            val side = minOf(decoded.width, decoded.height)
            val left = (decoded.width - side) / 2
            val top = (decoded.height - side) / 2
            val canvas = Canvas(result)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(
                decoded,
                Rect(left, top, left + side, top + side),
                Rect(0, 0, 512, 512),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            val output = ByteArrayOutputStream()
            if (!result.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                throw IOException("Could not encode avatar")
            }
            val bytes = output.toByteArray()
            if (bytes.isEmpty() || bytes.size > MAX_AVATAR_BYTES) throw IOException("Avatar is too large")
            return bytes
        } finally {
            decoded.recycle()
            result.recycle()
        }
    }

    private companion object {
        const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
    }
}
