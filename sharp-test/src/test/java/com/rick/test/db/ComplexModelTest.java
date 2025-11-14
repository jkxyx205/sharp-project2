package com.rick.test.db;

import com.rick.db.repository.EntityDAO;
import com.rick.meta.dict.model.DictValue;
import com.rick.meta.dict.service.DictUtils;
import com.rick.test.module.db.complex.entity.CodeDescription;
import com.rick.test.module.db.complex.entity.ComplexModel;
import com.rick.test.module.db.complex.model.EmbeddedValue;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Rick.Xu
 * @date 2024/8/18 04:29
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ComplexModelTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityDAO<ComplexModel, Long> complexModelDAO;

    @Test
    @Order(1)
    public void testComplexModel() {
        List<Map<String, Object>> attachmentList = new ArrayList<>();
        HashMap<String, Object> attachment1 = new HashMap<>();
        attachment1.put("url", "baidu.com");
        attachment1.put("name", "picture");
        attachmentList.add(attachment1);

        List<List<String>> schoolExperienceList = new ArrayList<>();
        schoolExperienceList.add(Arrays.asList("2023-11-11", "苏州大学"));
        schoolExperienceList.add(Arrays.asList("2019-11-11", "苏州中学"));

        complexModelDAO.insert(ComplexModel.builder()
                .id(856759492044419072L)
                .name("Rick2")
                .age(34)
                .birthday(LocalDate.of(2021, 12, 26))
                .categoryList(Arrays.asList(CodeDescription.CategoryEnum.MATERIAL, CodeDescription.CategoryEnum.PURCHASING_ORG))
                .marriage(true)
                .attachmentList(attachmentList)
                .schoolExperienceList(schoolExperienceList)
                .map(attachment1)
                .materialType(new DictValue("HIBE"))
                .unit(new DictValue("EA"))
                .categoryDict(new DictValue("SALES_ORG"))
                .categoryDictList(Arrays.asList(new DictValue("MATERIAL"), new DictValue("SALES_ORG")))
                .embeddedValue(new EmbeddedValue(new DictValue("HIBE"), "texg"))
                .category(CodeDescription.CategoryEnum.MATERIAL)
                .workStatus(ComplexModel.WorkStatusEnum.FINISHED).build());
    }

    @Test
    @Order(2)
    public void testSelectComplexModel() {
        Optional<ComplexModel> optional = complexModelDAO.selectById(856759492044419072L);
        ComplexModel complexModel = optional.get();
        System.out.println(complexModel);
        // 获取label
//        complexModel.setEmbeddedValue(new EmbeddedValue(new DictValue("HIBE"), "texg"));
        DictUtils.fillDictLabel(complexModel);
        System.out.println(complexModel);

        allFieldsNotNull(complexModel);
    }

    @AfterAll
    public void afterAll() {
        jdbcTemplate.execute("truncate table t_complex_model");
    }

    // ========== 方案1: 使用反射检查所有字段 (推荐) ==========
    /**
     * 判断对象的所有字段是否都不为 null
     * @param obj 待检查的对象
     * @return true - 所有字段都不为null, false - 存在null字段
     */
    private void allFieldsNotNull(Object obj) {
        // 获取所有声明的字段（包括private）
        Field[] fields = obj.getClass().getDeclaredFields();

        for (Field field : fields) {
            // 跳过静态字段和瞬态字段
            if (Modifier.isStatic(field.getModifiers()) ||
                    Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            try {
                field.setAccessible(true);  // 允许访问私有字段
                Object value = field.get(obj);

                assertNotNull(value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("无法访问字段: " + field.getName(), e);
            }
        }
    }
}
