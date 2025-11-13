package com.rick.test.db;

import com.rick.db.repository.EntityDAO;
import com.rick.db.repository.support.EntityDAOSupport;
import com.rick.db.repository.support.EntityUtils;
import com.rick.test.module.db.user.dao.RoleDAO;
import com.rick.test.module.db.user.dao.UserDAO;
import com.rick.test.module.db.user.entity.IdCard;
import com.rick.test.module.db.user.entity.Pet;
import com.rick.test.module.db.user.entity.Role;
import com.rick.test.module.db.user.entity.User;
import com.rick.test.module.db.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * @author Rick.Xu
 * @date 2025/11/10 18:50
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class UserTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    EntityDAOSupport entityDAOSupport;

    private Long userId;

    @Autowired
    private RoleDAO roleDAO;

    @Test
    @Order(1)
    public void testUserInsert() {
        List<Role> roleList = List.of(Role.builder().name("SYSTEM").build(), Role.builder().name("ADMIN").build());
        roleDAO.insertOrUpdate(roleList);

        User user = User.builder()
                .name("Tom")
                .nameList(List.of("张三", "李四"))
                .idCard(IdCard.builder().code("321283197719123452").build())
                .petList(List.of(Pet.builder().name("Tom").build(), Pet.builder().name("Jerry").build()))
                .roleList(roleList)
                .build();

        userService.saveOrUpdate(user);
        userId = user.getId();
        log.info("user id: {}", userId);
    }

    @Test
    @Order(2)
    public void testUserUpdate() {
        Optional<User> optional = userService.selectById(userId);
        List<Pet> petList = optional.get().getPetList();
        for (Pet pet : petList) {
            pet.setName(pet.getName() + "-1");
        }

        User user = User.builder()
                .id(userId)
                .name("Tom-1")
                .nameList(List.of("张三-1", "李四-1"))
                .idCard(IdCard.builder().code("321283197719123452-1").build())
                .petList(petList)
                .roleList(optional.get().getRoleList())
                .build();

        userService.saveOrUpdate(user);
    }

    @Test
    @Order(3)
    public void testSelect() {
        Optional<User> optional = userService.selectById(userId);
        User user = optional.get();
//        System.out.println(JsonUtils.toJson(user));

        assertEquals("Tom-1", user.getName(), "名字修改不成功");
        assertEquals(user.getId(), user.getIdCard().getId(), "主键映射不正确");
        assertEquals("321283197719123452-1", user.getIdCard().getCode(), "名字修改不成功");
        assertEquals(2, user.getPetList().size(), "获取宠物信息失败");
        assertEquals(true, user.getPetList().stream().anyMatch(pet -> pet.getName().equals("Tom-1")), "获取宠物信息失败");

        EntityUtils.resetInfoFields(user);

        assertNull(user.getId());
//        System.out.println(JsonUtils.toJson(user));

        EntityDAO<Pet, Long> petDAO = entityDAOSupport.getEntityDAO(Pet.class);

        List<Pet> petList = petDAO.select("user_id = ?", userId);

        assertEquals(2, petList.size(), "获取宠物信息失败");
        assertEquals(true, petList.stream().anyMatch(pet -> pet.getName().equals("Tom-1")), "获取宠物信息失败");

        assertEquals(2, user.getRoleList().size());
        assertEquals(true, user.getRoleList().stream().anyMatch(role -> role.getName().equals("ADMIN")), "获取角色信息失败");

    }

    @Test
    @Order(4)
    public void testUserDelete() {
        int count = userDAO.deleteById(userId);
        assertEquals(1, count, "删除不成功");

        Optional<User> optional = userService.selectById(userId);
        assertEquals(true, optional.isEmpty());
    }

//    @AfterAll
    public void afterAll() {
        jdbcTemplate.execute("""
            truncate table t_user;
            truncate table t_id_card;
            truncate table t_pet;
            truncate table t_role;
            truncate table t_user_role;
""");
    }
}
