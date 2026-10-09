package com.rick.test.db;

import com.rick.db.repository.TableDAO;
import com.rick.db.repository.support.dialect.*;
import com.rick.test.module.db.fix.dao.FixRecordDAO;
import com.rick.test.module.db.fix.entity.FixRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * exists 系列硬编码 LIMIT 1 的方言化修复回归测试（对应 ARCHITECTURE.md §7 限制 2、troubleshooting.md 第 15 条）。
 * 修复前：EntityDAO.exists / TableDAO.exists 直接拼 " LIMIT 1"，Oracle/SQLServer 下必报语法错误；
 * 修复后：经 AbstractDialect.limitOneSql 按方言生成“取一行”SQL
 * （MySQL/PostgreSQL/SQLite: LIMIT 1；Oracle: ROWNUM 包裹；SQLServer: TOP 1 包裹）。
 * 复用 P0FixTest 的专用表 t_p0_fix_record（@BeforeEach 建表 + 物理清场）。
 *
 * @author Rick.Xu
 */
@SpringBootTest
@Slf4j
public class ExistsDialectTest {

    private static final String SQL = "SELECT 1 FROM t";

    @Autowired
    private FixRecordDAO fixRecordDAO;

    @Autowired
    private TableDAO tableDAO;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_p0_fix_record (" +
                "id int8 PRIMARY KEY, name varchar(255), " +
                "create_by int8, create_time timestamp NOT NULL, " +
                "update_by int8, update_time timestamp NOT NULL, " +
                "is_deleted bool NOT NULL DEFAULT false)");
        // 物理清场（jdbcTemplate 直连，不走 ExtendTableDAOImpl 的逻辑删除/is_deleted 过滤）
        jdbcTemplate.execute("DELETE FROM t_p0_fix_record");
    }

    /**
     * 纯字符串断言：各方言 limitOneSql 生成的“取一行”SQL（不依赖数据库）。
     */
    @Test
    void testLimitOneSqlPerDialect() {
        assertEquals(SQL + " LIMIT 1", new MySQL5Dialect().limitOneSql(SQL));
        assertEquals(SQL + " LIMIT 1", new MySQL8Dialect().limitOneSql(SQL));
        assertEquals(SQL + " LIMIT 1", new PostgresDialect().limitOneSql(SQL));
        assertEquals(SQL + " LIMIT 1", new SQLiteDialect().limitOneSql(SQL));
        assertEquals("SELECT * FROM (" + SQL + ") temp_ WHERE ROWNUM <= 1", new Oracle10gDialect().limitOneSql(SQL));
        assertEquals("SELECT * FROM (" + SQL + ") temp_ WHERE ROWNUM <= 1", new Oracle11cDialect().limitOneSql(SQL));
        assertEquals("SELECT TOP 1 * FROM (" + SQL + ") temp_", new SQLServer2012Dialect().limitOneSql(SQL));
    }

    /**
     * EntityDAO.exists 四个变体（id / condition+args / condition+paramMap / condition+example）
     * 与 TableDAO.exists 在 PostgreSQL 下的行为回归（修复后委托链变化，行为应不变）。
     */
    @Test
    void testExistsVariantsOnPostgres() {
        FixRecord r1 = fixRecordDAO.insert(FixRecord.builder().name("keep").build());

        assertTrue(fixRecordDAO.exists(r1.getId()));
        assertFalse(fixRecordDAO.exists(-999L));

        assertTrue(fixRecordDAO.exists("name = ?", "keep"));
        assertFalse(fixRecordDAO.exists("name = ?", "nope"));

        assertTrue(fixRecordDAO.exists("name = :name", Map.of("name", "keep")));
        assertFalse(fixRecordDAO.exists("name = :name", Map.of("name", "nope")));

        assertTrue(fixRecordDAO.exists("name = :name", FixRecord.builder().name("keep").build()));
        assertFalse(fixRecordDAO.exists("name = :name", FixRecord.builder().name("nope").build()));

        assertTrue(tableDAO.exists("SELECT id FROM t_p0_fix_record WHERE name = ?", "keep"));
        assertFalse(tableDAO.exists("SELECT id FROM t_p0_fix_record WHERE name = ?", "nope"));
    }

    /**
     * ExtendTableDAOImpl.exists 覆写：逻辑删除后的行 exists 必须为 false
     * （is_deleted 条件在方言包裹**前**注入原始 SQL，包裹后的嵌套子查询不再被识别为单表）。
     */
    @Test
    void testExistsRespectsLogicalDelete() {
        FixRecord r1 = fixRecordDAO.insert(FixRecord.builder().name("doomed").build());
        assertTrue(fixRecordDAO.exists(r1.getId()));

        fixRecordDAO.deleteById(r1.getId());   // ExtendTableDAOImpl → 逻辑删除

        assertFalse(fixRecordDAO.exists(r1.getId()));
        assertFalse(tableDAO.exists("SELECT id FROM t_p0_fix_record WHERE id = ?", r1.getId()));
    }
}
