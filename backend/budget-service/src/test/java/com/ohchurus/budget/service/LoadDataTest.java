package com.ohchurus.budget.service;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.service.impl.LoadData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadDataTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private LoadData loadData;

    @Test
    @DisplayName("Should seed categories when database is empty")
    void shouldSeedCategoriesWhenEmpty() {
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", true);
        when(categoryRepository.count()).thenReturn(0L);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category cat = invocation.getArgument(0);
            cat.setId((long) (Math.random() * 1000));
            return cat;
        });

        loadData.run();

        verify(categoryRepository, atLeast(25)).save(any(Category.class));
    }

    @Test
    @DisplayName("Should skip seed when categories exist")
    void shouldSkipWhenCategoriesExist() {
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", true);
        when(categoryRepository.count()).thenReturn(25L);

        loadData.run();

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Should skip seed when disabled")
    void shouldSkipWhenDisabled() {
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", false);

        loadData.run();

        verify(categoryRepository, never()).count();
    }
}
