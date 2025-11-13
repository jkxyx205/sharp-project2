package com.rick.test.module.db.user.controller;

import com.rick.test.module.db.user.entity.Pet;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

/**
 * @author Rick.Xu
 * @date 2025/11/13 10:57
 */
@RestController
@RequestMapping("pets")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PetController {

    @PostMapping
    public Pet saveOrUpdate(@RequestBody Pet pet) {
        return pet;
    }

    @GetMapping
    public Pet get(Pet pet) {
        return pet;
    }
}
