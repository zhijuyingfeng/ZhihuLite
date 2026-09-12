package org.nigao.zhihuLite.performance;

import android.os.Trace;

/**
 * Runtime bridge used by bytecode-instrumented methods.
 *
 * <p>The RuntimeException fallback keeps local JVM tests compatible with Android's mockable
 * android.jar, whose framework method bodies throw by default. On a device, the calls delegate
 * directly to the platform tracing API.</p>
 */
@NoBusinessTrace
public final class BusinessMethodTrace {
    private BusinessMethodTrace() {
    }

    public static void begin(String sectionName) {
        try {
            Trace.beginSection(sectionName);
        } catch (RuntimeException ignored) {
            // Local JVM unit tests execute against method stubs from the mockable android.jar.
        }
    }

    public static void end() {
        try {
            Trace.endSection();
        } catch (RuntimeException ignored) {
            // Local JVM unit tests execute against method stubs from the mockable android.jar.
        }
    }
}
