package com.ohchurus.budget.mapper;

import com.ohchurus.budget.dto.output.ResultScheduledMovementDTO;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.mapper.impl.ScheduledMovementMapperImpl;
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
class ScheduledMovementMapperImplTest {

    @Mock
    private ModelMapper modelMapper;

    private ScheduledMovementMapperImpl scheduledMovementMapper;

    private ScheduledMovement testScheduledMovement;

    @BeforeEach
    void setUp() {
        scheduledMovementMapper = new ScheduledMovementMapperImpl(modelMapper);
        testScheduledMovement = ScheduledMovement.builder()
                .id(1L)
                .userId(1L)
                .categoryId(2L)
                .name("Monthly Rent")
                .amount(new BigDecimal("500.00"))
                .frequency(Frequency.MONTHLY)
                .durationMonths(12)
                .startDate(LocalDate.of(2026, 1, 1))
                .dayOfMonth(1)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("toResultDTO")
    class ToResultDTOTests {

        @Test
        @DisplayName("Should map scheduled movement to ResultScheduledMovementDTO")
        void shouldMapScheduledMovementToResultDTO() {
            ResultScheduledMovementDTO expectedDTO = new ResultScheduledMovementDTO();
            expectedDTO.setId(1L);
            expectedDTO.setUserId(1L);
            expectedDTO.setCategoryId(2L);
            expectedDTO.setName("Monthly Rent");
            expectedDTO.setAmount(new BigDecimal("500.00"));
            expectedDTO.setFrequency(Frequency.MONTHLY);
            expectedDTO.setDurationMonths(12);
            expectedDTO.setActive(true);

            when(modelMapper.map(testScheduledMovement, ResultScheduledMovementDTO.class)).thenReturn(expectedDTO);

            ResultScheduledMovementDTO result = scheduledMovementMapper.toResultDTO(testScheduledMovement);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("Monthly Rent", result.getName());
            assertEquals(new BigDecimal("500.00"), result.getAmount());
            assertEquals(Frequency.MONTHLY, result.getFrequency());
            assertEquals(12, result.getDurationMonths());
            assertTrue(result.getActive());
            verify(modelMapper).map(testScheduledMovement, ResultScheduledMovementDTO.class);
        }

        @Test
        @DisplayName("Should return null when scheduled movement is null")
        void shouldReturnNullWhenScheduledMovementIsNull() {
            ResultScheduledMovementDTO result = scheduledMovementMapper.toResultDTO(null);
            assertNull(result);
        }
    }
}
