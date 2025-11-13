package com.rick.test.module.db.user.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.test.module.db.user.entity.User;
import org.springframework.stereotype.Repository;

/**
 * @author Rick.Xu
 * @date 2025/11/10 16:32
 */
@Repository
public class UserDAO extends EntityDAOImpl<User, Long> {

}