package com.rick.test.db;

import com.rick.common.util.IdGenerator;
import com.rick.db.repository.EntityDAO;
import com.rick.db.repository.TableDAO;
import com.rick.db.repository.support.baseinfo.ExtendTableDAOImpl;
import com.rick.test.module.db.fix.dao.FixRecordDAO;
import com.rick.test.module.db.fix.entity.FixRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * sharp-database P0 修复回归测试（对应 docs/troubleshooting.md 第 22/27/28 条）：
 * <ol>
 *   <li>insertOrUpdateTable(list, deleteItem, consumer) 三参版必 NPE（refColumnName/refValue 为 null）→ 现正常回调将被删除的 id</li>
 *   <li>ExtendTableDAOImpl 在 ApplicationReadyEvent 前写入 NPE（tableNameDAOMap 无初始值）→ 现回落 EntityDAOManager 实时查找</li>
 *   <li>TableDAO.execute(ConnectionCallback) 吞 SQLException 返回 null、不参与 Spring 事务 → 现异常转译抛出、连接随事务回滚</li>
 *   <li>insertOrUpdate(List&lt;Map&gt;) TODO 静默返回空数组 → 现逐项保存、回填 id、返回每项影响行数</li>
 * </ol>
 * 专用表 t_p0_fix_record：@BeforeEach 建表 + 物理清场（绕过逻辑删除），不影响其他测试数据。
 *
 * @author Rick.Xu
 */
@SpringBootTest
@Slf4j
public class P0FixTest {

    private static final String TABLE = "t_p0_fix_record";

    @Autowired
    private FixRecordDAO fixRecordDAO;

    @Autowired
    private TableDAO tableDAO;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
     * 修复 1：三参 (list, deleteItem, consumer) 版曾必 NPE（"null = :refValue" + Map.of 拒 null）。
     * 现 refColumnName 为 null 时按全表范围解析将被删除的 id（与方法内 delete 路径语义一致）。
     */
    @Test
    void testInsertOrUpdateTableWithDeletedIdsConsumer() {
        FixRecord keep = FixRecord.builder().name("keep").build();
        fixRecordDAO.insertOrUpdateTable(List.of(keep));

        FixRecord gone1 = fixRecordDAO.insert(FixRecord.builder().name("gone1").build());
        FixRecord gone2 = fixRecordDAO.insert(FixRecord.builder().name("gone2").build());

        Collection<Long> deletedIds = new ArrayList<>();
        // 修复前：此行必抛 NullPointerException
        fixRecordDAO.insertOrUpdateTable(List.of(keep), true, deletedIds::addAll);

        assertEquals(2, deletedIds.size());
        assertTrue(deletedIds.containsAll(List.of(gone1.getId(), gone2.getId())));
        // 逻辑删除生效：单表查询自动过滤 is_deleted
        assertEquals(1, fixRecordDAO.selectAll().size());
        assertEquals(1, countBySql("SELECT count(*) FROM t_p0_fix_record WHERE id = ? AND is_deleted = true", gone1.getId()));
    }

