package com.rick.test.db;

import com.rick.db.repository.TableDAO;
import com.rick.test.module.db.fix.dao.FixRecordDAO;
import com.rick.test.module.db.fix.entity.FixRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TableDAO.deleteNotIn 超过 1000 个值的分批修复回归测试（对应 docs/troubleshooting.md 第 11 条）。
 * 修复前：值数 > 1000 直接抛 RuntimeException("SQL_PATH_NOT_IN in的个数不能超过1000")；
 * 修复后：单条语句内拆成多段 AND (col NOT IN (...))，每段 ≤ 1000。
 * 复用 P0FixTest 的专用表 t_p0_fix_record（@BeforeEach 建表 + 物理清场）。
 *
 * @author Rick.Xu
 */
@SpringBootTest
@Slf4j
public class DeleteNotInTest {

    private static final String TABLE = "t_p0_fix_record";

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
     * 1002 个值触发分段路径：r1.id 在第 1 段（index 0）、r2.id 在第 2 段（index 1001），
     * 两段都必须正确 AND 才能同时保住 r1/r2、只删 r3。修复前此行直接抛 RuntimeException。
     */
    @Test
    void testDeleteNotInOver1000Values() {
        FixRecord r1 = fixRecordDAO.insert(FixRecord.builder().name("keep-1").build());
        FixRecord r2 = fixRecordDAO.insert(FixRecord.builder().name("keep-2").build());
        FixRecord r3 = fixRecordDAO.insert(FixRecord.builder().name("gone").build());

        List<Object> values = new ArrayList<>();
        values.add(r1.getId());                       // 第 1 段
        for (long i = 1; i <= 1000; i++) {
            values.add(-i);                           // 负数假 id，不会命中雪花 id
        }
        values.add(r2.getId());                       // 第 2 段（index 1001）
        assertEquals(1002, values.size());

        int rows = tableDAO.deleteNotIn(TABLE, "id", values);

        assertEquals(1, rows);                        // 仅 r3 被删（ExtendTableDAOImpl 逻辑删除，影响 1 行）
        assertEquals(false, isDeleted(r1.getId()));
        assertEquals(false, isDeleted(r2.getId()));
        assertEquals(true, isDeleted(r3.getId()));
    }

    /**
     * 边界：恰好 1000 个值仍走原单段路径（size > IN_SIZE 才分段）。
     */
    @Test
    void testDeleteNotInExactly1000Values() {
        FixRecord r1 = fixRecordDAO.insert(FixRecord.builder().name("keep").build());
        FixRecord r2 = fixRecordDAO.insert(FixRecord.builder().name("gone").build());

        List<Object> values = new ArrayList<>();
        values.add(r1.getId());
        for (long i = 1; i <= 999; i++) {
            values.add(-i);
        }
        assertEquals(1000, values.size());

        int rows = tableDAO.deleteNotIn(TABLE, "id", values);

        assertEquals(1, rows);
        assertEquals(false, isDeleted(r1.getId()));
        assertEquals(true, isDeleted(r2.getId()));
    }

    private boolean isDeleted(Long id) {
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM t_p0_fix_record WHERE id = ?", Boolean.class, id);
        return Boolean.TRUE.equals(deleted);
    }
}
