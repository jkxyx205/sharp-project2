package com.rick.test;

import com.rick.test.module.db.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

/**
 * @author Rick.Xu
 * @date 2025/11/11 10:39
 */
public class SimpleTest {

    @Test
    public void testPrint() {
        System.out.println("hello world");
    }

    @Test
    public void testPrint2() {
//        CacheUtils.print1(); // java.lang.NoClassDefFoundError: com/github/benmanes/caffeine/cache/Caffeine
//        CacheUtils.print2(); // 可以打印，按需加载

        boolean present = ClassUtils.isPresent("com.devyean.erp.module.user.entity.User", getClass().getClassLoader());
        System.out.println(present);

        System.out.println(ClassLoader.getSystemClassLoader() == getClass().getClassLoader()); // AppClassLoader
    }

    @Test
    public void testClassLoader () {
        // AppClassLoader
        ClassLoader appClassLoader = ClassLoader.getSystemClassLoader();
        System.out.println("AppClassLoader: " + appClassLoader);

        // Platform/Ext ClassLoader
        ClassLoader platformClassLoader = appClassLoader.getParent();
        System.out.println("PlatformClassLoader: " + platformClassLoader);

        // Bootstrap ClassLoader
        ClassLoader bootstrapClassLoader = platformClassLoader.getParent();
        System.out.println("Bootstrap ClassLoader: " + bootstrapClassLoader);

        // 测试核心类加载器
        ClassLoader stringLoader = String.class.getClassLoader();
        System.out.println("String class loader: " + stringLoader); // null Bootstrap ClassLoader

        ClassLoader userLoader = User.class.getClassLoader();
        System.out.println("User class loader: " + userLoader); // AppClassLoader
    }

}
