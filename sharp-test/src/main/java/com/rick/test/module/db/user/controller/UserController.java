package com.rick.test.module.db.user.controller;

import com.rick.test.module.db.user.entity.Pet;
import com.rick.test.module.db.user.entity.Role;
import com.rick.test.module.db.user.entity.User;
import com.rick.test.module.db.user.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author Rick.Xu
 * @date 2025/11/11 15:41
 */
@RestController
@RequestMapping("users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User", description = "用户管理相关接口")
public class UserController {

    UserService userService;

    /**
     {
         "name": "Rick",
         "nameList": [
            "Tom"
         ],
         "idCard": {
            "code": "nTfMtZ6cBJlTRnhW0"
         },
         "petList": [
             {
                 "name": "123"
             }
         ],
         "roleIds": ["1020361648726110208"]
     }
     * @param user
     * @return
     */
    @PostMapping
    @Operation(
            summary = "保存用户信息",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(schema = @Schema(implementation = UserSchema.class))
            )
    )
    public User saveUser(@Valid @RequestBody User user) {
        userService.saveOrUpdate(user);
        for (Role role : user.getRoleList()) {
            role.setUserList(null);
        }

        for (Pet pet : user.getPetList()) {
            pet.setUser(null);
        }
        return user;
    }

    @Hidden
    @GetMapping
    public User getUser(User user) {
        return user;
    }

    @GetMapping("{id}")
    @Operation(
            summary = "根据 id 获取用户详情",
            parameters = {
//                    @Parameter(name = "id", description = "用户id", required = true, in = ParameterIn.PATH, example = "1020367431169765376")
            }
    )
    public User getUserById(@Parameter(
            description = "用户id",
            example = "1020367431169765376"// Swagger UI 示例值
    ) @PathVariable Long id) {
        User user = userService.selectById(id).get();
        for (Role role : user.getRoleList()) {
            role.setUserList(null);
        }

        for (Pet pet : user.getPetList()) {
            pet.setUser(null);
        }

        return user;
    }

    @Hidden
    @GetMapping("names")
    public User getNames() {
        int a = 1 / 0;
        return User.builder().id(1L).name("Rick").build();
    }

    @Schema(name = "user", description = "用户信息")
    @Data
    class UserSchema {

        String id;

        @Schema(description = "用户名", example = "Rick", required = true)
        String name;

        List<String> nameList;

        IdCardSchema idCard;

        List<PetSchema> petList;

        @ArraySchema(
                schema = @Schema(description = "用户角色 id", example = "321283197719123452")
        )
        List<String> roleIds;
    }


    @Schema(name = "id_card", description = "身份证")
    @Data
    class IdCardSchema {

        String id;

        @Schema(description = "身份证号", example = "321283197719123452", required = true)
        String code;
    }

    @Schema(name = "pet", description = "宠物")
    @Data
    class PetSchema {

        String id;

        @Schema(description = "名字", example = "Tom", required = true)
        String name;
    }


}
