package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.CategoryFilterDTO;
import com.ohchurus.budget.dto.input.CategorySaveDTO;
import com.ohchurus.budget.dto.output.ResultCategoryDTO;
import com.ohchurus.budget.dto.output.ResultCategoryTreeDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.mapper.CategoryMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.impl.CategoryServiceImpl;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryServiceImpl - edge cases / branches")
class CategoryServiceImplEdgeCasesTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private CategoryMapper categoryMapper;
    @Mock private HouseholdServiceImpl householdService;
    /* El control de acceso es una preocupacion aparte: aqui se le dice que si
       para poder probar la LOGICA. Que diga que no cuando toca lo comprueba
       AislamientoEntreUsuariosTest, que levanta la app entera con dos usuarios
       de verdad; un mock nunca podria demostrarlo. */
    @Mock
    private com.ohchurus.budget.util.ControlAcceso acceso;

    @BeforeEach
    void permitirAccesoEnLasPruebasDeLogica() {
        org.mockito.Mockito.lenient().when(acceso.puedeVer(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(acceso.puedeVerCategoria(
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(acceso.esMio(
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }


    @InjectMocks
    private CategoryServiceImpl service;

    private static final Long USER_ID = 1L;

    private Category cat(Long id, Long parentId) {
        return Category.builder().id(id).userId(USER_ID).name("Cat" + id).parentId(parentId)
                .type(CategoryType.EXPENSE).active(true).build();
    }

    private CategorySaveDTO saveDto(Long id, Long parentId) {
        CategorySaveDTO dto = new CategorySaveDTO();
        dto.setId(id);
        dto.setUserId(USER_ID);
        dto.setName("Nueva");
        dto.setParentId(parentId);
        dto.setType(CategoryType.EXPENSE);
        return dto;
    }

    @Nested
    @DisplayName("depth & cycle detection")
    class DepthTests {

        @Test
        @DisplayName("update should reject when parent depth exceeds the maximum")
        void updateDepthExceeded() {
            // parent chain: 2 -> 1 -> 0 (depth 2), +1 = 3 >= MAX_DEPTH(3)
            when(categoryRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(cat(5L, null)));
            when(categoryRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(cat(2L, 1L)));
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(cat(1L, 0L)));
            when(categoryRepository.findByIdAndActiveTrue(0L)).thenReturn(Optional.of(cat(0L, null)));

            ResultDTO r = service.saveAndUpdate(saveDto(5L, 2L));
            assertFalse(r.isCorrect());
            assertEquals(202, r.getErrorCode());
        }

        @Test
        @DisplayName("create should reject a cyclic parent chain")
        void createCyclicParent() {
            // 2 -> 3 -> 2 (cycle) -> getDepth returns -1 -> "Parent not found"
            when(categoryRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(cat(2L, 3L)));
            when(categoryRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(cat(3L, 2L)));

            ResultDTO r = service.saveAndUpdate(saveDto(null, 2L));
            assertFalse(r.isCorrect());
            assertEquals(201, r.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getAll branches")
    class GetAllTests {

        @Test
        @DisplayName("Should use empty households when userId is null and handle null mapped DTO")
        @SuppressWarnings("unchecked")
        void nullUserAndNullDto() {
            CategoryFilterDTO filter = new CategoryFilterDTO();
            filter.setUserId(null);
            Category c = cat(1L, null);
            Page<Category> page = new PageImpl<>(List.of(c), Pageable.ofSize(10), 1);
            when(categoryRepository.findAllWithFilters(isNull(), any(), any(), any())).thenReturn(page);
            when(categoryMapper.toResultDTO(c)).thenReturn(null); // dto == null branch

            ResultDTO r = service.getAll(filter);
            assertTrue(r.isCorrect());
            verify(householdService, never()).getHouseholdIds(any());
        }

        @Test
        @DisplayName("Should use household query and flag shared categories")
        @SuppressWarnings("unchecked")
        void householdSharedFlag() {
            CategoryFilterDTO filter = new CategoryFilterDTO();
            filter.setUserId(USER_ID);
            Category shared = Category.builder().id(1L).userId(USER_ID).name("Compartida")
                    .type(CategoryType.EXPENSE).householdId(500L).active(true).build();
            Page<Category> page = new PageImpl<>(List.of(shared), Pageable.ofSize(10), 1);
            ResultCategoryDTO dto = new ResultCategoryDTO();

            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(500L));
            when(categoryRepository.findAllWithFiltersAndHousehold(eq(USER_ID), anyList(), any(), any(), any()))
                    .thenReturn(page);
            when(categoryMapper.toResultDTO(shared)).thenReturn(dto);

            ResultDTO r = service.getAll(filter);
            assertTrue(r.isCorrect());
            Map<String, Object> resp = (Map<String, Object>) r.getObject();
            ResultCategoryDTO out = ((List<ResultCategoryDTO>) resp.get("list")).get(0);
            assertTrue(out.getShared());
        }
    }

    @Nested
    @DisplayName("getTree branches")
    class GetTreeTests {

        @Test
        @DisplayName("Should build tree from household categories and attach children to parents")
        @SuppressWarnings("unchecked")
        void householdTreeWithChildren() {
            Category root = cat(1L, null);
            Category child = cat(2L, 1L);

            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(500L));
            when(categoryRepository.findPersonalAndHousehold(eq(USER_ID), anyList()))
                    .thenReturn(List.of(root, child));
            when(categoryMapper.toTreeDTO(root)).thenReturn(new ResultCategoryTreeDTO());
            when(categoryMapper.toTreeDTO(child)).thenReturn(new ResultCategoryTreeDTO());

            ResultDTO r = service.getTree(USER_ID);
            assertTrue(r.isCorrect());
            List<ResultCategoryTreeDTO> roots = (List<ResultCategoryTreeDTO>) r.getObject();
            assertEquals(1, roots.size());
            assertEquals(1, roots.get(0).getChildren().size());
        }

        @Test
        @DisplayName("Should drop orphan children whose parent is not in the result set")
        @SuppressWarnings("unchecked")
        void orphanChildDropped() {
            Category orphan = cat(2L, 999L); // parent 999 not present

            when(householdService.getHouseholdIds(USER_ID)).thenReturn(java.util.Collections.emptyList());
            when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(orphan));
            when(categoryMapper.toTreeDTO(orphan)).thenReturn(new ResultCategoryTreeDTO());

            ResultDTO r = service.getTree(USER_ID);
            assertTrue(r.isCorrect());
            List<ResultCategoryTreeDTO> roots = (List<ResultCategoryTreeDTO>) r.getObject();
            assertTrue(roots.isEmpty()); // orphan is neither a root nor attached
        }
    }
}
