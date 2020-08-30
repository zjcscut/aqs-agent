package cn.throwx.lock.support;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import lombok.NonNull;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * @author throwable
 * @since 2020/8/30 20:34
 */
public class AqsAgent {

    private static final byte[] NO_TRANSFORM = null;

    public static void premain(final String agentArgs, @NonNull final Instrumentation inst) {
        inst.addTransformer(new LockSupportClassFileTransformer(), true);
    }

    private static class LockSupportClassFileTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader,
                                String classFileName,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) throws IllegalClassFormatException {
            String className = toClassName(classFileName);
            if (className.contains("concurrent")) {
                System.out.println("正K在处理:" + className);
            }
            if (className.equals("java.util.concurrent.locks.AbstractQueuedSynchronizer")) {
                return processTransform(loader, classfileBuffer);
            }
            return NO_TRANSFORM;
        }
    }

    private static byte[] processTransform(ClassLoader loader, byte[] classfileBuffer) {
        try {
            final ClassPool classPool = new ClassPool(true);
            if (loader == null) {
                classPool.appendClassPath(new LoaderClassPath(ClassLoader.getSystemClassLoader()));
            } else {
                classPool.appendClassPath(new LoaderClassPath(loader));
            }
            final CtClass clazz = classPool.makeClass(new ByteArrayInputStream(classfileBuffer), false);
            clazz.defrost();
            final CtClass paramClass = clazz.getClassPool().get("java.util.concurrent.locks.AbstractQueuedSynchronizer$Node");
            final CtMethod unparkMethod = clazz.getDeclaredMethod("unparkSuccessor", new CtClass[]{paramClass});
            unparkMethod.insertBefore("{java.lang.Object x = $1;\n" +
                    "            java.lang.reflect.Field nextField = Class.forName(\"java.util.concurrent.locks.AbstractQueuedSynchronizer$Node\").getDeclaredField(\"next\");\n" +
                    "            java.lang.reflect.Field threadField = Class.forName(\"java.util.concurrent.locks.AbstractQueuedSynchronizer$Node\").getDeclaredField(\"thread\");\n" +
                    "            nextField.setAccessible(true);\n" +
                    "            threadField.setAccessible(true);\n" +
                    "            java.lang.Object next = nextField.get(x);\n" +
                    "            if (null != next){" +
                    "java.lang.Object thread = threadField.get(next);\n" +
                    "System.out.println(\"当前解除阻塞的线程名称为:\"+ thread);\n" +
                    "}\n" +
                    "}");
            return clazz.toBytecode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toClassName(@NonNull final String classFileName) {
        return classFileName.replace('/', '.');
    }
}
