package com.rick.test.util;

import com.rick.common.util.ClassUtils;
import com.rick.db.repository.model.EntityId;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Rick.Xu
 * @date 2025/11/16 19:00
 */
@UtilityClass
public class TestHelper {

    public void sortList(Object object) {
        if (Objects.isNull(object)) {
            return;
        }

        if (BeanUtils.isSimpleValueType(object.getClass())) {
            return;
        }

        if (List.class.isAssignableFrom(object.getClass())) {
            // 如果是集合

            List list = (List)object;
            if (CollectionUtils.isNotEmpty(list)) {
                Object o = list.get(0);
                if (EntityId.class.isAssignableFrom(o.getClass())) {
                    Collections.sort((List) object, (o1, o2) -> ((EntityId<?>) o1).getId().hashCode() - ((EntityId) o2).getId().hashCode());
                }
            }

            return;
        }

        Class clazz = object.getClass();
        Field[] allFields = ClassUtils.getAllFields(clazz);

        for (Field field : allFields) {
            field.setAccessible(true);
            Object value = ReflectionUtils.getField(field, object);
            if (Objects.isNull(value)) {
                continue;
            }

            if (List.class.isAssignableFrom(field.getType())) {
                if (value.getClass() != ArrayList.class) {
                    List list = new ArrayList((List)value);
                    ReflectionUtils.setField(field, object, list);
                    value = list;
                }

                sortList(value);
            }
        }
    }
}
