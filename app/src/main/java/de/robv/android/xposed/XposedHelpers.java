package de.robv.android.xposed;

import java.lang.reflect.Method;

public class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader classLoader) throws Throwable {
        return Class.forName(className, false, classLoader);
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) throws Throwable {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] == null ? Object.class : args[i].getClass();
        }
        Method method = clazz.getMethod(methodName, paramTypes);
        return method.invoke(null, args);
    }

    public static Object callMethod(Object instance, String methodName, Object... args) throws Throwable {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] == null ? Object.class : args[i].getClass();
        }
        Method method = instance.getClass().getMethod(methodName, paramTypes);
        return method.invoke(instance, args);
    }
}
