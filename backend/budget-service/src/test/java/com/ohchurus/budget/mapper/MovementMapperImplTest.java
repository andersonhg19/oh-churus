package com.ohchurus.budget.mapper;

import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.mapper.impl.MovementMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovementMapperImplTest {

    @Mock
    private ModelMapper modelMapper;

    private MovementMapperImpl movementMapper;

    private Movement testMovement;

    @BeforeEach
    void setUp() {
        movementMapper = new MovementMapperImpl(modelMapper);
        testMovement = Movement.builder()
                .id(1L)
                .userId(1L)
                .categoryId(2L)
                .date(LocalDate.of(2026, 3, 15))
                .amount(new BigDecimal("150.00"))
                .description("Grocery shopping")
                .confirmed(true)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("toResultDTO")
    class ToResultDTOTests {

        @Test
        @DisplayName("Should map movement to ResultMovementDTO")
        void shouldMapMovementToResultDTO() {
            ResultMovementDTO expectedDTO = new ResultMovementDTO();
            expectedDTO.setId(1L);
            expectedDTO.setUserId(1L);
            expectedDTO.setCategoryId(2L);
            expectedDTO.setAmount(new BigDecimal("150.00"));
            expectedDTO.setConfirmed(true);

            when(modelMapper.map(testMovement, ResultMovementDTO.class)).thenReturn(expectedDTO);

            ResultMovementDTO result = movementMapper.toResultDTO(testMovement);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(1L, result.getUserId());
            assertEquals(2L, result.getCategoryId());
            assertEquals(new BigDecimal("150.00"), result.getAmount());
            assertTrue(result.getConfirmed());
            verify(modelMapper).map(testMovement, ResultMovementDTO.class);
        }

        @Test
        @DisplayName("Should return null when movement is null")
        void shouldReturnNullWhenMovementIsNull() {
            ResultMovementDTO result = movementMapper.toResultDTO(null);
            assertNull(result);
        }
    }
}
