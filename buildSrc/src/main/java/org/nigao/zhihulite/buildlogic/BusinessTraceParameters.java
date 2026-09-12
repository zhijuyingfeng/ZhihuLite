package org.nigao.zhihulite.buildlogic;

import com.android.build.api.instrumentation.InstrumentationParameters;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/**
 * Parameters used by the AGP bytecode instrumentation worker.
 */
public interface BusinessTraceParameters extends InstrumentationParameters {
    @Input
    Property<String> getIncludedClassPrefix();

    @Input
    Property<Integer> getMaxSectionNameLength();
}
