package com.rick.test;

import com.rick.db.plugin.BaseServiceImpl;
import com.rick.db.repository.EntityDAO;
import com.rick.db.repository.TableDAO;
import com.rick.db.repository.model.EntityId;
import com.rick.test.util.TestHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Rick.Xu
 * @date 2025/11/14 16:23
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class BaseTest<S extends BaseServiceImpl<? extends EntityDAO<T, ID>, T, ID>, T extends EntityId<ID>, ID> {

    protected ID id;

    private T entity;

    protected final EntityDAO<T, ID> entityDAO;

    protected final S baseService;

    protected final Class<T> entityClass;

    @Resource
    protected TableDAO tableDAO;

    public BaseTest(S baseService) {
        this.baseService = baseService;
        this.entityDAO = baseService.getBaseDAO();
        this.entityClass = entityDAO.getTableMeta().getEntityClass();
    }

    protected void testInsertOrUpdate(T t) {
        baseService.insertOrUpdate(t);
        id = t.getId();
        entity = t;
        log.info("【{}】id = {}", "testInsertOrUpdate", t.getId());

        testUpdate(t);
    }

    private void testUpdate(T t) {
        baseService.update(t);
        log.info("【{}】id = {}", "testUpdate", t.getId());
    }

    @Test
    @Order(Integer.MAX_VALUE - 1)
    public void testSelectById() {
        T t = baseService.selectById(id).get();
        TestHelper.sortList(t);
        TestHelper.sortList((entity));
        assertEquals(true, EqualsBuilder.reflectionEquals(t, entity, "baseEntityInfo"));
        log.info("【{}】id = {}", "testSelectById", t.getId());
    }

    @Test
    @Order(Integer.MAX_VALUE)
    public void testDeleteById() {
        assertEquals(1, baseService.deleteById(id));
        log.info("【{}】deleted = {}", "testDeleteById", true);
    }


}
