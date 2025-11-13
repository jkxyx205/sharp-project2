package com.rick.test.module.db.user.service;

import com.rick.test.module.db.user.dao.IdCardDAO;
import com.rick.test.module.db.user.dao.UserDAO;
import com.rick.test.module.db.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Rick.Xu
 * @date 2025/11/10 19:04
 */
@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Validated
@Slf4j
public class UserService {

    UserDAO userDAO;

    IdCardDAO idCardDAO;

    public User saveOrUpdate(@Valid User user) {
        log.info("saveOrUpdate。。。。。。info");
        log.debug("saveOrUpdate。。。。。。debug");

        boolean insert = Objects.isNull(user.getId());
        userDAO.insertOrUpdate(user);

        if (insert) {
            // 一对一主键映射，需要在 service 中处理 insert 方法，sharp-database 会处理为更新
            idCardDAO.insert(user.getIdCard());
        }
        return user;
    }

    public Optional<User> selectById(@NotNull Long id) {
        return userDAO.selectById(id);
    }

    public Optional<User> selectById2(Long id) {
        return userDAO.selectById(id);
    }

    public void add(@NotBlank String orderNo) {
        log.info(orderNo);
    }
}