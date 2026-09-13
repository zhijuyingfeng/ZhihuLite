package org.nigao.app

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_logic.share.IMAGE_EXPORT_RELATIVE_PATH
import org.nigao.zhihuLite.business_logic.share.ImageNeedsPermission
import org.nigao.zhihuLite.business_logic.share.imageExportFileName
import org.nigao.zhihuLite.business_logic.share.imageExportMimeType
import org.nigao.zhihuLite.business_logic.share.imageShareIntent
import org.nigao.zhihuLite.business_logic.share.saveImageToPictures
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Saving and sharing one picture.
 *
 * The pieces that decide *what* is written and *what* is handed to the share sheet are pure and
 * pinned here; the parts that touch MediaStore and the chooser are verified on the device, because a
 * Robolectric content resolver is not the one the gallery reads.
 */
@RunWith(RobolectricTestRunner::class)
class ImageExportTest {

    @Test
    fun `pictures land in the ZhihuLite pictures folder`() {
        assertEquals("Pictures/ZhihuLite", IMAGE_EXPORT_RELATIVE_PATH)
    }

    @Test
    fun `a saved file is named for this app and sorts by time`() {
        val name = imageExportFileName(CDN_URL, nowMs = 1_787_000_000_000)

        assertTrue("unexpected name: $name", Regex("""ZhihuLite_\d{8}_\d{6}\.jpg""").matches(name))
    }

    @Test
    fun `the extension comes from the url's path, not its query`() {
        // The captured urls end in `_qhd.jpg?source=...`, so the query has to be ignored.
        assertEquals("image/jpeg", imageExportMimeType(CDN_URL))
        assertEquals("image/png", imageExportMimeType("https://picx.zhimg.com/v2-abc.png?source=x"))
        assertEquals("image/webp", imageExportMimeType("https://picx.zhimg.com/v2-abc.webp"))
        assertEquals("image/gif", imageExportMimeType("https://picx.zhimg.com/v2-abc.gif#frag"))
        // Anything unrecognised is treated as what those urls overwhelmingly are.
        assertEquals("image/jpeg", imageExportMimeType("https://picx.zhimg.com/v2-abc"))
        assertTrue(imageExportFileName("https://picx.zhimg.com/v2-abc", 0).endsWith(".jpg"))
    }

    @Test
    fun `sharing hands over the image and the right to read it`() {
        val uri = Uri.parse("content://org.nigao.zhihuLite.fileprovider/shared_images/a.jpg")

        val intent = imageShareIntent(uri, "image/jpeg")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue(
            "without this the receiving app cannot read the uri",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    /** Before Android 10 the public pictures directory needs a permission the UI has to ask for. */
    @Test
    @Config(sdk = [28])
    fun `an older device without the storage permission is told to ask for it`() {
        val context = RuntimeEnvironment.getApplication()

        val result = saveImageToPictures(context, CDN_URL, ByteArray(4), nowMs = 0)

        assertEquals(ImageNeedsPermission, result)
    }

    private companion object {
        const val CDN_URL =
            "https://picx.zhimg.com/v2-54e32908012d03a46e066b8437e99f2d_qhd.jpg?source=1d2f5c51"
    }
}
