package org.nigao.zhihulite.buildlogic;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Adds a synchronous Perfetto slice around each concrete source method.
 *
 * <p>The catch-all handler is important: a callee can throw without an ATHROW instruction being
 * present in the caller, so relying only on AdviceAdapter.onMethodExit would leave the trace stack
 * unbalanced.</p>
 */
final class BusinessTraceClassVisitor extends ClassVisitor {
    private static final String NO_BUSINESS_TRACE_DESCRIPTOR =
            "Lorg/nigao/zhihuLite/performance/NoBusinessTrace;";
    private static final String TRACE_OWNER =
            "org/nigao/zhihuLite/performance/BusinessMethodTrace";
    private static final String TRACE_BEGIN_METHOD = "begin";
    private static final String TRACE_BEGIN_DESCRIPTOR = "(Ljava/lang/String;)V";
    private static final String TRACE_END_METHOD = "end";
    private static final String TRACE_END_DESCRIPTOR = "()V";

    private final String className;
    private final String includedClassPrefix;
    private final int maxSectionNameLength;
    private boolean classExcluded;

    BusinessTraceClassVisitor(
            ClassVisitor nextClassVisitor,
            String className,
            String includedClassPrefix,
            int maxSectionNameLength
    ) {
        super(Opcodes.ASM9, nextClassVisitor);
        this.className = className;
        this.includedClassPrefix = includedClassPrefix;
        this.maxSectionNameLength = maxSectionNameLength;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (NO_BUSINESS_TRACE_DESCRIPTOR.equals(descriptor)) {
            classExcluded = true;
        }
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions
    ) {
        MethodVisitor nextMethodVisitor =
                super.visitMethod(access, name, descriptor, signature, exceptions);

        if (nextMethodVisitor == null || classExcluded || shouldSkipMethod(access, name)) {
            return nextMethodVisitor;
        }

        String sectionName = createSectionName(className, name, descriptor);
        return new BusinessTraceMethodVisitor(
                nextMethodVisitor,
                access,
                name,
                descriptor,
                sectionName
        );
    }

    private static boolean shouldSkipMethod(int access, String name) {
        int unsupportedAccess = Opcodes.ACC_ABSTRACT
                | Opcodes.ACC_NATIVE
                | Opcodes.ACC_SYNTHETIC
                | Opcodes.ACC_BRIDGE;
        return (access & unsupportedAccess) != 0 || "$jacocoInit".equals(name);
    }

    private String createSectionName(String fullClassName, String methodName, String descriptor) {
        String relativeClassName = fullClassName;
        if (fullClassName.startsWith(includedClassPrefix + ".")) {
            relativeClassName = fullClassName.substring(includedClassPrefix.length() + 1);
        }

        String arguments = Arrays.stream(Type.getArgumentTypes(descriptor))
                .map(BusinessTraceClassVisitor::readableTypeName)
                .collect(Collectors.joining(","));
        String sectionName = "BM:" + relativeClassName.replace('$', '.')
                + "#"
                + methodName
                + "("
                + arguments
                + ")";

        if (sectionName.length() <= maxSectionNameLength) {
            return sectionName;
        }
        return sectionName.substring(0, Math.max(0, maxSectionNameLength - 1)) + "…";
    }

    private static String readableTypeName(Type type) {
        if (type.getSort() == Type.ARRAY) {
            return readableTypeName(type.getElementType()) + "[]".repeat(type.getDimensions());
        }
        if (type.getSort() == Type.OBJECT) {
            String className = type.getClassName();
            int lastDot = className.lastIndexOf('.');
            return lastDot >= 0 ? className.substring(lastDot + 1) : className;
        }
        return type.getClassName();
    }

    private static final class BusinessTraceMethodVisitor extends AdviceAdapter {
        private final String sectionName;
        private final Label traceStart = new Label();
        private final Label traceEnd = new Label();
        private final Label traceExceptionHandler = new Label();
        private boolean methodExcluded;
        private boolean methodEntered;

        BusinessTraceMethodVisitor(
                MethodVisitor nextMethodVisitor,
                int access,
                String name,
                String descriptor,
                String sectionName
        ) {
            super(Opcodes.ASM9, nextMethodVisitor, access, name, descriptor);
            this.sectionName = sectionName;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (NO_BUSINESS_TRACE_DESCRIPTOR.equals(descriptor)) {
                methodExcluded = true;
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        protected void onMethodEnter() {
            if (methodExcluded) {
                return;
            }
            visitLabel(traceStart);
            visitLdcInsn(sectionName);
            visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    TRACE_OWNER,
                    TRACE_BEGIN_METHOD,
                    TRACE_BEGIN_DESCRIPTOR,
                    false
            );
            methodEntered = true;
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (methodEntered && opcode != Opcodes.ATHROW) {
                endTraceSection();
            }
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            if (methodEntered) {
                visitLabel(traceEnd);
                visitTryCatchBlock(traceStart, traceEnd, traceExceptionHandler, null);
                visitLabel(traceExceptionHandler);
                endTraceSection();
                visitInsn(Opcodes.ATHROW);
            }
            super.visitMaxs(maxStack, maxLocals);
        }

        private void endTraceSection() {
            visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    TRACE_OWNER,
                    TRACE_END_METHOD,
                    TRACE_END_DESCRIPTOR,
                    false
            );
        }
    }
}
