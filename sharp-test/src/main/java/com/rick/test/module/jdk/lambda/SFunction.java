package com.rick.test.module.jdk.lambda;

import java.io.Serializable;

public interface SFunction<T, R> extends Serializable {
    R apply(T t);
}