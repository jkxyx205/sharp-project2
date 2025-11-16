package com.rick.test.module.jdk.lambda;

import lombok.experimental.UtilityClass;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Rick.Xu
 * @date 2025/11/16 19:20
 */
@UtilityClass
public class LambdaTest {

    public <T, R> void mapGetName(Function<T, R> function) {
        // 抛出异常
        // Caused by: java.lang.NoSuchMethodException: com.devyean.erp.module.data.plant.Demo$$Lambda$25/0x0000000401005348.writeReplace()
        // Function 没有实现接口 Serializable
        System.out.println(getName(function));
    }

    public <T, R> void mapGetName2(SFunction<T, R> function) {
        System.out.println(getName(function));
    }

    public <T, R> void map(Function<T, R> function) {

    }

    public <T, R> void map(Function<T, R> function, T t) {
        R apply = function.apply(t);
        System.out.println(apply);
    }

    public <T> void println(Consumer<T> consumer) {

    }

    public <T> void println(Consumer<T> consumer, T t) {
        consumer.accept(t);
    }

    public <T> void optional(Supplier<T> supplier) {
        T t = supplier.get();
        System.out.println(t);
    }


    private String getName(Object function) {
        try {
            // 2. 获取 Lambda 表达式对应的 writeReplace 方法
            Method writeReplace = function.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);

            // 3. 得到 SerializedLambda
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(function);

            // implMethodName = "getName"
            String methodName = lambda.getImplMethodName();

            // 4. 去掉 get 前缀 → 字段名 name
            if (methodName.startsWith("get")) {
                methodName = methodName.substring(3);
            }
            methodName = Character.toLowerCase(methodName.charAt(0)) + methodName.substring(1);

            return methodName;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
