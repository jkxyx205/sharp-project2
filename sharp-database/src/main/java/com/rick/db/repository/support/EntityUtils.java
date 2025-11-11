package com.rick.db.repository.support;

import com.rick.db.repository.model.BaseEntityInfo;
import com.rick.db.repository.model.BaseEntityInfoGetter;
import com.rick.db.repository.model.EntityId;
import lombok.experimental.UtilityClass;
import org.springframework.beans.BeanUtils;

import java.util.Objects;

/**
 * @author Rick.Xu
 * @date 2023/4/7 21:17
 */
@UtilityClass
public class EntityUtils {

    public void resetInfoFields(EntityId entity) {
        entity.setId(null);

        if (entity instanceof BaseEntityInfoGetter) {
            BaseEntityInfo baseEntityInfo = ((BaseEntityInfoGetter) entity).getBaseEntityInfo();
            if (Objects.nonNull(baseEntityInfo)) {
                baseEntityInfo.setCreateBy(null);
                baseEntityInfo.setCreateTime(null);
                baseEntityInfo.setUpdateBy(null);
                baseEntityInfo.setUpdateTime(null);
                baseEntityInfo.setDeleted(null);
            }
        }
    }

    public void copyPropertiesAndResetInfoFields(Object source, EntityId target) {
        copyPropertiesAndResetInfoFields(source, target, (String[])null);
    }

    public void copyPropertiesAndResetInfoFields(Object source, EntityId target, String... ignoreProperties) {
        BeanUtils.copyProperties(source, target, ignoreProperties);
        EntityUtils.resetInfoFields(target);
    }

    public boolean isEntityClass(Class<?> clazz) {
        return EntityId.class.isAssignableFrom(clazz);
    }
}