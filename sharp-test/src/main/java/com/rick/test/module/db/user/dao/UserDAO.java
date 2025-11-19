package com.rick.test.module.db.user.dao;

import com.rick.db.repository.EntityDAO;
import com.rick.db.repository.EntityDAOImpl;
import com.rick.test.module.db.user.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Collection;

/**
 * @author Rick.Xu
 * @date 2025/11/10 16:32
 */
@Repository
public class UserDAO extends EntityDAOImpl<User, Long> {

    /**
     * @OneToMany 中 cascadeSaveItemDeleteCheck = true，会执行
     * @param referenceDAO
     * @param deletedIds
     */
    @Override
    protected void itemDeletedCheckCallback(EntityDAO referenceDAO, Collection<Long> deletedIds) {
        super.itemDeletedCheckCallback(referenceDAO, deletedIds);
    }
}