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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private HouseholdServiceImpl householdService;
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
        org.mockito.Mockito.lenient().when(acceso.esDeMiHogar(
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }
    /* La creacion toma el dueno del TOKEN, no del cuerpo: era la ultima
       puerta por la que se podia plantar una categoria o un movimiento dentro
       de la cuenta de otro. Aqui se planta la identidad para poder seguir
       probando la logica. */
    @org.junit.jupiter.api.BeforeEach
    void plantarIdentidadDelDueno() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "dueno@ohchurus.com", null, java.util.Collections.emptyList());
        auth.setDetails(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }

    @org.junit.jupiter.api.AfterEach
    void limpiarIdentidadDelDueno() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }



    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category testCategory;
    private ResultCategoryDTO testResultDTO;
    private CategorySaveDTO testSaveDTO;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L).userId(1L).name("Salario").type(CategoryType.INCOME)
                .icon("wallet").color("#4CAF50").active(true).build();

        testResultDTO = new ResultCategoryDTO();
        testResultDTO.setId(1L);
        testResultDTO.setName("Salario");
        testResultDTO.setType(CategoryType.INCOME);

        testSaveDTO = new CategorySaveDTO();
        testSaveDTO.setUserId(1L);
        testSaveDTO.setName("Salario");
        testSaveDTO.setType(CategoryType.INCOME);
        testSaveDTO.setIcon("wallet");
        testSaveDTO.setColor("#4CAF50");
    }

    @Nested
    @DisplayName("Create")
    class CreateTests {

        @Test
        @DisplayName("Should create root category successfully")
        void shouldCreateRootCategory() {
            when(categoryRepository.existsByUserIdAndNameAndParentIdAndActiveTrue(1L, "Salario", null)).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);
            when(categoryMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("Should create child category successfully")
        void shouldCreateChildCategory() {
            testSaveDTO.setParentId(1L);
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByUserIdAndNameAndParentIdAndActiveTrue(1L, "Salario", 1L)).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);
            when(categoryMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should create category with householdId")
        void shouldCreateWithHouseholdId() {
            testSaveDTO.setHouseholdId(100L);
            when(categoryRepository.existsByUserIdAndNameAndParentIdAndActiveTrue(1L, "Salario", null)).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);
            when(categoryMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(categoryRepository).save(argThat(c -> c.getHouseholdId() != null));
        }

        @Test
        @DisplayName("Should fail on duplicate name at same level")
        void shouldFailOnDuplicateName() {
            when(categoryRepository.existsByUserIdAndNameAndParentIdAndActiveTrue(1L, "Salario", null)).thenReturn(true);

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(203, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail when max depth exceeded")
        void shouldFailWhenMaxDepthExceeded() {
            Category level1 = Category.builder().id(1L).userId(1L).name("L1").parentId(null).active(true).build();
            Category level2 = Category.builder().id(2L).userId(1L).name("L2").parentId(1L).active(true).build();
            Category level3 = Category.builder().id(3L).userId(1L).name("L3").parentId(2L).active(true).build();

            testSaveDTO.setParentId(3L);

            when(categoryRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(level3));
            when(categoryRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(level2));
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(level1));

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(202, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail when parent not found")
        void shouldFailWhenParentNotFound() {
            testSaveDTO.setParentId(99L);
            when(categoryRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(201, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Update")
    class UpdateTests {

        @BeforeEach
        void setUp() { testSaveDTO.setId(1L); }

        @Test
        @DisplayName("Should update category successfully")
        void shouldUpdateCategory() {
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByUserIdAndNameAndParentIdAndActiveTrueAndIdNot(1L, "Salario", null, 1L)).thenReturn(false);
            when(categoryRepository.save(any())).thenReturn(testCategory);
            when(categoryMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should fail when setting self as parent")
        void shouldFailWhenSelfParent() {
            testSaveDTO.setParentId(1L);
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(205, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail update when category not found")
        void shouldFailUpdateWhenNotFound() {
            testSaveDTO.setId(999L);
            when(categoryRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);
            assertFalse(result.isCorrect());
            assertEquals(204, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail update when parent not found")
        void shouldFailUpdateWhenParentNotFound() {
            testSaveDTO.setId(1L);
            testSaveDTO.setParentId(888L);
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.findByIdAndActiveTrue(888L)).thenReturn(Optional.empty());

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);
            assertFalse(result.isCorrect());
            assertEquals(201, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail update when max depth exceeded")
        void shouldFailUpdateWhenMaxDepthExceeded() {
            Category level1 = Category.builder().id(100L).parentId(null).active(true).build();
            Category level2 = Category.builder().id(200L).parentId(100L).active(true).build();
            Category level3 = Category.builder().id(300L).parentId(200L).active(true).build();

            testSaveDTO.setId(1L);
            testSaveDTO.setParentId(300L);
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.findByIdAndActiveTrue(300L)).thenReturn(Optional.of(level3));
            when(categoryRepository.findByIdAndActiveTrue(200L)).thenReturn(Optional.of(level2));
            when(categoryRepository.findByIdAndActiveTrue(100L)).thenReturn(Optional.of(level1));

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);
            assertFalse(result.isCorrect());
            assertEquals(202, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail update when name duplicate at same level")
        void shouldFailUpdateOnDuplicateName() {
            testSaveDTO.setId(1L);
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByUserIdAndNameAndParentIdAndActiveTrueAndIdNot(
                    eq(1L), eq("Salario"), isNull(), eq(1L))).thenReturn(true);

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);
            assertFalse(result.isCorrect());
            assertEquals(203, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("GetById")
    class GetByIdTests {

        @Test
        @DisplayName("Should return category when found")
        void shouldReturnWhenFound() {
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));
            when(categoryMapper.toResultDTO(testCategory)).thenReturn(testResultDTO);

            ResultDTO result = categoryService.getById(1L);

            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should return error when not found")
        void shouldReturnErrorWhenNotFound() {
            when(categoryRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = categoryService.getById(99L);

            assertFalse(result.isCorrect());
            assertEquals(204, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("GetAll")
    class GetAllTests {

        @Test
        @DisplayName("Should return paginated categories")
        void shouldReturnPaginated() {
            CategoryFilterDTO filter = new CategoryFilterDTO();
            filter.setUserId(1L);

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            Page<Category> page = new PageImpl<>(List.of(testCategory));
            when(categoryRepository.findAllWithFilters(any(), any(), any(), any(Pageable.class))).thenReturn(page);
            when(categoryMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = categoryService.getAll(filter);

            assertTrue(result.isCorrect());
        }
    }

    @Nested
    @DisplayName("GetTree")
    class GetTreeTests {

        @Test
        @DisplayName("Should return tree structure")
        void shouldReturnTree() {
            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());

            Category parent = Category.builder().id(1L).userId(1L).name("Parent").parentId(null).type(CategoryType.EXPENSE).active(true).build();
            Category child = Category.builder().id(2L).userId(1L).name("Child").parentId(1L).type(CategoryType.EXPENSE).active(true).build();

            ResultCategoryTreeDTO parentDTO = new ResultCategoryTreeDTO();
            parentDTO.setId(1L);
            parentDTO.setName("Parent");

            ResultCategoryTreeDTO childDTO = new ResultCategoryTreeDTO();
            childDTO.setId(2L);
            childDTO.setName("Child");

            when(categoryRepository.findByUserIdAndActiveTrue(1L)).thenReturn(List.of(parent, child));
            when(categoryMapper.toTreeDTO(parent)).thenReturn(parentDTO);
            when(categoryMapper.toTreeDTO(child)).thenReturn(childDTO);

            ResultDTO result = categoryService.getTree(1L);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<ResultCategoryTreeDTO> roots = (List<ResultCategoryTreeDTO>) result.getObject();
            assertEquals(1, roots.size());
            assertEquals("Parent", roots.get(0).getName());
            assertEquals(1, roots.get(0).getChildren().size());
            assertEquals("Child", roots.get(0).getChildren().get(0).getName());
        }

        @Test
        @DisplayName("Should return empty when no categories")
        void shouldReturnEmptyTree() {
            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(categoryRepository.findByUserIdAndActiveTrue(1L)).thenReturn(List.of());

            ResultDTO result = categoryService.getTree(1L);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<?> roots = (List<?>) result.getObject();
            assertTrue(roots.isEmpty());
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("Should soft delete category")
        void shouldSoftDelete() {
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByParentIdAndActiveTrue(1L)).thenReturn(false);
            when(movementRepository.existsByCategoryIdAndActiveTrue(1L)).thenReturn(false);
            when(categoryRepository.save(any())).thenReturn(testCategory);

            ResultDTO result = categoryService.delete(1L);

            assertTrue(result.isCorrect());
            verify(categoryRepository).save(argThat(cat -> !cat.getActive()));
        }

        @Test
        @DisplayName("Should fail when category has children")
        void shouldFailWhenHasChildren() {
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByParentIdAndActiveTrue(1L)).thenReturn(true);

            ResultDTO result = categoryService.delete(1L);

            assertFalse(result.isCorrect());
            assertEquals(400, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail when category has active movements")
        void shouldFailWhenHasMovements() {
            when(categoryRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.existsByParentIdAndActiveTrue(1L)).thenReturn(false);
            when(movementRepository.existsByCategoryIdAndActiveTrue(1L)).thenReturn(true);

            ResultDTO result = categoryService.delete(1L);

            assertFalse(result.isCorrect());
            assertEquals(400, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail when category not found for delete")
        void shouldFailWhenNotFound() {
            when(categoryRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = categoryService.delete(99L);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("TypeList")
    class TypeListTests {

        @Test
        @DisplayName("Should return all category types")
        void shouldReturnTypes() {
            ResultDTO result = categoryService.typeList();

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<Map<String, String>> types = (List<Map<String, String>>) result.getObject();
            assertEquals(2, types.size());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle exception in save gracefully")
        void shouldHandleExceptionInSave() {
            when(categoryRepository.existsByUserIdAndNameAndParentIdAndActiveTrue(any(), any(), any()))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = categoryService.saveAndUpdate(testSaveDTO);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }
}
