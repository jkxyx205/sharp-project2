package com.rick.common.http.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.rick.common.util.ClassUtils;
import com.rick.common.util.EnumUtils;
import com.rick.common.util.JsonUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static com.fasterxml.jackson.databind.node.JsonNodeType.OBJECT;
import static com.fasterxml.jackson.databind.node.JsonNodeType.STRING;

/**
 * EnumJsonDeserializer 已经被 EnumCustomizeDeserializer 替换，这个类在多层结构下，枚举会出现问题
 */
@Deprecated
public class EnumJsonDeserializer extends JsonDeserializer<Enum> {

    @Override
    public Enum deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        JsonNodeType nodeType = node.getNodeType();

        String code = node.asText();
        if (nodeType == OBJECT) {
            code = (String) JsonUtils.toObject(node.asOptional().get(), Map.class).get("code");
        } else if (nodeType == STRING) {
            code = node.asText();
        }

        JsonStreamContext parsingContext = jsonParser.getParsingContext();

        Field field = null;
        Enum value = null;
        try {
            if (parsingContext.hasCurrentName()) {
                Object currentValue = parsingContext.getCurrentValue();
                // 字段名
                String currentName = parsingContext.getCurrentName();

                field = ClassUtils.getField(currentValue.getClass(), currentName);
                value = EnumUtils.valueOfCode(field.getType(), code);
            } else if (parsingContext.getParent().hasCurrentName()) {
                Object currentValue = jsonParser.getParsingContext().getParent().getCurrentValue();
                String currentName = jsonParser.getParsingContext().getParent().getCurrentName();
                field = ClassUtils.getField(currentValue.getClass(), currentName);
                Class<?>[] fieldGenericClass = ClassUtils.getFieldGenericClass(field);
                value = EnumUtils.valueOfCode(fieldGenericClass[0], code);
            }

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "No enum constant " + field.getName() + "." + code);
        }

        if (value == null) {
            throw new IllegalArgumentException(
                    "No enum constant " + field.getName() + "." + code);
        }

        return value;
    }
}