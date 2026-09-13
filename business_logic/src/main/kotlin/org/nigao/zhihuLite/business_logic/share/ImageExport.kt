package org.nigao.zhihuLite.business_logic.share

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.github.aakira.napier.Napier
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.nigao.zhihuLite.business_logic.zhihu.ZhihuApi
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Where a picture the reader saves goes, as the gallery shows it. */
const val IMAGE_EXPORT_RELATIVE_PATH = "Pictures/ZhihuLite"

/** Folder name on the legacy (pre-Q) path, where the public directory is addressed directly. */
private const val IMAGE_EXPORT_FOLDER = "ZhihuLite"

/**
 * The picture is on the phone now, under [IMAGE_EXPORT_RELATIVE_PATH].
 */
data class ImageSaved(val displayName: String) : ImageExportResult

/**
 * Nothing was written: writing to the public pictures collection needs
 * `WRITE_EXTERNAL_STORAGE` before Android 10, and the caller has to ask for it.
 */
object ImageNeedsPermission : ImageExportResult

/** The download or the write failed; [reason] is for the log, not for the reader. */
data class ImageExportFailed(val reason: String?) : ImageExportResult

sealed interface ImageExportResult

/**
 * Fetches the bytes of an image the viewer is already showing.
 *
 * A plain GET with the app's own client: these CDN urls need no session and no signature (the feed
 * already loads them the same way), and the client carries the request timeouts that keep a stalled
 * download from hanging a coroutine forever.
 */
suspend fun downloadImage(url: String): ByteArray? {
    return try {
        val response = ZhihuApi.client.get(url)
        if (!response.status.isSuccess()) {
            Napier.w("Image download answered ${response.status} for $url")
            return null
        }
        response.bodyAsBytes()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Napier.e("Failed to download image $url", e)
        null
    }
}

/**
 * Writes [bytes] into the shared pictures collection.
 *
 * Android 10 and later go through `MediaStore` — no permission, and the file lands in
 * `Pictures/ZhihuLite` where a gallery will find it. Older releases have no `RELATIVE_PATH` column,
 * so the file is written into the public directory and offered to the media scanner instead; that
 * path needs the storage permission, which is reported back rather than requested here (the layer
 * that can ask the reader is the UI).
 */
fun saveImageToPictures(
    context: Context,
    url: String,
    bytes: ByteArray,
    nowMs: Long = System.currentTimeMillis(),
): ImageExportResult {
    val displayName = imageExportFileName(url, nowMs)
    val mimeType = imageExportMimeType(url)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return insertIntoMediaStore(context, displayName, mimeType, bytes, nowMs)
    }
    val granted = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return ImageNeedsPermission
    return writeToPublicPictures(context, displayName, mimeType, bytes)
}

/**
 * Opens the system share sheet with the picture itself.
 *
 * The bytes go to this app's cache and are exposed through its `FileProvider`: sharing must not
 * write into the reader's gallery, and the system cleans the cache up. Older exports are deleted
 * first so the folder does not grow with every share.
 */
fun shareImage(
    context: Context,
    url: String,
    bytes: ByteArray,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    return try {
        val directory = File(context.cacheDir, SHARED_IMAGE_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            Napier.w("Could not create ${directory.absolutePath}")
            return false
        }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, imageExportFileName(url, nowMs))
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val chooser = Intent.createChooser(imageShareIntent(uri, imageExportMimeType(url)), null)
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // From a non-activity context (or when the viewer's task is alone) the chooser still needs
        // its own task.
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        Napier.e("Failed to share image $url", e)
        false
    }
}

/**
 * The share intent for one image.
 *
 * Public for the app module's suite: the flags matter (`FLAG_GRANT_READ_URI_PERMISSION` is what lets
 * the receiving app read a URI it has no business reaching), and they are easy to lose in a refactor.
 */
fun imageShareIntent(uri: Uri, mimeType: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = mimeType
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/**
 * `ZhihuLite_20260913_113012.jpg`: sortable, obviously ours, and never a name the CDN has to be asked
 * for.
 */
fun imageExportFileName(url: String, nowMs: Long): String =
    "ZhihuLite_${FILE_STAMP.format(Instant.ofEpochMilli(nowMs))}.${imageExtension(url)}"

fun imageExportMimeType(url: String): String = when (imageExtension(url)) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}

/**
 * The image's real extension, from the url's path.
 *
 * Zhihu serves `..._qhd.jpg?source=...`, so the query has to go first; anything unrecognised is
 * treated as a JPEG, which is what those urls overwhelmingly are.
 */
private fun imageExtension(url: String): String {
    val path = url.substringBefore('?').substringBefore('#')
    val extension = path.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "jpg", "jpeg" -> "jpg"
        "png" -> "png"
        "webp" -> "webp"
        "gif" -> "gif"
        else -> "jpg"
    }
}

private fun insertIntoMediaStore(
    context: Context,
    displayName: String,
    mimeType: String,
    bytes: ByteArray,
    nowMs: Long,
): ImageExportResult {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, IMAGE_EXPORT_RELATIVE_PATH)
        put(MediaStore.Images.Media.DATE_ADDED, nowMs / 1000)
        // Pending until the bytes are in: a half-written row must not show up in the gallery.
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = try {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    } catch (e: Exception) {
        Napier.e("MediaStore refused the insert for $displayName", e)
        null
    } ?: return ImageExportFailed("MediaStore insert returned no uri")

    return try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: return ImageExportFailed("no output stream for $uri").also {
                resolver.delete(uri, null, null)
            }
        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
        ImageSaved(displayName)
    } catch (e: Exception) {
        // Leave nothing behind if the write failed.
        resolver.delete(uri, null, null)
        Napier.e("Failed to write $displayName", e)
        ImageExportFailed(e.message)
    }
}

/** Pre-Android-10 path: the public directory plus a media-scanner nudge. */
private fun writeToPublicPictures(
    context: Context,
    displayName: String,
    mimeType: String,
    bytes: ByteArray,
): ImageExportResult {
    return try {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val directory = File(pictures, IMAGE_EXPORT_FOLDER)
        if (!directory.exists() && !directory.mkdirs()) {
            return ImageExportFailed("could not create ${directory.absolutePath}")
        }
        val file = File(directory, displayName)
        file.writeBytes(bytes)
        // Without this the bytes are on disk but no gallery lists them until the next media scan.
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        ImageSaved(displayName)
    } catch (e: Exception) {
        Napier.e("Failed to write $displayName", e)
        ImageExportFailed(e.message)
    }
}

private const val SHARED_IMAGE_DIRECTORY = "shared_images"

private val FILE_STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())
