package com.rick.test.module.jdk.proxy;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author Rick.Xu
 * @date 2025/11/18 10:16
 */
@Slf4j
public class MapperInvocationHandler implements InvocationHandler {

    private final Object target;

    public MapperInvocationHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Objects.nonNull(target)) {
            log.info("被代理的对象{},代理对象的方法名{},参数列表{}", target.getClass(), method.getName(), args);
            return method.invoke(target, args);
        }

        // proxy 是类里信息
//        proxy.getClass().getInterfaces();
        return method.invoke((Mapper) o -> 900, args);
    }
}
