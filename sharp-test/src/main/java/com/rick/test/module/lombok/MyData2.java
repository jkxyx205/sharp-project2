package com.rick.test.module.lombok;

import lombok.Builder;

/**
 * @author Rick.Xu
 * @date 2025/11/18 15:13
 */
@Builder(builderMethodName="br", buildMethodName = "bd", setterPrefix = "s", builderClassName = "t", toBuilder = true)
public class MyData2 {

    int id;
}
