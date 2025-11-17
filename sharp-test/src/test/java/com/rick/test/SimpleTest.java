package com.rick.test;

import com.rick.common.function.SFunction;
import com.rick.db.util.OperatorUtils;
import com.rick.test.module.db.user.entity.Pet;
import com.rick.test.module.db.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Map;

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

    @Test
    public void testOperatorUtils() {
        Pet pet1 = Pet.builder().id(1L).name("pet1").build();
        Pet pet2 = Pet.builder().id(2L).name("pet2").build();

        User user1 = User.builder().id(8L).name("Rick").build();
        pet1.setUser(user1);

        User user2 = User.builder().id(9L).name("Tom").build();
        pet2.setUser(user1);

        Map<String, List<Pet>> map1 = OperatorUtils.groupMap(List.of(pet1, pet2), Pet::getName);

        Map<Long, List<Pet>> map2 = OperatorUtils.groupMap(List.of(pet1, pet2), Pet::getUserId);
        Map<Long, List<Pet>> map3 = OperatorUtils.groupMap(List.of(pet1, pet2), (SFunction<Pet, Long>) pet -> pet.getUser().getId());

    }

}
