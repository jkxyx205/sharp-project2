package com.rick.test.module.db.user.controller;

import com.rick.test.module.db.user.entity.User;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

/**
 * @author Rick.Xu
 * @date 2025/11/11 15:41
 */
@RestController
@RequestMapping("users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    @PostMapping
    public User saveUser(@Valid @RequestBody User user) {
        return user;
    }

    @GetMapping
    public User getUser(User user) {
        return user;
    }

}
