package com.rick.test.module.jdk.lambda;

import com.google.common.base.Function;

import java.util.List;

public class Demo {

    public static void main(String[] args) {
//        LambdaQueryWrapper<User> lambdaQuery = Wrappers.<User>lambdaQuery();
//        lambdaQuery.like(User::getName,"雨").lt(User::getAge,40);

        // JDK
        List<User> list = List.of(new User("Jim"), new User("Sherry"));
        list.stream().map(User::getName);
        list.forEach(System.out::print);

        // User::getName 有两种等价， 所有可以用于 Function 和 Consumer
        //  user -> user.getName()
        //  user -> {user.getName()}

        Function<User, String> fun1 = User::getName;
        Function<User, String> fun2 = user -> user.getName();

        // 功能上：✅ 等价 - 执行结果完全相同
        // 实现上：❌ 不等价 - 字节码、类型、性能都不同
//        | 维度 | 方法引用 | Lambda表达式 |
//        |-----|---------|------------|
//        | **功能** | ✅ 相同 | ✅ 相同 |
//        | **结果** | ✅ 相同 | ✅ 相同 |
//        | **字节码** | 直接引用 | 生成合成方法 |
//        | **性能** | 略快 | 略慢 |
//        | **可读性** | ✓ 好 | ✗ 差 |
//        | **方法名** | 可获取 | 不可获取 |

//        方法函数式接口传参：
//        1. 类方法引用 User::getName 构造器引用 User::new
//        2. User user= new User(); user::print;
//        3. 静态方法 User::score
//        4. 自定义实现 (a) -> b


        // function
        // 类方法引用
        LambdaTest.map(User::getName); // user -> user.getName
        LambdaTest.map(User::getName, new User("Rick")); // user -> user.getName()
//        LambdaTest.map(User::setAge); // 必须要有返回值，setAge 没有返回值

        // 静态方法实现
        LambdaTest.map(User::score);
        LambdaTest.map(User::score, 99);

        // 绑定实例实现
        User user = new User("Tom");
        LambdaTest.map(user::print, "HELLO");
//        LambdaTest.map(user::handler, "HELLO"); // handler 必须要有参数和返回值
//        LambdaTest.map(user::getName, "HELLO"); // getName 必须要有参数和返回值

        // 自定义实现 Lambda表达式
        LambdaTest.map(S -> S, "WORLD");
        LambdaTest.map(u -> u.getName(), new User("Ashly")); // user -> user.getName()
        // User 中的方法有参数，同时有返回值

        // consumer
        // 类方法引用
        LambdaTest.println(User::getName); // user -> {user.getName()}
        LambdaTest.println(User::getName, new User("Rick")); // User user -> { user.getName(); }
        LambdaTest.println(User::setAge); // user -> {user.getAge()}

        LambdaTest.println(User::score);
        LambdaTest.println(User::score, 99);

        // 绑定实例实现
        User user2 = new User("Tom");
        LambdaTest.println(user2::print, "HELLO"); // user2.print(args)
        LambdaTest.println(user2::handler); // user2.handler(args)
//        LambdaTest.println(user2::setAge); // setAge 必须要有参数 可以没有返回值

        // 自定义实现 Lambda表达式
        LambdaTest.println(S -> {}, "WORLD"); // 实现

        // Supplier
        // 1. 静态方法引用
        LambdaTest.optional(User::score2); // 实现
//        LambdaTest.optional(user2::setAge); // 不允许 setAge 没有返回值

        // 2. 绑定实例
        LambdaTest.optional(user2::getName);
        // 3. 构造器引用（最常用），必须有无参构造函数
        LambdaTest.optional(User::new);
        // 4. 自定义实现 Lambda表达式
        LambdaTest.optional(() -> new User("23"));
        // LambdaTest.optional(User::); 不允许通过 User::非静态方法 因为 Suppler 没有参数

        // 反射获取属性
        LambdaTest.mapGetName2(User::getName); // name
//        LambdaTest.mapGetName(User::getName); // Caused by: java.lang.NoSuchMethodException: com.devyean.erp.module.data.plant.Demo$$Lambda$25/0x0000000401005348.writeReplace()

    }
}