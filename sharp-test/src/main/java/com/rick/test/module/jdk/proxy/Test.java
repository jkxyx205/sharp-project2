package com.rick.test.module.jdk.proxy;

import com.rick.test.module.db.user.entity.User;

import java.lang.reflect.Proxy;

/**
 * @author Rick.Xu
 * @date 2025/11/18 10:18
 */
public class Test {
    public static void main(String[] args) {
//        Mapper mapper = new MapperImpl(); // 代理 MapperImpl
//
//        Mapper<User> mapperProxy = (Mapper) Proxy.newProxyInstance(
//                Mapper.class.getClassLoader(), // mapper.getClass().getClassLoader(),
////                mapper.getClass().getInterfaces(),
//                new Class[]{Mapper.class}, new MapperInvocationHandler(mapper));
//
//        int result = mapperProxy.save(User.builder().name("Tom").build());
//        System.out.println(result);


        Mapper<User> mapperProxy = (Mapper) Proxy.newProxyInstance(
                Mapper.class.getClassLoader(), // mapper.getClass().getClassLoader(),
//                mapper.getClass().getInterfaces(),
                new Class[]{Mapper.class}, new MapperInvocationHandler(null));

        int result = mapperProxy.save(User.builder().name("Tom").build());
        System.out.println(result);
    }
}
