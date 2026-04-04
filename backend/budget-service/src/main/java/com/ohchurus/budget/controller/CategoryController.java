package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.CategoryFilterDTO;
import com.ohchurus.budget.dto.input.CategorySaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping(value = "/save", produces = "application/json")
    public ResponseEntity<ResultDTO> save(@Valid @RequestBody CategorySaveDTO dto) {
        return ResponseEntity.ok(categoryService.saveAndUpdate(dto));
    }

    @PostMapping(value = "/get/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getById(id));
    }

    @PostMapping(value = "/all", produces = "application/json")
    public ResponseEntity<ResultDTO> getAll(@Valid @RequestBody CategoryFilterDTO filter) {
        return ResponseEntity.ok(categoryService.getAll(filter));
    }

    @PostMapping(value = "/tree", produces = "application/json")
    public ResponseEntity<ResultDTO> getTree(@RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        return ResponseEntity.ok(categoryService.getTree(userId));
    }

    @PostMapping(value = "/delete/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> delete(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.delete(id));
    }

    @PostMapping(value = "/type-list", produces = "application/json")
    public ResponseEntity<ResultDTO> typeList() {
        return ResponseEntity.ok(categoryService.typeList());
    }
}
