package com.rick.test.module.db.complex.controller;

import com.rick.db.repository.EntityDAO;
import com.rick.test.module.db.complex.entity.ComplexModel;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("complex")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "ComplexModel", description = "ComplexModel 相关接口")
public class ComplexController {

    @Autowired
    private EntityDAO<ComplexModel, Long> complexModelDAO;

    @GetMapping("{id}")
    public ComplexModel getComplexModel(@PathVariable Long id) {
        Optional<ComplexModel> optional = complexModelDAO.selectById(id);
        ComplexModel complexModel = optional.get();
        return complexModel;
    }

    @PostMapping
    public ComplexModel postComplexModel(@RequestBody ComplexModel complexMode) {
        return complexMode;
    }

}
