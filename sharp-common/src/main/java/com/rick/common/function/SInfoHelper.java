package com.rick.common.function;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rick.Xu
 * @date 2025/11/17 10:29
 */
class SInfoHelper {

    private static final Map<String, Class<?>> PRIMITIVE_MAP = new HashMap<>();
    static {
        PRIMITIVE_MAP.put("Z", boolean.class);
        PRIMITIVE_MAP.put("B", byte.class);
        PRIMITIVE_MAP.put("C", char.class);
        PRIMITIVE_MAP.put("S", short.class);
        PRIMITIVE_MAP.put("I", int.class);
        PRIMITIVE_MAP.put("J", long.class);
        PRIMITIVE_MAP.put("F", float.class);
        PRIMITIVE_MAP.put("D", double.class);
        PRIMITIVE_MAP.put("V", void.class);
    }

    // 获取属性的类型
    static Class<?> getPropertyType(Object lambdaObject) {
        // 2. 获取属性类型
        try {
            return getReturnClass(getSerializedLambda(lambdaObject));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    // 获取方法名
    static String getMethodName(Object lambdaObject) {
        try {
            return getSerializedLambda(lambdaObject).getImplMethodName();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 获取属性名
    static String getPropertyName(Object lambdaObject) {
        String methodName = getMethodName(lambdaObject);
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String name = methodName.substring(3);
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return methodName;
    }

    static boolean isMethodReference(Object lambdaObject) {
        try {
            SerializedLambda lambda = getSerializedLambda(lambdaObject);
            String methodName = lambda.getImplMethodName();
            int kind = lambda.getImplMethodKind();
            // 方法引用的特征：
            // 1. 方法名不以 lambda$ 开头
            // 2. implMethodKind 为 5(虚方法) 或 6(静态) 或 7(特殊)
            return !methodName.startsWith("lambda$") &&
                    (kind == 5 || kind == 6 || kind == 7);
        } catch (Exception e) {
            return false;
        }
    }

    private static SerializedLambda getSerializedLambda(Object lambdaObject) {
        try {
            Method method = lambdaObject.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) method.invoke(lambdaObject);
            return lambda;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> getReturnClass(SerializedLambda lambda) {
        String instantiatedMethodType = lambda.getInstantiatedMethodType();
        String returnTypeStr = instantiatedMethodType
                .substring(instantiatedMethodType.indexOf(')') + 1);

        // 基本类型直接查表
        if (PRIMITIVE_MAP.containsKey(returnTypeStr)) {
            return PRIMITIVE_MAP.get(returnTypeStr);
        }

        String className = returnTypeStr
                .replace("/", ".")
                .replaceAll("^L", "")
                .replaceAll(";$", "");

        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("无法解析返回值类型: " + className, e);
        }
    }
}
