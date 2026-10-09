package com.rick.test.db;

import com.rick.db.plugin.page.Grid;
import com.rick.db.plugin.page.GridService;
import com.rick.db.plugin.page.GridUtils;
import com.rick.db.plugin.page.PageModel;
import com.rick.test.module.db.fix.dao.FixRecordDAO;
import com.rick.test.module.db.fix.entity.FixRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grid sidx/sord 排序注入的模块侧兜底校验回归测试。
 * ORDER BY 是字符串拼接而非绑定参数；GridUtils.list 2/3 参重载与 AbstractTableGridService
 * 拿 sidx 自身当白名单（自我放行），真正的列白名单只有 4 参 GridUtils.list(..., sortableColumns)。
 * 兜底：GridService.query（pageSql/wrapSordString 的唯一运行时入口）校验
 * sidx 仅允许 [A-Za-z0-9_.]、sord 仅允许 [A-Za-z0-9_.,\s]（兼容方言生成的 "asc,temp_.id ASC" 稳定排序组合），
 * 不匹配直接抛 IllegalArgumentException。
 * 复用 P0FixTest 的专用表 t_p0_fix_record（@BeforeEach 建表 + 物理清场）。
 *
 * @author Rick.Xu
 */
@SpringBootTest
@Slf4j
public class GridSidxInjectionTest {

    private static final String LIST_SQL = "SELECT id, name FROM t_p0_fix_record";

    @Autowired
    private FixRecordDAO fixRecordDAO;

    @Autowired
    private GridService gridService;

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
     * 直接调 GridService.query：恶意 sidx/sord 在执行前被拒绝。
     */
    @Test
    void testMaliciousOrderParamsRejectedOnDirectQuery() {
        Map<String, Object> params = new HashMap<>();

        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, () ->
                gridService.query(LIST_SQL, new PageModel(1, 15, "id; DROP TABLE t_p0_fix_record", "asc"), params));
        assertTrue(e1.getMessage().contains("sidx"));

        // 无空格变体（绕过按空格拆分的 setOrderParams 后仍须被兜底拒绝）
        assertThrows(IllegalArgumentException.class, () ->
                gridService.query(LIST_SQL, new PageModel(1, 15, "name,(select(1))", "asc"), params));

        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () ->
                gridService.query(LIST_SQL, new PageModel(1, 15, "name", "asc; DROP TABLE t_p0_fix_record"), params));
        assertTrue(e2.getMessage().contains("sord"));
    }

    /**
     * 经 GridUtils.list 2 参重载（sidx 自我放行路径）：恶意 sidx 仍被兜底拒绝。
     */
    @Test
    void testMaliciousSidxRejectedViaGridUtils() {
        Map<String, Object> params = new HashMap<>();
        params.put(PageModel.PARAM_PAGE, 1);
        params.put(PageModel.PARAM_SIZE, 15);
        params.put(PageModel.PARAM_SIDX, "name;DROP TABLE t_p0_fix_record");
        params.put(PageModel.PARAM_SORD, "asc");

        assertThrows(IllegalArgumentException.class, () -> GridUtils.list(LIST_SQL, params));
    }

    /**
     * 合法排序不受影响：GridUtils 2 参（自我放行）、4 参（显式白名单）、直接 query 三条路径，
     * 含 MySQL 稳定排序组合 sord（"asc,temp_.id ASC" 形态）经 setOrderParams 生成后仍通过校验。
     */
    @Test
    void testLegitSortStillWorks() {
        fixRecordDAO.insert(FixRecord.builder().name("aaa").build());
        fixRecordDAO.insert(FixRecord.builder().name("bbb").build());
        fixRecordDAO.insert(FixRecord.builder().name("ccc").build());

        // 2 参：sidx 自我放行 → setOrderParams 转成 temp_.name + 方言 sord
        Map<String, Object> descParams = new HashMap<>();
        descParams.put(PageModel.PARAM_PAGE, 1);
        descParams.put(PageModel.PARAM_SIZE, 15);
        descParams.put(PageModel.PARAM_SIDX, "name");
        descParams.put(PageModel.PARAM_SORD, "desc");
        Grid<Map<String, Object>> desc = GridUtils.list(LIST_SQL, descParams);
        assertEquals(3, desc.getRecords());
        assertEquals("ccc", desc.getRows().get(0).get("name"));

        // 4 参显式白名单：命中才排序
        Map<String, Object> ascParams = new HashMap<>();
        ascParams.put(PageModel.PARAM_PAGE, 1);
        ascParams.put(PageModel.PARAM_SIZE, 15);
        ascParams.put(PageModel.PARAM_SIDX, "name");
        ascParams.put(PageModel.PARAM_SORD, "asc");
        Grid<Map<String, Object>> asc = GridUtils.list(LIST_SQL, ascParams, null, "name", "id");
        assertEquals("aaa", asc.getRows().get(0).get("name"));

        // 白名单未命中 → 不排序但不报错（原有语义）
        Grid<Map<String, Object>> notSortable = GridUtils.list(LIST_SQL, ascParams, null, "id");
        assertEquals(3, notSortable.getRecords());

        // 直接 query：原始 sidx/sord（不经 setOrderParams）
        Grid<Map<String, Object>> direct = gridService.query(LIST_SQL, new PageModel(1, 15, "name", "asc"), new HashMap<>());
        assertEquals("aaa", direct.getRows().get(0).get("name"));
    }
}
