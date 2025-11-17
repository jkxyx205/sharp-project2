package com.rick.test.db;

import com.rick.db.repository.EntityDAO;
import com.rick.db.repository.EntityDAOSupport;
import com.rick.db.repository.support.EntityUtils;
import com.rick.test.BaseTest;
import com.rick.test.module.db.user.dao.RoleDAO;
import com.rick.test.module.db.user.entity.IdCard;
import com.rick.test.module.db.user.entity.Pet;
import com.rick.test.module.db.user.entity.Role;
import com.rick.test.module.db.user.entity.User;
import com.rick.test.module.db.user.select.UserSelect;
import com.rick.test.module.db.user.service.PetService;
import com.rick.test.module.db.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * @author Rick.Xu
 * @date 2025/11/10 18:50
 */
@Slf4j
public class UserTest extends BaseTest<UserService, User, Long> {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    EntityDAOSupport entityDAOSupport;

    @Autowired
    private RoleDAO roleDAO;

    @Autowired
    PetService petService;

    private Long userId;

    public UserTest(@Autowired UserService baseService) {
        super(baseService);
    }

    @Test
    @Order(1)
    public void testUserInsert() {
// 如果设置级联更新 Role，需要 Role 信息， 否则只需要 role#id
//        List<Role> roleList = List.of(Role.builder().name("SYSTEM-1").build(), Role.builder().name("ADMIN-1").build());

        List<Role> roleList = List.of(Role.builder().name("SYSTEM").id(1020361648726110208L).build(), Role.builder().name("ADMIN").id(102036743143936256L).build());
        for (Role role : roleList) {
            roleDAO.insert(role);
        }

        User user = User.builder()
                .name("Tom")
                .nameList(List.of("张三", "李四"))
                .idCard(IdCard.builder().code("321283197719123452").build())
                .petList(List.of(Pet.builder().name("Tom").build(), Pet.builder().name("Jerry").build()))
                .roleList(roleList)
                .build();

        baseService.insertOrUpdate(user);
        userId = user.getId();
        log.info("user id: {}", userId);
    }

    @Test
    @Order(2)
    public void testPetInsert() {
        List<Role> roleList = roleDAO.selectAll();
        User user = User.builder()
                .name("Tom-2")
                .nameList(List.of("张三-2", "李四-2"))
                .idCard(IdCard.builder().code("321283197719123453").build())
                .roleList(roleList)
                .build();

        Pet pig = Pet.builder().name("pig-2").user(user).build();
        petService.saveOrUpdate(pig);
    }

    @Test
    @Order(2)
    public void testUserUpdate() {
        Optional<User> optional = baseService.selectById(userId);
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

        baseService.insertOrUpdate(user);
    }

    @Test
    @Order(3)
    public void testSelect() {
        Optional<User> optional = baseService.selectById(userId);
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
    public void testSelect2() {
        List<User> userList = entityDAO.select(Map.of("id", userId));
        assertEquals(1, userList.size());
    }

    @Test
    @Order(5)
    public void testSelect3() {
        List<User> userList = entityDAO.select(Map.of("id", userId));
        User user = userList.get(0);

        user.getIdCard().toString();

        UserSelect select = new UserSelect();
        BeanUtils.copyProperties(user, select);

        select.getIdCard().setCode(null);
        for (Role role : select.getRoleList()) {
            role.setUserList(null);
            role.setName(null);
        }

        for (Pet pet : select.getPetList()) {
            pet.setUser(null);
            pet.setName(null);
        }

        entityDAOSupport.select(select);

        List<UserSelect> userSelectList1 = entityDAOSupport.select(UserSelect.class, "select id from t_user where id = :id", Map.of("id", userId));
        List<UserSelect> userSelectList2 = entityDAOSupport.select(UserSelect.class, "select id from t_user where id = ?", userId);
        assertEquals(true, EqualsBuilder.reflectionEquals(userSelectList1.get(0), userSelectList2.get(0)));
    }

    @Test
    @Order(6)
    public void testUserDelete() {
        int count = entityDAO.deleteById(userId);
        assertEquals(1, count, "删除不成功");

        Optional<User> optional = baseService.selectById(userId);
        assertEquals(true, optional.isEmpty());
    }

    @Test
    @Order(100)
    public void testInsertOrUpdate() {
// 如果设置级联更新 Role，需要 Role 信息， 否则只需要 role#id
//        List<Role> roleList = List.of(Role.builder().name("SYSTEM-1").build(), Role.builder().name("ADMIN-1").build());

        List<Role> roleList = List.of(Role.builder().name("SYSTEM-2").id(1020361648726110201L).build(), Role.builder().name("ADMIN-2").id(102036743143936251L).build());
        for (Role role : roleList) {
            roleDAO.insert(role);
        }

        User user = User.builder()
                .name("Tom")
                .nameList(List.of("张三", "李四"))
                .idCard(IdCard.builder().code("321283197719123452q").build())
                .petList(List.of(Pet.builder().name("Tom").build(), Pet.builder().name("Jerry").build()))
                .roleList(roleList)
                .build();

        super.testInsertOrUpdate(user);
    }

    @AfterAll
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
