package app.acbooks.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.acbooks.core.datastore.ProfilePreferences
import app.acbooks.server.ServerFailure
import app.acbooks.server.ServerSession
import app.acbooks.server.ServerSessions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal enum class AvatarSync { Idle, Syncing, Pending, Failed }

internal class ProfileAvatars(
    private val context: Context,
    private val preferences: ProfilePreferences,
    private val sessions: ServerSessions,
) {
    private val file = File(context.filesDir, "profile/avatar.jpg")
    private val local = MutableStateFlow<ImageBitmap?>(null)
    private val status = MutableStateFlow(AvatarSync.Idle)

    val image: StateFlow<ImageBitmap?> = local.asStateFlow()
    val sync: StateFlow<AvatarSync> = status.asStateFlow()

    suspend fun restore() = withContext(Dispatchers.IO) {
        local.value = file.takeIf(File::isFile)?.let(::decode)
    }

    suspend fun refresh(session: ServerSession?) = withContext(Dispatchers.IO) {
        if (session == null || session.isExpired()) return@withContext
        status.value = AvatarSync.Syncing
        try {
            val account = sessions.account(session)
            val pending = preferences.avatarPending.first()
            val cachedRevision = preferences.avatarRevision.first()
            when {
                file.isFile && (pending || account.avatarUpdatedAt == null) -> upload(session)
                account.avatarUpdatedAt != null && account.avatarUpdatedAt != cachedRevision ->
                    download(session, account.avatarUpdatedAt)
                else -> status.value = AvatarSync.Idle
            }
        } catch (_: ServerFailure) {
            status.value = if (file.isFile) AvatarSync.Pending else AvatarSync.Failed
        }
    }

    suspend fun select(uri: Uri) = withContext(Dispatchers.IO) {
        val image = try {
            prepare(uri)
        } catch (failure: Exception) {
            Log.w(TAG, "Could not prepare avatar: ${failure.javaClass.simpleName}")
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        if (image == null) {
            Log.w(TAG, "Avatar preparation returned no image")
            status.value = AvatarSync.Failed
            return@withContext
        }
        try {
            publish(image)
            local.value = decode(file)
        } catch (failure: IOException) {
            Log.w(TAG, "Could not store avatar: ${failure.javaClass.simpleName}")
            status.value = AvatarSync.Failed
            return@withContext
        }
        val session = sessions.session.value?.takeUnless(ServerSession::isExpired)
        if (session == null) {
            preferences.setAvatarPending(true)
            status.value = AvatarSync.Pending
        } else {
            upload(session)
        }
    }

    private suspend fun upload(session: ServerSession) {
        status.value = AvatarSync.Syncing
        try {
            val account = sessions.uploadAvatar(session, file.readBytes())
            val revision = account.avatarUpdatedAt
            if (revision == null) throw ServerFailure(app.acbooks.server.ServerError.Response)
            preferences.setAvatarRevision(revision)
            preferences.setAvatarPending(false)
            status.value = AvatarSync.Idle
        } catch (_: ServerFailure) {
            preferences.setAvatarPending(true)
            status.value = AvatarSync.Pending
        } catch (_: IOException) {
            preferences.setAvatarPending(true)
            status.value = AvatarSync.Pending
        }
    }

    private suspend fun download(session: ServerSession, revision: Long) {
        try {
            publish(sessions.fetchAvatar(session))
            local.value = decode(file)
            preferences.setAvatarRevision(revision)
            preferences.setAvatarPending(false)
            status.value = AvatarSync.Idle
        } catch (_: ServerFailure) {
            status.value = if (file.isFile) AvatarSync.Pending else AvatarSync.Failed
        } catch (_: IOException) {
            status.value = if (file.isFile) AvatarSync.Pending else AvatarSync.Failed
        }
    }

    private fun publish(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, ".avatar.part")
        try {
            pending.writeBytes(bytes)
            if (!pending.renameTo(file)) {
                pending.copyTo(file, overwrite = true)
            }
        } finally {
            pending.delete()
        }
    }

    private fun decode(source: File): ImageBitmap? = try {
        BitmapFactory.decodeFile(source.path)?.asImageBitmap()
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun prepare(uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = resolver.openInputStream(uri)
        if (boundsInput == null) {
            Log.w(TAG, "Avatar source is not readable")
            return null
        }
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "Avatar source has invalid dimensions")
            return null
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SOURCE) sample *= 2
        val decodeInput = resolver.openInputStream(uri)
        if (decodeInput == null) {
            Log.w(TAG, "Avatar source is not readable for decoding")
            return null
        }
        val decoded = decodeInput.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (decoded == null) {
            Log.w(TAG, "Avatar source could not be decoded")
            return null
        }
        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val upright = orient(decoded, orientation)
        if (upright !== decoded) decoded.recycle()
        val side = minOf(upright.width, upright.height)
        if (side <= 0) {
            upright.recycle()
            return null
        }
        val cropped = Bitmap.createBitmap(
            upright,
            (upright.width - side) / 2,
            (upright.height - side) / 2,
            side,
            side,
        )
        if (cropped !== upright) upright.recycle()
        val scaled = Bitmap.createScaledBitmap(cropped, AVATAR_SIZE, AVATAR_SIZE, true)
        if (scaled !== cropped) cropped.recycle()
        return try {
            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_QUALITY, output)
                output.toByteArray()
            }
        } finally {
            scaled.recycle()
        }
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

private const val TAG = "ProfileAvatars"
private const val MAX_SOURCE = 2048
private const val AVATAR_SIZE = 512
private const val AVATAR_QUALITY = 90