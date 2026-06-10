package com.ohchurus.budget.mapper;

import com.ohchurus.budget.dto.output.ResultCategoryDTO;
import com.ohchurus.budget.dto.output.ResultCategoryTreeDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.mapper.impl.CategoryMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryMapperImplTest {

    @Mock
    private ModelMapper modelMapper;

    private CategoryMapperImpl categoryMapper;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        categoryMapper = new CategoryMapperImpl(modelMapper);
        testCategory = Category.builder()
                .id(1L)
                .userId(1L)
                .name("Food")
                .description("Food expenses")
                .icon("utensils")
                .color("#FF5733")
                .type(CategoryType.EXPENSE)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("toResultDTO")
    class ToResultDTOTests {

        @Test
        @DisplayName("Should map category to ResultCategoryDTO")
        void shouldMapCategoryToResultDTO() {
            ResultCategoryDTO expectedDTO = new ResultCategoryDTO();
            expectedDTO.setId(1L);
            expectedDTO.setName("Food");
            expectedDTO.setType(CategoryType.EXPENSE);

            when(modelMapper.map(testCategory, ResultCategoryDTO.class)).thenReturn(expectedDTO);

            ResultCategoryDTO result = categoryMapper.toResultDTO(testCategory);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("Food", result.getName());
            assertEquals(CategoryType.EXPENSE, result.getType());
            verify(modelMapper).map(testCategory, ResultCategoryDTO.class);
        }

        @Test
        @DisplayName("Should return null when category is null")
        void shouldReturnNullWhenCategoryIsNull() {
            ResultCategoryDTO result = categoryMapper.toResultDTO(null);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("toTreeDTO")
    class ToTreeDTOTests {

        @Test
        @DisplayName("Should map category to ResultCategoryTreeDTO")
        void shouldMapCategoryToTreeDTO() {
            ResultCategoryTreeDTO result = categoryMapper.toTreeDTO(testCategory);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("Food", result.getName());
            assertEquals("Food expenses", result.getDescription());
            assertEquals("utensils", result.getIcon());
            assertEquals("#FF5733", result.getColor());
            assertEquals(CategoryType.EXPENSE, result.getType());
            assertNotNull(result.getChildren());
            assertTrue(result.getChildren().isEmpty());
        }

        @Test
        @DisplayName("Should return null when category is null")
        void shouldReturnNullWhenCategoryIsNull() {
            ResultCategoryTreeDTO result = categoryMapper.toTreeDTO(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Should flag shared=true when the category belongs to a household")
        void shouldFlagSharedForHouseholdCategory() {
            Category shared = Category.builder()
                    .id(2L).userId(1L).name("Mercado").type(CategoryType.EXPENSE)
                    .householdId(500L).active(true).build();

            ResultCategoryTreeDTO result = categoryMapper.toTreeDTO(shared);

            assertNotNull(result);
            assertEquals(500L, result.getHouseholdId());
            assertTrue(result.getShared());
        }
    }
}
