package org.nigao.app

import android.content.Context
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_ui.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.Locale

/**
 * The app's two languages, and the rule that keeps them honest.
 *
 * Everything the reader can see comes from `strings.xml`: English in `values/`, Chinese in
 * `values-zh/`. That rule was broken in both directions before — Chinese sat in the default file (so
 * it showed on every device), and the Chinese file was named `values-cn`, a qualifier Android does
 * not resolve, so those 19 strings never loaded at all.
 *
 * The checks below are cheap and catch exactly those two mistakes, plus a hard-coded Chinese literal
 * slipping back into the code.
 */
@RunWith(RobolectricTestRunner::class)
class LocalizationTest {

    @Test
    fun `the default resources are english`() {
        val chinese = resourceStrings(defaultFile()).filterValues { it.hasChinese() }

        assertTrue("the default resources must not carry Chinese: $chinese", chinese.isEmpty())
    }

    @Test
    fun `every string has a chinese translation and none are stale`() {
        val english = resourceStrings(defaultFile()).keys
        val chinese = resourceStrings(chineseFile()).keys

        assertEquals("missing from values-zh", emptyList<String>(), (english - chinese).sorted())
        assertEquals("no longer in values", emptyList<String>(), (chinese - english).sorted())
    }

    @Test
    fun `the app module translates its name too`() {
        assertEquals(
            setOf("app_name"),
            resourceStrings(appDefaultFile()).keys,
        )
        assertEquals(
            resourceStrings(appDefaultFile()).keys,
            resourceStrings(appChineseFile()).keys,
        )
    }

    @Test
    fun `strings resolve per locale`() {
        assertEquals("Save image", context(Locale.US).getString(R.string.image_menu_save))
        assertEquals("保存图片", context(Locale.SIMPLIFIED_CHINESE).getString(R.string.image_menu_save))
        assertEquals("2× speed", context(Locale.US).getString(R.string.video_double_speed))
        assertEquals("倍速播放中", context(Locale.SIMPLIFIED_CHINESE).getString(R.string.video_double_speed))
    }

    @Test
    fun `no main source hard-codes chinese`() {
        val offenders = mainSourceFiles().mapNotNull { file ->
            val literals = stringLiterals(file.readText()).filter { it.hasChinese() }
            if (literals.isEmpty()) null else "${file.relativeTo(repoRoot())}: $literals"
        }

        assertTrue("Chinese must live in values-zh/strings.xml, not in code: $offenders", offenders.isEmpty())
    }

    /** The scanner has to ignore comments, or every quoted example in a KDoc would trip it. */
    @Test
    fun `the literal scanner ignores comments and keeps real strings`() {
        val source = """
            // 这是注释 "不是字符串"
            /* 块注释里的 "中文" */
            val real = "硬编码中文"
            val raw = ""${'"'}也是中文""${'"'}
        """.trimIndent()

        val found = stringLiterals(source).filter { it.hasChinese() }

        assertEquals(listOf("硬编码中文", "也是中文"), found)
    }

    private fun context(locale: Locale): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.setLocale(locale)
        return application.createConfigurationContext(configuration)
    }

    private fun defaultFile() = moduleFile("business_ui", "values")
    private fun chineseFile() = moduleFile("business_ui", "values-zh")
    private fun appDefaultFile() = moduleFile("assemble", "values")
    private fun appChineseFile() = moduleFile("assemble", "values-zh")

    private fun moduleFile(module: String, qualifier: String) =
        File(repoRoot(), "$module/src/main/res/$qualifier/strings.xml")

    /** Walks up from wherever the test runs until the Gradle settings file shows up. */
    private fun repoRoot(): File {
        var directory = File(".").absoluteFile
        while (directory.parentFile != null) {
            if (File(directory, "settings.gradle.kts").exists()) return directory
            directory = directory.parentFile
        }
        error("could not find the repository root from ${File(".").absolutePath}")
    }

    private fun mainSourceFiles(): List<File> =
        repoRoot().listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "src/main").isDirectory }
            .flatMap { module -> File(module, "src/main").walkTopDown().filter { it.extension == "kt" }.toList() }

    private fun resourceStrings(file: File): Map<String, String> {
        assertTrue("missing $file", file.exists())
        return STRING_ENTRY.findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun String.hasChinese() = CJK.containsMatchIn(this)

    /**
     * The string literals of a Kotlin source file, with comments left out.
     *
     * A small state machine rather than a regex: comments quote the Chinese the reader reported (and
     * urls contain `//`), so neither stripping by line nor matching quoted text naively is correct.
     */
    private fun stringLiterals(source: String): List<String> {
        val literals = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        var inString = false
        var inRawString = false
        var inChar = false
        var inLineComment = false
        var inBlockComment = false

        while (index < source.length) {
            val char = source[index]
            val next = source.getOrNull(index + 1)
            when {
                inLineComment -> if (char == '\n') inLineComment = false
                inBlockComment -> if (char == '*' && next == '/') { inBlockComment = false; index++ }
                inString -> when {
                    char == '\\' -> index++ // an escaped character, whatever it is
                    char == '"' -> { inString = false; literals += current.toString(); current.clear() }
                    else -> current.append(char)
                }
                inRawString -> if (char == '"' && next == '"' && source.getOrNull(index + 2) == '"') {
                    inRawString = false
                    literals += current.toString()
                    current.clear()
                    index += 2
                } else {
                    current.append(char)
                }
                inChar -> if (char == '\\') index++ else if (char == '\'') inChar = false
                char == '/' && next == '/' -> inLineComment = true
                char == '/' && next == '*' -> { inBlockComment = true; index++ }
                char == '"' && next == '"' && source.getOrNull(index + 2) == '"' -> { inRawString = true; index += 2 }
                char == '"' -> inString = true
                char == '\'' -> inChar = true
            }
            index++
        }
        return literals
    }

    private companion object {
        val CJK = Regex("[\\u4e00-\\u9fff]")
        val STRING_ENTRY = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    }
}
