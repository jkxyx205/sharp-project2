package com.rick.common.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Rick.Xu
 * @date 2025/11/11 09:40
 */
@Slf4j
public class JsonUtilsTest {

    @Test
    public void testJson() {
        String json = JsonUtils.toJson(Map.of("name", "Rick.Xu", "age", 40));
        log.info(json);
    }
}
