package com.rick.test.module.jdk.proxy;

/**
 * @author Rick.Xu
 * @date 2025/11/18 10:27
 */
public class MapperImpl<T> implements Mapper<T> {

    @Override
    public int save(T o) {
        return 100;
    }
}
