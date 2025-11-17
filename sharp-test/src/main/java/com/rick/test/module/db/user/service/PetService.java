package com.rick.test.module.db.user.service;

import com.rick.db.repository.EntityDAO;
import com.rick.db.repository.EntityDAOSupport;
import com.rick.test.module.db.user.dao.IdCardDAO;
import com.rick.test.module.db.user.entity.Pet;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

/**
 * @author Rick.Xu
 * @date 2025/11/14 08:46
 */
@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Validated
public class PetService {

    EntityDAOSupport entityDAOSupport;

    IdCardDAO idCardDAO;

    @Transactional(rollbackFor = Exception.class)
    public Pet saveOrUpdate(Pet pet) {
        EntityDAO<Pet, Long> petDAO = entityDAOSupport.getEntityDAO(Pet.class);

        boolean insert = Objects.isNull(pet.getId());
        petDAO.insertOrUpdate(pet);

        if (insert) {
            // 一对一主键映射，需要在 service 中处理 insert 方法，sharp-database 会处理为更新
            idCardDAO.insert(pet.getUser().getIdCard());
        }

        return pet;
    }

}