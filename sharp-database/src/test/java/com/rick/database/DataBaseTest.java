package com.rick.database;

import com.rick.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Rick.Xu
 * @date 2025/11/11 12:00
 */
public class DataBaseTest {

    @Test
    public void testDatabase() {
        String json = JsonUtils.toJson(Map.of("hello", "World"));
        System.out.println(json);
    }
}
