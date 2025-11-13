package com.rick.test;

import com.rick.db.plugin.generator.TableGenerator;
import com.rick.test.module.db.user.entity.IdCard;
import com.rick.test.module.db.user.entity.Pet;
import com.rick.test.module.db.user.entity.Role;
import com.rick.test.module.db.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author Rick.Xu
 * @date 2025/11/10 16:27
 */
@SpringBootTest
public class TableGeneratorTest {

    @Autowired
    private TableGenerator tableGenerator;

    @Test
    public void testGeneratorTable() {
        tableGenerator.createTable(User.class);
        tableGenerator.createTable(IdCard.class);

        tableGenerator.createTable(Pet.class);
        tableGenerator.createTable(Role.class);
    }
}
