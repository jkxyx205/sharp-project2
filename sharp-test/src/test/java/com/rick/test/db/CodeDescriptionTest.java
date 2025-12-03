package com.rick.test.db;

import com.rick.test.module.db.complex.dao.CodeDescriptionDAO;
import com.rick.test.module.db.complex.entity.CodeDescription;
import com.rick.test.module.db.complex.service.CodeDescriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

/**
 * @author Rick.Xu
 * @date 2023/5/28 15:07
 */
@SpringBootTest
public class CodeDescriptionTest {

    @Autowired
    private CodeDescriptionService codeDescriptionService;

    @Autowired
    private CodeDescriptionDAO codeDescriptionDAO;

    @Test
    public void testSave() {
        codeDescriptionService.saveAll(CodeDescription.CategoryEnum.MATERIAL, Arrays.asList(
                CodeDescription.builder().code("0001").description("物料组1").sort(0).build(),
                CodeDescription.builder().code("0002").description("物料组1").sort(0).build(),
                CodeDescription.builder().code("M2").description("物料组2").sort(1).build(),
                CodeDescription.builder().code("M3").description("物料组3").sort(2).build()
        ));
    }

    @Test
    public void testSave2() {
        codeDescriptionService.saveAll(CodeDescription.CategoryEnum.PURCHASING_ORG, Arrays.asList(
                CodeDescription.builder().code("0001").description("采购组织1").sort(0).build(),
                CodeDescription.builder().code("0002").description("采购组织2").sort(0).build(),
                CodeDescription.builder().code("PG2").description("采购组织2").sort(1).build(),
                CodeDescription.builder().code("P-G-9").description("采购组织9").sort(9).build()
        ));
    }

    @Test
    public void testSave3() {
        codeDescriptionService.saveAll(CodeDescription.CategoryEnum.PACKAGING, Arrays.asList(
                CodeDescription.builder().code("0001").description("包装组1").sort(0).build(),
                CodeDescription.builder().code("0002").description("包装组2").sort(0).build(),
                CodeDescription.builder().code("PKG1").description("包装组1").sort(1).build()
        ));
    }

    @Test
    public void testSave4() {
        codeDescriptionService.saveAll(CodeDescription.CategoryEnum.DIVISION, Arrays.asList(
                CodeDescription.builder().code("0001").description("商品组1").sort(0).build(),
                CodeDescription.builder().code("0002").description("商品组2").sort(0).build()
        ));
    }

    @Test
    public void testSave5() {
        codeDescriptionService.saveAll(CodeDescription.CategoryEnum.DIRECT_DISCOVERY, Arrays.asList(
                CodeDescription.builder().code("0001").description("直发现场理由1").sort(0).build(),
                CodeDescription.builder().code("0002").description("直发现场理由2").sort(0).build()
        ));
    }

    @Test
    public void testByCode() {
        codeDescriptionDAO.update(CodeDescription.builder().code("M2").description("物料组-22").category(CodeDescription.CategoryEnum.MATERIAL).sort(0).build());
    }

}
