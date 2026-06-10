package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.MovementFilterDTO;
import com.ohchurus.budget.dto.input.MovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import com.ohchurus.budget.service.impl.MovementServiceImpl;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovementServiceImplTest {

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MovementMapper movementMapper;

    @Mock
    private HouseholdServiceImpl householdService;

    @InjectMocks
    private MovementServiceImpl movementService;

    private Movement testMovement;
    private ResultMovementDTO testResultDTO;
    private MovementSaveDTO testSaveDTO;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(10L).userId(1L).name("Alimentacion").type(CategoryType.EXPENSE)
                .active(true).build();

        testMovement = Movement.builder()
                .id(1L).userId(1L).categoryId(10L)
                .date(LocalDate.of(2026, 3, 15))
                .amount(new BigDecimal("150000.00"))
                .description("Grocery shopping")
                .confirmed(true).active(true)
                .build();

        testResultDTO = new ResultMovementDTO();
        testResultDTO.setId(1L);
        testResultDTO.setUserId(1L);
        testResultDTO.setCategoryId(10L);
        testResultDTO.setDate(LocalDate.of(2026, 3, 15));
        testResultDTO.setAmount(new BigDecimal("150000.00"));
        testResultDTO.setDescription("Grocery shopping");
        testResultDTO.setConfirmed(true);
        testResultDTO.setActive(true);

        testSaveDTO = new MovementSaveDTO();
        testSaveDTO.setUserId(1L);
        testSaveDTO.setCategoryId(10L);
        testSaveDTO.setDate(LocalDate.of(2026, 3, 15));
        testSaveDTO.setAmount(new BigDecimal("150000.00"));
        testSaveDTO.setDescription("Grocery shopping");
    }

    @Nested
    @DisplayName("Create")
    class CreateTests {

        @Test
        @DisplayName("Should create movement successfully")
        void shouldCreateMovement() {
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(testCategory));
            when(movementRepository.save(any(Movement.class))).thenReturn(testMovement);
            when(movementMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = movementService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(movementRepository).save(any(Movement.class));
        }

        @Test
        @DisplayName("Should create movement with confirmed false")
        void shouldCreateMovementUnconfirmed() {
            testSaveDTO.setConfirmed(false);
            Movement unconfirmed = Movement.builder()
                    .id(2L).userId(1L).categoryId(10L)
                    .date(LocalDate.of(2026, 3, 15))
                    .amount(new BigDecimal("150000.00"))
                    .confirmed(false).active(true).build();

            ResultMovementDTO unconfirmedDTO = new ResultMovementDTO();
            unconfirmedDTO.setId(2L);
            unconfirmedDTO.setCategoryId(10L);
            unconfirmedDTO.setConfirmed(false);

            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(testCategory));
            when(movementRepository.save(any(Movement.class))).thenReturn(unconfirmed);
            when(movementMapper.toResultDTO(any())).thenReturn(unconfirmedDTO);

            ResultDTO result = movementService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(movementRepository).save(argThat(m -> !m.getConfirmed()));
        }

        @Test
        @DisplayName("Should fail when category not found")
        void shouldFailWhenCategoryNotFound() {
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());

            ResultDTO result = movementService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
            verify(movementRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create child movement with parentMovementId")
        void shouldCreateChildMovement() {
            testSaveDTO.setParentMovementId(50L);
            Movement parent = Movement.builder()
                    .id(50L).userId(1L).categoryId(10L).date(LocalDate.now())
                    .amount(new BigDecimal("500000")).confirmed(true).active(true).build();

            when(movementRepository.findByIdAndActiveTrue(50L)).thenReturn(Optional.of(parent));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(testCategory));
            when(movementRepository.save(any(Movement.class))).thenReturn(testMovement);
            when(movementMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = movementService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should fail when parent movement not found")
        void shouldFailWhenParentNotFound() {
            testSaveDTO.setParentMovementId(999L);
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(testCategory));
            when(movementRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

            ResultDTO result = movementService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Update")
    class UpdateTests {

        @BeforeEach
        void setUp() {
            testSaveDTO.setId(1L);
        }

        @Test
        @DisplayName("Should update movement successfully")
        void shouldUpdateMovement() {
            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testMovement));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(testCategory));
            when(movementRepository.save(any())).thenReturn(testMovement);
            when(movementMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = movementService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should fail when movement not found for update")
        void shouldFailWhenNotFound() {
            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.empty());

            ResultDTO result = movementService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail when category not found for update")
        void shouldFailWhenCategoryNotFoundForUpdate() {
            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testMovement));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());

            ResultDTO result = movementService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("GetById")
    class GetByIdTests {

        @Test
        @DisplayName("Should return movement when found")
        void shouldReturnWhenFound() {
            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testMovement));
            when(movementMapper.toResultDTO(testMovement)).thenReturn(testResultDTO);

            ResultDTO result = movementService.getById(1L);

            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should return error when not found")
        void shouldReturnErrorWhenNotFound() {
            when(movementRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = movementService.getById(99L);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("GetAll")
    class GetAllTests {

        @Test
        @DisplayName("Should return paginated movements")
        void shouldReturnPaginated() {
            MovementFilterDTO filter = new MovementFilterDTO();
            filter.setUserId(1L);

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            Page<Movement> page = new PageImpl<>(List.of(testMovement));
            when(movementRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);
            when(movementMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = movementService.getAll(filter);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) result.getObject();
            assertEquals(1, ((List<?>) response.get("list")).size());
        }

        @Test
        @DisplayName("Should return empty list when no movements")
        void shouldReturnEmptyList() {
            MovementFilterDTO filter = new MovementFilterDTO();
            filter.setUserId(1L);

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            Page<Movement> page = new PageImpl<>(List.of());
            when(movementRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

            ResultDTO result = movementService.getAll(filter);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) result.getObject();
            assertTrue(((List<?>) response.get("list")).isEmpty());
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("Should soft delete movement")
        void shouldSoftDelete() {
            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testMovement));
            when(movementRepository.save(any())).thenReturn(testMovement);

            ResultDTO result = movementService.delete(1L);

            assertTrue(result.isCorrect());
            verify(movementRepository).save(argThat(m -> !m.getActive()));
        }

        @Test
        @DisplayName("Should fail when movement not found for delete")
        void shouldFailWhenNotFound() {
            when(movementRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = movementService.delete(99L);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }

        @Test
        @DisplayName("Should delete transfer pair when deleting a transfer movement")
        void shouldDeleteTransferPair() {
            Movement transfer = Movement.builder()
                    .id(1L).userId(1L).categoryId(10L).date(LocalDate.now())
                    .amount(new BigDecimal("100000")).isTransfer(true).transferPairId(2L)
                    .confirmed(true).active(true).build();
            Movement pair = Movement.builder()
                    .id(2L).userId(1L).categoryId(20L).date(LocalDate.now())
                    .amount(new BigDecimal("100000")).isTransfer(true).transferPairId(1L)
                    .confirmed(true).active(true).build();

            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(transfer));
            when(movementRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(pair));
            when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ResultDTO result = movementService.delete(1L);

            assertTrue(result.isCorrect());
            verify(movementRepository, times(2)).save(any(Movement.class));
        }
    }

    @Nested
    @DisplayName("Confirm")
    class ConfirmTests {

        @Test
        @DisplayName("Should confirm movement successfully")
        void shouldConfirmMovement() {
            Movement unconfirmed = Movement.builder()
                    .id(1L).userId(1L).categoryId(10L)
                    .date(LocalDate.of(2026, 3, 15))
                    .amount(new BigDecimal("150000.00"))
                    .confirmed(false).active(true).build();

            ResultMovementDTO confirmedDTO = new ResultMovementDTO();
            confirmedDTO.setId(1L);
            confirmedDTO.setCategoryId(10L);
            confirmedDTO.setConfirmed(true);

            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(unconfirmed));
            when(movementRepository.save(any())).thenReturn(unconfirmed);
            when(movementMapper.toResultDTO(any())).thenReturn(confirmedDTO);

            ResultDTO result = movementService.confirm(1L);

            assertTrue(result.isCorrect());
            verify(movementRepository).save(argThat(m -> m.getConfirmed()));
        }

        @Test
        @DisplayName("Should confirm with new amount")
        void shouldConfirmWithAmount() {
            Movement unconfirmed = Movement.builder()
                    .id(1L).userId(1L).categoryId(10L)
                    .date(LocalDate.of(2026, 3, 15))
                    .amount(new BigDecimal("100000.00"))
                    .confirmed(false).active(true).build();

            ResultMovementDTO confirmedDTO = new ResultMovementDTO();
            confirmedDTO.setId(1L);
            confirmedDTO.setCategoryId(10L);
            confirmedDTO.setConfirmed(true);

            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(unconfirmed));
            when(movementRepository.save(any())).thenReturn(unconfirmed);
            when(movementMapper.toResultDTO(any())).thenReturn(confirmedDTO);

            ResultDTO result = movementService.confirmWithAmount(1L, new BigDecimal("200000"));

            assertTrue(result.isCorrect());
            verify(movementRepository).save(argThat(m ->
                    m.getConfirmed() && m.getAmount().equals(new BigDecimal("200000"))));
        }

        @Test
        @DisplayName("Should fail when movement not found for confirm")
        void shouldFailWhenNotFound() {
            when(movementRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = movementService.confirm(99L);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("GetByPeriod")
    class GetByPeriodTests {

        @Test
        @DisplayName("Should return movements for period")
        void shouldReturnMovementsForPeriod() {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndActiveTrue(1L, start, end))
                    .thenReturn(List.of(testMovement));
            when(movementMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = movementService.getByPeriod(1L, start, end);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<ResultMovementDTO> list = (List<ResultMovementDTO>) result.getObject();
            assertEquals(1, list.size());
        }

        @Test
        @DisplayName("Should return empty list when no movements in period")
        void shouldReturnEmptyForPeriod() {
            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2026, 1, 31);

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndActiveTrue(1L, start, end))
                    .thenReturn(List.of());

            ResultDTO result = movementService.getByPeriod(1L, start, end);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<?> list = (List<?>) result.getObject();
            assertTrue(list.isEmpty());
        }
    }

    @Nested
    @DisplayName("GetChildren")
    class GetChildrenTests {

        @Test
        @DisplayName("Should return children of a parent movement")
        void shouldReturnChildren() {
            Movement parent = Movement.builder()
                    .id(50L).userId(1L).categoryId(10L).date(LocalDate.now())
                    .amount(new BigDecimal("500000")).confirmed(true).active(true).build();
            Movement child = Movement.builder()
                    .id(51L).userId(1L).categoryId(10L).date(LocalDate.now())
                    .amount(new BigDecimal("200000")).parentMovementId(50L).confirmed(true).active(true).build();

            ResultMovementDTO childDTO = new ResultMovementDTO();
            childDTO.setId(51L);
            childDTO.setCategoryId(10L);

            when(movementRepository.findByIdAndActiveTrue(50L)).thenReturn(Optional.of(parent));
            when(movementRepository.findByParentMovementIdAndActiveTrue(50L)).thenReturn(List.of(child));
            when(movementMapper.toResultDTO(child)).thenReturn(childDTO);

            ResultDTO result = movementService.getChildren(50L);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) result.getObject();
            assertEquals(50L, response.get("parentId"));
            assertEquals(1, response.get("childrenCount"));
        }

        @Test
        @DisplayName("Should fail when parent not found")
        void shouldFailWhenParentNotFound() {
            when(movementRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

            ResultDTO result = movementService.getChildren(999L);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle exception in save")
        void shouldHandleExceptionInSave() {
            when(categoryRepository.findByIdAndActiveTrue(anyLong()))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = movementService.saveAndUpdate(testSaveDTO);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }
}