    /**
     * 修复 2：ApplicationReadyEvent 之前 tableNameDAOMap 未填充（修复前字段无初始值，insert/delete 两处解引用均 NPE）。
     * 在容器真实 Bean 上临时置换为空 map，忠实模拟启动期（@PostConstruct/ApplicationRunner）写入场景；
     * 修复后回落 EntityDAOManager 实时查找：审计字段自动填充、逻辑删除即时生效。
     */
    @Test
    void testExtendTableDAOBeforeApplicationReady() {
        // TestConfig 注册的 tableDAO Bean 即 ExtendTableDAOImpl
        ExtendTableDAOImpl extendTableDAO = (ExtendTableDAOImpl) tableDAO;
        Object originalMap = ReflectionTestUtils.getField(extendTableDAO, "tableNameDAOMap");
        try {
            // 模拟启动期：map 尚未由 onApplicationReady 填充（修复前该字段为 null → 任何写入 NPE）
            ReflectionTestUtils.setField(extendTableDAO, "tableNameDAOMap", new ConcurrentHashMap<String, EntityDAO<?, ?>>());

            long id = IdGenerator.getSequenceId();
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("id", id);
            paramMap.put("name", "startup-insert");
            // 修复前：addInsertInfo 解引用 tableNameDAOMap → NPE；修复后：回落查找并自动填充审计字段
            extendTableDAO.insert(TABLE, "id, name, create_by, create_time, update_by, update_time, is_deleted", paramMap);

            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT create_by, is_deleted FROM t_p0_fix_record WHERE id = ?", id);
            assertEquals(1L, ((Number) row.get("create_by")).longValue());   // getUserId() 默认 1L，审计填充生效
            assertEquals(false, row.get("is_deleted"));

            // 修复前：delete 解引用 tableNameDAOMap → NPE；修复后：注册表已注册 → 逻辑删除
            int rows = extendTableDAO.delete(TABLE, "id = ?", id);
            assertEquals(1, rows);
            assertEquals(true, jdbcTemplate.queryForObject("SELECT is_deleted FROM t_p0_fix_record WHERE id = ?", Boolean.class, id));
        } finally {
            ReflectionTestUtils.setField(extendTableDAO, "tableNameDAOMap", originalMap);
        }
    }

    /**
     * 修复 3：execute(ConnectionCallback) 曾 printStackTrace 后返回 null，且用独立连接（autocommit）不参与 Spring 事务。
     */
    @Test
    void testExecuteConnectionCallback() {
        // 3a 异常不再被吞：SQLException 转译为 DataAccessException 抛出（修复前静默返回 null）
        assertThrows(DataAccessException.class, () ->
                tableDAO.execute((ConnectionCallback<Object>) con -> {
                    throw new SQLException("expected-boom");
                }));

        // 3b 参与 Spring 事务：事务内插入后回滚 → 数据不存在（修复前独立连接 autocommit，回滚后行仍在）
        long txId = 9990001L;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            tableDAO.execute((ConnectionCallback<Object>) con -> {
                con.createStatement().executeUpdate(insertSql(txId, "tx-rollback"));
                return null;
            });
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0, countBySql("SELECT count(*) FROM t_p0_fix_record WHERE id = ?", txId));

        // 3c 无事务时正常执行并落库
        long id = 9990002L;
        tableDAO.execute((ConnectionCallback<Object>) con -> {
            con.createStatement().executeUpdate(insertSql(id, "no-tx"));
            return null;
        });
        assertEquals(1, countBySql("SELECT count(*) FROM t_p0_fix_record WHERE id = ?", id));
    }

    /**
     * 修复 4：insertOrUpdate(List&lt;Map&gt;) 曾是 TODO 静默返回 new int[0]、不保存任何数据。
     * 现逐项委托单 Map 版：每项成功记 1 行，且 id 回填进 Map（单 Map 版既有语义，Map 必须可变）。
     */
    @Test
    void testInsertOrUpdateListOfMap() {
        Map<String, Object> m1 = new HashMap<>();
        m1.put("name", "batch-1");
        Map<String, Object> m2 = new HashMap<>();
        m2.put("name", "batch-2");
        List<Map<String, Object>> list = List.of(m1, m2);

        // 修复前：返回空数组且无任何数据落库
        int[] rows = fixRecordDAO.insertOrUpdate(list);

        assertArrayEquals(new int[]{1, 1}, rows);
        assertNotNull(m1.get("id"));
        assertNotNull(m2.get("id"));
        assertEquals(2, countBySql("SELECT count(*) FROM t_p0_fix_record WHERE is_deleted = false", (Object[]) null));

        List<Map<String, Object>> empty = List.of();
        assertArrayEquals(new int[0], fixRecordDAO.insertOrUpdate(empty));
    }

    private String insertSql(long id, String name) {
        return "INSERT INTO t_p0_fix_record (id, name, create_by, create_time, update_by, update_time, is_deleted) " +
                "VALUES (" + id + ", '" + name + "', 1, now(), 1, now(), false)";
    }

    private int countBySql(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}
