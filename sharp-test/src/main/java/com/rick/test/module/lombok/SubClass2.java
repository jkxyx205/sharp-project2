package com.rick.test.module.lombok;

import lombok.experimental.SuperBuilder;

/**
 * @author Rick.Xu
 * @date 2025/11/18 15:30
 */
///Users/rick/Space/Workspace/sharp-project2/sharp-test/src/main/java/com/rick/test/module/lombok/SubClass2.java:9: error: cannot find symbol
//        @SuperBuilder
//        ^
//        symbol:   class MyData2Builder
//        location: class MyData2
    // MyData2 不使用 @SuperBuilder 注解就会有编译异常
@SuperBuilder
public class SubClass2 /*extends MyData2*/ {
    private String subName;
}
