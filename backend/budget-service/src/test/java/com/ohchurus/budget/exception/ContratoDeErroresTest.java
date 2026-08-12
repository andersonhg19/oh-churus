package com.ohchurus.budget.exception;

import com.ohchurus.budget.controller.BudgetAllocationController;
import com.ohchurus.budget.controller.ExportController;
import com.ohchurus.budget.controller.HouseholdController;
import com.ohchurus.budget.controller.MovementController;
import com.ohchurus.budget.security.JWTAuthorizationFilter;
import com.ohchurus.budget.security.SecParams;
import com.ohchurus.budget.service.MovementService;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import com.ohchurus.budget.service.impl.ExcelExportService;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================================
 * EL CONTRATO DE ERRORES SE CUMPLE, PASE LO QUE PASE
 * ============================================================================
 *
 * La API entera habla ResultDTO con HTTP 200, y el frontend SOLO sabe leer eso.
 * Pero no habia un solo RestControllerAdvice: un cuerpo vacio, un campo con el
 * tipo cambiado o un texto demasiado largo salian como el 400/500 propio de
 * Spring. El usuario veia "Request failed with status code 400" o una pantalla
 * en blanco sin una sola pista de que campo habia escrito mal.
 *
 * Por cada controller se ataca con las tres formas de romper un cuerpo —vacio,
 * con el tipo cambiado y con un texto que no cabe— y se exige siempre lo mismo:
 * HTTP 200, correct:false y un mensaje que diga QUE campo falla. Ni un 400, ni
 * un 500.
 *
 * Si esta prueba se pone roja, alguien volvio a dejar escapar el error de
 * Spring hacia el movil.
 */
@WebMvcTest({HouseholdController.class, BudgetAllocationController.class,
        MovementController.class, ExportController.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Contrato de errores: siempre 200 con ResultDTO")
class ContratoDeErroresTest {

    private static final String TEXTO_QUE_NO_CABE = "x".repeat(300);

    @Autowired private MockMvc mvc;

    @MockBean private HouseholdServiceImpl householdService;
    @MockBean private BudgetAllocationServiceImpl budgetAllocationService;
    @MockBean private MovementService movementService;
    @MockBean private ExcelExportService excelExportService;
    @MockBean private JWTAuthorizationFilter jwtAuthorizationFilter;
    @MockBean private SecParams secParams;

    /** Ataca una ruta con un cuerpo roto y exige que la respuesta siga el contrato. */
    private void exigeContrato(String ruta, String cuerpo, String pistaEsperada) throws Exception {
        mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.errorCode").value(400))
                .andExpect(jsonPath("$.message").value(containsString(pistaEsperada)));
    }

    @Nested
    @DisplayName("HouseholdController")
    class Hogares {

        @Test
        @DisplayName("cuerpo vacio")
        void cuerpoVacio() throws Exception {
            exigeContrato("/v1/household/create", "", "vacio");
        }

        @Test
        @DisplayName("campo con tipo incorrecto")
        void tipoIncorrecto() throws Exception {
            exigeContrato("/v1/household/add-member",
                    "{\"householdId\":\"no-soy-un-numero\",\"userId\":2}", "'householdId'");
        }

        @Test
        @DisplayName("texto demasiado largo")
        void textoDemasiadoLargo() throws Exception {
            exigeContrato("/v1/household/create",
                    "{\"name\":\"" + TEXTO_QUE_NO_CABE + "\"}", "'name'");
        }
    }

    @Nested
    @DisplayName("BudgetAllocationController")
    class Asignaciones {

        @Test
        @DisplayName("cuerpo vacio")
        void cuerpoVacio() throws Exception {
            exigeContrato("/v1/budget-allocation/save", "", "vacio");
        }

        @Test
        @DisplayName("campo con tipo incorrecto")
        void tipoIncorrecto() throws Exception {
            exigeContrato("/v1/budget-allocation/save",
                    "{\"categoryId\":\"veinte\",\"amount\":100}", "'categoryId'");
        }

        @Test
        @DisplayName("texto demasiado largo")
        void textoDemasiadoLargo() throws Exception {
            exigeContrato("/v1/budget-allocation/save",
                    "{\"categoryId\":20,\"amount\":100,\"notes\":\"" + TEXTO_QUE_NO_CABE + "\"}",
                    "'notes'");
        }
    }

    @Nested
    @DisplayName("MovementController (transfer)")
    class Transferencias {

        @Test
        @DisplayName("cuerpo vacio")
        void cuerpoVacio() throws Exception {
            exigeContrato("/v1/movements/transfer", "", "vacio");
        }

        @Test
        @DisplayName("campo con tipo incorrecto")
        void tipoIncorrecto() throws Exception {
            exigeContrato("/v1/movements/transfer",
                    "{\"fromCategoryId\":\"treinta\",\"toCategoryId\":20,\"amount\":50000}",
                    "'fromCategoryId'");
        }

        @Test
        @DisplayName("texto demasiado largo")
        void textoDemasiadoLargo() throws Exception {
            exigeContrato("/v1/movements/transfer",
                    "{\"fromCategoryId\":30,\"toCategoryId\":20,\"amount\":50000,"
                            + "\"description\":\"" + TEXTO_QUE_NO_CABE + "\"}", "'description'");
        }

        @Test
        @DisplayName("faltan los ids obligatorios y se dice cuales")
        void faltanLosIds() throws Exception {
            exigeContrato("/v1/movements/transfer", "{}", "'fromCategoryId'");
        }
    }

    @Nested
    @DisplayName("ExportController")
    class Exportacion {

        @Test
        @DisplayName("cuerpo vacio")
        void cuerpoVacio() throws Exception {
            exigeContrato("/v1/export/excel", "", "vacio");
        }

        @Test
        @DisplayName("campo con tipo incorrecto")
        void tipoIncorrecto() throws Exception {
            exigeContrato("/v1/export/excel", "{\"budgetStartDay\":\"lunes\"}", "'budgetStartDay'");
        }

        @Test
        @DisplayName("texto demasiado largo")
        void textoDemasiadoLargo() throws Exception {
            exigeContrato("/v1/export/excel",
                    "{\"referenceDate\":\"" + TEXTO_QUE_NO_CABE + "\"}", "'referenceDate'");
        }
    }

    @Nested
    @DisplayName("La red final")
    class RedFinal {

        @Test
        @DisplayName("una excepcion no capturada tampoco rompe el contrato")
        void excepcionNoCapturada() throws Exception {
            when(movementService.getById(anyLong())).thenThrow(new IllegalStateException("boom"));

            mvc.perform(post("/v1/movements/get/1").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false))
                    .andExpect(jsonPath("$.errorCode").value(500))
                    .andExpect(jsonPath("$.message").value(containsString("error inesperado")));
        }

        @Test
        @DisplayName("un acceso denegado se cuenta, no se estrella")
        void accesoDenegado() throws Exception {
            when(householdService.getByUser(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new org.springframework.security.access.AccessDeniedException("nope"));

            mvc.perform(post("/v1/household/by-user")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false))
                    .andExpect(jsonPath("$.errorCode").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("permiso")));
        }

        @Test
        @DisplayName("un id de la ruta que no es un numero")
        void idDeRutaQueNoEsNumero() throws Exception {
            mvc.perform(post("/v1/movements/delete/abc").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false))
                    .andExpect(jsonPath("$.errorCode").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("'id'")));
        }
    }
}
