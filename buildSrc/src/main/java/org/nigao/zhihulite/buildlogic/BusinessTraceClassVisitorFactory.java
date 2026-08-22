package org.nigao.zhihulite.buildlogic;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;

import org.objectweb.asm.ClassVisitor;

import java.util.Objects;

/**
 * Instruments project-owned classes with {@code android.os.Trace} sections.
 */
public abstract class BusinessTraceClassVisitorFactory
        implements AsmClassVisitorFactory<BusinessTraceParameters> {

    @Override
    public ClassVisitor createClassVisitor(
            ClassContext classContext,
            ClassVisitor nextClassVisitor
    ) {
        BusinessTraceParameters parameters = getParameters().get();
        return new BusinessTraceClassVisitor(
                nextClassVisitor,
                classContext.getCurrentClassData().getClassName(),
                parameters.getIncludedClassPrefix().get(),
                parameters.getMaxSectionNameLength().get()
        );
    }

    @Override
    public boolean isInstrumentable(ClassData classData) {
        String includedPrefix = getParameters()
                .get()
                .getIncludedClassPrefix()
                .get();
        String className = classData.getClassName();

        if (!(className.equals(includedPrefix) || className.startsWith(includedPrefix + "."))) {
            return false;
        }

        return !isAndroidGeneratedClass(className, includedPrefix);
    }

    private static boolean isAndroidGeneratedClass(String className, String includedPrefix) {
        Objects.requireNonNull(className);
        String relativeName = className.substring(includedPrefix.length());
        return relativeName.equals(".BuildConfig")
                || relativeName.equals(".R")
                || relativeName.startsWith(".R$")
                || relativeName.equals(".Manifest")
                || relativeName.startsWith(".Manifest$");
    }
}
