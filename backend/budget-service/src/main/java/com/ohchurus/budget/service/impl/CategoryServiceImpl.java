package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.input.CategoryFilterDTO;
import com.ohchurus.budget.dto.input.CategorySaveDTO;
import com.ohchurus.budget.dto.output.ResultCategoryDTO;
import com.ohchurus.budget.dto.output.ResultCategoryTreeDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.mapper.CategoryMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private static final int MAX_DEPTH = 3;

    private final CategoryRepository categoryRepository;
    private final com.ohchurus.budget.repository.MovementRepository movementRepository;
    private final CategoryMapper categoryMapper;
    private final HouseholdServiceImpl householdService;
    private final com.ohchurus.budget.util.ControlAcceso acceso;

    public CategoryServiceImpl(CategoryRepository categoryRepository,
                                com.ohchurus.budget.repository.MovementRepository movementRepository,
                                CategoryMapper categoryMapper,
                                HouseholdServiceImpl householdService,
                                com.ohchurus.budget.util.ControlAcceso acceso) {
        this.categoryRepository = categoryRepository;
        this.movementRepository = movementRepository;
        this.categoryMapper = categoryMapper;
        this.householdService = householdService;
        this.acceso = acceso;
    }

    @Override
    public ResultDTO saveAndUpdate(CategorySaveDTO dto) {
        try {
            boolean isUpdate = dto.getId() != null;
            return isUpdate ? updateCategory(dto) : createCategory(dto);
        } catch (Exception e) {
            log.error("Error saving category: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving category", 500);
        }
    }

    private ResultDTO createCategory(CategorySaveDTO dto) {
        if (dto.getParentId() != null) {
            int parentDepth = getDepth(dto.getParentId());
            if (parentDepth < 0) {
                return new ResultDTO(false, "Parent category not found", 201);
            }
            if (parentDepth + 1 >= MAX_DEPTH) {
                return new ResultDTO(false, "Maximum category depth exceeded (max " + MAX_DEPTH + " levels)", 202);
            }
        }

        /* El dueno de lo que se crea es QUIEN LO CREA, no lo que diga el
           cuerpo. Se demostro con trafico real: Ana enviaba
           {"userId": <id de Bruno>} con su propio token y la categoria
           aparecia dentro de la cuenta de Bruno. Las lecturas y los borrados
           ya estaban cerrados; la creacion se habia quedado fuera. */
        Long dueno = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();

        if (categoryRepository.existsByUserIdAndNameAndParentIdAndActiveTrue(
                dueno, dto.getName(), dto.getParentId())) {
            return new ResultDTO(false, "Category name already exists at this level", 203);
        }

        Category category = Category.builder()
                .userId(dueno)
                .name(dto.getName())
                .description(dto.getDescription())
                .parentId(dto.getParentId())
                .icon(dto.getIcon())
                .color(dto.getColor())
                .type(dto.getType())
                .householdId(dto.getHouseholdId())
                .reimbursable(Boolean.TRUE.equals(dto.getReimbursable()))
                .active(true)
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Category created: {} for user {}", saved.getName(), saved.getUserId());
        return new ResultDTO(categoryMapper.toResultDTO(saved));
    }

    private ResultDTO updateCategory(CategorySaveDTO dto) {
        Optional<Category> existing = categoryRepository.findByIdAndActiveTrue(dto.getId());
        if (existing.isEmpty() || !acceso.puedeVerCategoria(existing.get())) {
            return new ResultDTO(false, "Category not found", 204);
        }

        if (dto.getParentId() != null) {
            if (dto.getParentId().equals(dto.getId())) {
                return new ResultDTO(false, "Category cannot be its own parent", 205);
            }
            int parentDepth = getDepth(dto.getParentId());
            if (parentDepth < 0) {
                return new ResultDTO(false, "Parent category not found", 201);
            }
            if (parentDepth + 1 >= MAX_DEPTH) {
                return new ResultDTO(false, "Maximum category depth exceeded (max " + MAX_DEPTH + " levels)", 202);
            }
        }

        if (categoryRepository.existsByUserIdAndNameAndParentIdAndActiveTrueAndIdNot(
                dto.getUserId(), dto.getName(), dto.getParentId(), dto.getId())) {
            return new ResultDTO(false, "Category name already exists at this level", 203);
        }

        Category category = existing.get();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setParentId(dto.getParentId());
        category.setIcon(dto.getIcon());
        category.setColor(dto.getColor());
        category.setType(dto.getType());
        category.setHouseholdId(dto.getHouseholdId());
        /* El interruptor se edita como cualquier otro campo: marcar una
                   categoria como reembolsable a mitad de mes tiene que poder
                   deshacerse igual de facil. */
        category.setReimbursable(Boolean.TRUE.equals(dto.getReimbursable()));

        Category saved = categoryRepository.save(category);
        log.info("Category updated: {}", saved.getName());
        return new ResultDTO(categoryMapper.toResultDTO(saved));
    }

    private int getDepth(Long categoryId) {
        int depth = 0;
        Long currentId = categoryId;
        Set<Long> visited = new HashSet<>();

        while (currentId != null) {
            if (visited.contains(currentId)) return -1;
            visited.add(currentId);

            Optional<Category> cat = categoryRepository.findByIdAndActiveTrue(currentId);
            if (cat.isEmpty()) return -1;

            currentId = cat.get().getParentId();
            if (currentId != null) depth++;
        }
        return depth;
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getById(Long id) {
        Optional<Category> category = categoryRepository.findByIdAndActiveTrue(id);
        if (category.isEmpty() || !acceso.puedeVerCategoria(category.get())) {
            return new ResultDTO(false, "Category not found", 204);
        }
        return new ResultDTO(categoryMapper.toResultDTO(category.get()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getAll(CategoryFilterDTO filter) {
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by("name").ascending());

        // Include household categories
        List<Long> householdIds = filter.getUserId() != null
                ? householdService.getHouseholdIds(filter.getUserId())
                : Collections.emptyList();

        Page<Category> page;
        if (!householdIds.isEmpty()) {
            page = categoryRepository.findAllWithFiltersAndHousehold(
                    filter.getUserId(), householdIds, filter.getName(), filter.getType(), pageable);
        } else {
            page = categoryRepository.findAllWithFilters(
                    filter.getUserId(), filter.getName(), filter.getType(), pageable);
        }

        List<ResultCategoryDTO> list = page.getContent().stream()
                .map(c -> {
                    ResultCategoryDTO dto = categoryMapper.toResultDTO(c);
                    if (dto != null && c.getHouseholdId() != null) {
                        dto.setShared(true);
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        response.put("totalPage", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("list", list);

        return new ResultDTO(response);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getTree(Long userId) {
        List<Long> hIds = householdService.getHouseholdIds(userId);
        List<Category> allCategories = !hIds.isEmpty()
                ? categoryRepository.findPersonalAndHousehold(userId, hIds)
                : categoryRepository.findByUserIdAndActiveTrue(userId);

        Map<Long, ResultCategoryTreeDTO> dtoMap = new LinkedHashMap<>();
        for (Category cat : allCategories) {
            dtoMap.put(cat.getId(), categoryMapper.toTreeDTO(cat));
        }

        List<ResultCategoryTreeDTO> roots = new ArrayList<>();
        for (Category cat : allCategories) {
            ResultCategoryTreeDTO dto = dtoMap.get(cat.getId());
            if (cat.getParentId() == null) {
                roots.add(dto);
            } else {
                ResultCategoryTreeDTO parent = dtoMap.get(cat.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dto);
                } else {
                    /* Padre no visible -> el hijo sube a raiz en vez de caerse
                       del arbol. Pasaba al salir de un nucleo familiar: las
                       subcategorias personales colgaban de una categoria del
                       hogar y, al dejar de verse el padre, desaparecian de la
                       pantalla sin borrarse ni avisar. */
                    roots.add(dto);
                }
            }
        }

        return new ResultDTO(roots);
    }

    @Override
    public ResultDTO delete(Long id) {
        Optional<Category> category = categoryRepository.findByIdAndActiveTrue(id);
        if (category.isEmpty() || !acceso.puedeVerCategoria(category.get())) {
            return new ResultDTO(false, "Category not found", 404);
        }

        if (categoryRepository.existsByParentIdAndActiveTrue(id)) {
            return new ResultDTO(false, "Cannot delete category with active children", 400);
        }

        // Prevent deleting categories with active movements
        boolean hasMovements = movementRepository.existsByCategoryIdAndActiveTrue(id);
        if (hasMovements) {
            return new ResultDTO(false, "No se puede eliminar una categoria con movimientos activos", 400);
        }

        Category entity = category.get();
        entity.setActive(false);
        categoryRepository.save(entity);
        log.info("Category deleted (soft): {}", entity.getName());
        return new ResultDTO(true, "Category deleted successfully", 0);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO typeList() {
        List<Map<String, String>> types = new ArrayList<>();
        for (CategoryType type : CategoryType.values()) {
            Map<String, String> map = new HashMap<>();
            map.put("key", type.name());
            map.put("name", type.name());
            types.add(map);
        }
        return new ResultDTO(types);
    }
}
