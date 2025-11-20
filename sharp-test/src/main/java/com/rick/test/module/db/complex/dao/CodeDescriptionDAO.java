package com.rick.test.module.db.complex.dao;

import com.rick.db.repository.support.category.CategoryEnumEntityCodeDAOImpl;
import com.rick.test.module.db.complex.entity.CodeDescription;
import org.springframework.stereotype.Repository;

/**
 * @author Rick.Xu
 * @date 2025/11/14 11:59
 */
@Repository
public class CodeDescriptionDAO extends CategoryEnumEntityCodeDAOImpl<CodeDescription, Long, CodeDescription.CategoryEnum> {

}