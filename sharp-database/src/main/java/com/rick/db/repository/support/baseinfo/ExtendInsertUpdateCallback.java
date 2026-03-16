package com.rick.db.repository.support.baseinfo;

import com.rick.db.repository.model.BaseCodeEntity;
import com.rick.db.repository.model.BaseEntity;
import com.rick.db.repository.model.BaseEntityInfo;
import com.rick.db.repository.model.EntityId;
import com.rick.db.repository.support.InsertUpdateCallback;

import java.time.LocalDateTime;
import java.util.Map;

import static com.rick.db.repository.support.Constants.*;

/**
 * @author Rick.Xu
 * @date 2025/11/17 13:18
 */
public class ExtendInsertUpdateCallback implements InsertUpdateCallback<EntityId<Long>, Long> {

    @Override
    public void handler(boolean insert, EntityId<Long> entity, Map<String, Object> args) {
        BaseEntityInfo baseEntityInfo = new BaseEntityInfo();
        if (insert) {
            baseEntityInfo.setCreateBy((Long) args.get(CREATE_ID_COLUMN_NAME));
            baseEntityInfo.setCreateTime((LocalDateTime) args.get(CREATE_TIME_COLUMN_NAME));
            baseEntityInfo.setUpdateBy((Long) args.get(UPDATE_ID_COLUMN_NAME));
            baseEntityInfo.setUpdateTime((LocalDateTime) args.get(UPDATE_TIME_COLUMN_NAME));
            baseEntityInfo.setDeleted((Boolean) args.get(LOGIC_DELETE_COLUMN_NAME));
        } else {
            baseEntityInfo.setCreateBy((Long) args.get("baseEntityInfo.createBy"));
            baseEntityInfo.setCreateTime((LocalDateTime) args.get("baseEntityInfo.createTime"));
            baseEntityInfo.setUpdateBy((Long) args.get("baseEntityInfo.updateBy"));
            baseEntityInfo.setUpdateTime((LocalDateTime) args.get("baseEntityInfo.updateTime"));
            baseEntityInfo.setDeleted((Boolean) args.get("baseEntityInfo.deleted"));
        }

        if (entity instanceof BaseEntity baseEntity) {
            baseEntity.setBaseEntityInfo(baseEntityInfo);
        } else if (entity instanceof BaseCodeEntity baseCodeEntity) {
            baseCodeEntity.setBaseEntityInfo(baseEntityInfo);
        }
    }

}
