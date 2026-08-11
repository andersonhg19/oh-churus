package com.ohchurus.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.dto.input.GeneratePendingRequestDTO;
import com.ohchurus.budget.dto.input.ScheduledMovementFilterDTO;
import com.ohchurus.budget.dto.input.ScheduledMovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.security.JWTAuthorizationFilter;
import com.ohchurus.budget.security.SecParams;
import com.ohchurus.budget.service.ScheduledMovementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScheduledMovementController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScheduledMovementControllerTest {
    /* Los controllers ya no aceptan el userId del cuerpo: lo sacan del token.
       Estas pruebas usan @WebMvcTest con los filtros apagados, asi que no hay
       token; se planta la identidad a mano para poder seguir probando el
       CONTROLLER. Que el userId del cuerpo se ignore de verdad lo demuestra
       AislamientoEntreUsuariosTest con la aplicacion entera levantada. */
    @org.junit.jupiter.api.BeforeEach
    void autenticarComoUsuario1() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "usuario1@ohchurus.com", null, java.util.Collections.emptyList());
        auth.setDetails(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }

    @org.junit.jupiter.api.AfterEach
    void limpiarSesion() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScheduledMovementService scheduledMovementService;

    @MockBean
    private JWTAuthorizationFilter jwtAuthorizationFilter;

    @MockBean
    private SecParams secParams;

    private ResultDTO successResult;
    private ResultDTO errorResult;

    @BeforeEach
    void setUp() {
        successResult = new ResultDTO("data");
        errorResult = new ResultDTO(false, "Error", 100);
    }

    @Nested
    @DisplayName("POST /v1/scheduled/save")
    class SaveTests {

        @Test
        @DisplayName("Should save scheduled movement and return 200")
        void shouldSaveScheduledMovement() throws Exception {
            ScheduledMovementSaveDTO dto = new ScheduledMovementSaveDTO();
            dto.setUserId(1L);
            dto.setCategoryId(1L);
            dto.setName("Rent");
            dto.setAmount(new BigDecimal("500.00"));
            dto.setFrequency(Frequency.MONTHLY);
            dto.setStartDate(LocalDate.of(2026, 3, 1));

            when(scheduledMovementService.saveAndUpdate(any(ScheduledMovementSaveDTO.class))).thenReturn(successResult);

            mockMvc.perform(post("/v1/scheduled/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(scheduledMovementService).saveAndUpdate(any(ScheduledMovementSaveDTO.class));
        }

        @Test
        @DisplayName("Should return error when service fails")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            ScheduledMovementSaveDTO dto = new ScheduledMovementSaveDTO();
            dto.setUserId(1L);
            dto.setCategoryId(1L);
            dto.setName("Rent");
            dto.setFrequency(Frequency.MONTHLY);
            dto.setStartDate(LocalDate.of(2026, 3, 1));

            when(scheduledMovementService.saveAndUpdate(any(ScheduledMovementSaveDTO.class))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/scheduled/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }

        @Test
        @DisplayName("Should return 400 when name is blank")
        void shouldReturn400WhenNameBlank() throws Exception {
            ScheduledMovementSaveDTO dto = new ScheduledMovementSaveDTO();
            dto.setUserId(1L);
            dto.setCategoryId(1L);
            dto.setName("");
            dto.setFrequency(Frequency.MONTHLY);
            dto.setStartDate(LocalDate.of(2026, 3, 1));

            mockMvc.perform(post("/v1/scheduled/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when frequency is missing")
        void shouldReturn400WhenFrequencyMissing() throws Exception {
            ScheduledMovementSaveDTO dto = new ScheduledMovementSaveDTO();
            dto.setUserId(1L);
            dto.setCategoryId(1L);
            dto.setName("Rent");
            dto.setStartDate(LocalDate.of(2026, 3, 1));

            mockMvc.perform(post("/v1/scheduled/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            ScheduledMovementSaveDTO dto = new ScheduledMovementSaveDTO();
            dto.setCategoryId(1L);
            dto.setName("Rent");
            dto.setFrequency(Frequency.MONTHLY);
            dto.setStartDate(LocalDate.of(2026, 3, 1));

            mockMvc.perform(post("/v1/scheduled/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/scheduled/get/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Should return scheduled movement by id")
        void shouldReturnById() throws Exception {
            when(scheduledMovementService.getById(1L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/scheduled/get/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(scheduledMovementService).getById(1L);
        }

        @Test
        @DisplayName("Should return error when not found")
        void shouldReturnErrorWhenNotFound() throws Exception {
            when(scheduledMovementService.getById(99L)).thenReturn(errorResult);

            mockMvc.perform(post("/v1/scheduled/get/99")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));

            verify(scheduledMovementService).getById(99L);
        }
    }

    @Nested
    @DisplayName("POST /v1/scheduled/all")
    class GetAllTests {

        @Test
        @DisplayName("Should return all scheduled movements")
        void shouldReturnAll() throws Exception {
            ScheduledMovementFilterDTO filter = new ScheduledMovementFilterDTO();
            filter.setUserId(1L);

            when(scheduledMovementService.getAll(any(ScheduledMovementFilterDTO.class))).thenReturn(successResult);

            mockMvc.perform(post("/v1/scheduled/all")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(filter)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(scheduledMovementService).getAll(any(ScheduledMovementFilterDTO.class));
        }

        @Test
        @DisplayName("Should return error on service failure")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            ScheduledMovementFilterDTO filter = new ScheduledMovementFilterDTO();

            when(scheduledMovementService.getAll(any(ScheduledMovementFilterDTO.class))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/scheduled/all")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(filter)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }

    @Nested
    @DisplayName("POST /v1/scheduled/delete/{id}")
    class DeleteTests {

        @Test
        @DisplayName("Should delete scheduled movement")
        void shouldDelete() throws Exception {
            when(scheduledMovementService.delete(1L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/scheduled/delete/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(scheduledMovementService).delete(1L);
        }

        @Test
        @DisplayName("Should return error when delete fails")
        void shouldReturnErrorOnDeleteFailure() throws Exception {
            when(scheduledMovementService.delete(1L)).thenReturn(errorResult);

            mockMvc.perform(post("/v1/scheduled/delete/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }

    @Nested
    @DisplayName("POST /v1/scheduled/generate-pending")
    class GeneratePendingTests {

        @Test
        @DisplayName("Should generate pending movements")
        void shouldGeneratePending() throws Exception {
            GeneratePendingRequestDTO dto = new GeneratePendingRequestDTO();
            dto.setUserId(1L);
            dto.setBudgetStartDay(15);

            when(scheduledMovementService.generatePending(eq(1L), eq(15))).thenReturn(successResult);

            mockMvc.perform(post("/v1/scheduled/generate-pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(scheduledMovementService).generatePending(eq(1L), eq(15));
        }

        @Test
        @DisplayName("Should return 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            GeneratePendingRequestDTO dto = new GeneratePendingRequestDTO();
            dto.setBudgetStartDay(15);

            mockMvc.perform(post("/v1/scheduled/generate-pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when budgetStartDay is missing")
        void shouldReturn400WhenBudgetStartDayMissing() throws Exception {
            GeneratePendingRequestDTO dto = new GeneratePendingRequestDTO();
            dto.setUserId(1L);

            mockMvc.perform(post("/v1/scheduled/generate-pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/scheduled/frequency-list")
    class FrequencyListTests {

        @Test
        @DisplayName("Should return frequency list")
        void shouldReturnFrequencyList() throws Exception {
            ResultDTO freqResult = new ResultDTO(List.of(
                    Map.of("key", "MONTHLY", "name", "Monthly")));
            when(scheduledMovementService.frequencyList()).thenReturn(freqResult);

            mockMvc.perform(post("/v1/scheduled/frequency-list")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true))
                    .andExpect(jsonPath("$.object").isArray());

            verify(scheduledMovementService).frequencyList();
        }

        @Test
        @DisplayName("Should return error when frequency list fails")
        void shouldReturnErrorOnFrequencyListFailure() throws Exception {
            when(scheduledMovementService.frequencyList()).thenReturn(errorResult);

            mockMvc.perform(post("/v1/scheduled/frequency-list")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }
}
