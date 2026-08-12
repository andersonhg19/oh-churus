package com.ohchurus.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.dto.input.CategoryFilterDTO;
import com.ohchurus.budget.dto.input.CategorySaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.security.JWTAuthorizationFilter;
import com.ohchurus.budget.security.SecParams;
import com.ohchurus.budget.service.CategoryService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {
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
    private CategoryService categoryService;

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
    @DisplayName("POST /v1/categories/save")
    class SaveTests {

        @Test
        @DisplayName("Should save category and return 200")
        void shouldSaveCategory() throws Exception {
            CategorySaveDTO dto = new CategorySaveDTO();
            dto.setUserId(1L);
            dto.setName("Food");
            dto.setType(CategoryType.EXPENSE);

            when(categoryService.saveAndUpdate(any(CategorySaveDTO.class))).thenReturn(successResult);

            mockMvc.perform(post("/v1/categories/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(categoryService).saveAndUpdate(any(CategorySaveDTO.class));
        }

        @Test
        @DisplayName("Should return error when service fails")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            CategorySaveDTO dto = new CategorySaveDTO();
            dto.setUserId(1L);
            dto.setName("Food");
            dto.setType(CategoryType.EXPENSE);

            when(categoryService.saveAndUpdate(any(CategorySaveDTO.class))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/categories/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false))
                    .andExpect(jsonPath("$.errorCode").value(100));
        }

        @Test
        @DisplayName("funciona SIN userId en el cuerpo: la identidad la pone el token")
        void funcionaSinUserIdEnElCuerpo() throws Exception {
            /* Estas pruebas exigian un 400 si faltaba el userId. Ese contrato
               desaparecio con el arreglo de seguridad: el userId ya no se pide
               al cliente —lo ponia el cliente, y por eso podia poner el de
               otro—, sale del token. Que una peticion sin userId funcione es
               ahora la prueba de que la identidad viene del sitio correcto. */
            CategorySaveDTO dto = new CategorySaveDTO();
            dto.setName("Food");
            dto.setType(CategoryType.EXPENSE);

            mockMvc.perform(post("/v1/categories/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        /* Estas dos pruebas exigian un 400 de Spring. Certificaban justo lo que
           rompe al frontend: toda la API habla ResultDTO con HTTP 200 y el
           frontend solo sabe leer eso, asi que un 400 se convertia en
           "Request failed with status code 400". Ahora se exige lo que de
           verdad hace falta: 200, correct:false y el nombre del campo malo. */

        @Test
        @DisplayName("nombre en blanco: 200 con correct:false y el campo que falla")
        void nombreEnBlancoRespondeDentroDelContrato() throws Exception {
            CategorySaveDTO dto = new CategorySaveDTO();
            dto.setUserId(1L);
            dto.setName("");
            dto.setType(CategoryType.EXPENSE);

            mockMvc.perform(post("/v1/categories/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("'name'")));
        }

        @Test
        @DisplayName("tipo ausente: 200 con correct:false y el campo que falla")
        void tipoAusenteRespondeDentroDelContrato() throws Exception {
            CategorySaveDTO dto = new CategorySaveDTO();
            dto.setUserId(1L);
            dto.setName("Food");

            mockMvc.perform(post("/v1/categories/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("'type'")));
        }
    }

    @Nested
    @DisplayName("POST /v1/categories/get/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Should return category by id")
        void shouldReturnCategoryById() throws Exception {
            when(categoryService.getById(1L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/categories/get/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(categoryService).getById(1L);
        }

        @Test
        @DisplayName("Should return error when not found")
        void shouldReturnErrorWhenNotFound() throws Exception {
            when(categoryService.getById(99L)).thenReturn(errorResult);

            mockMvc.perform(post("/v1/categories/get/99")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));

            verify(categoryService).getById(99L);
        }
    }

    @Nested
    @DisplayName("POST /v1/categories/all")
    class GetAllTests {

        @Test
        @DisplayName("Should return all categories with filters")
        void shouldReturnAllCategories() throws Exception {
            CategoryFilterDTO filter = new CategoryFilterDTO();
            filter.setUserId(1L);

            when(categoryService.getAll(any(CategoryFilterDTO.class))).thenReturn(successResult);

            mockMvc.perform(post("/v1/categories/all")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(filter)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(categoryService).getAll(any(CategoryFilterDTO.class));
        }

        @Test
        @DisplayName("Should return error on service failure")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            CategoryFilterDTO filter = new CategoryFilterDTO();

            when(categoryService.getAll(any(CategoryFilterDTO.class))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/categories/all")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(filter)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }

    @Nested
    @DisplayName("POST /v1/categories/tree")
    class GetTreeTests {

        @Test
        @DisplayName("Should return category tree")
        void shouldReturnTree() throws Exception {
            when(categoryService.getTree(1L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/categories/tree")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("userId", 1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(categoryService).getTree(1L);
        }

        @Test
        @DisplayName("Should return error when service fails")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            when(categoryService.getTree(1L)).thenReturn(errorResult);

            mockMvc.perform(post("/v1/categories/tree")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("userId", 1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }

    @Nested
    @DisplayName("POST /v1/categories/delete/{id}")
    class DeleteTests {

        @Test
        @DisplayName("Should delete category")
        void shouldDeleteCategory() throws Exception {
            when(categoryService.delete(1L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/categories/delete/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(categoryService).delete(1L);
        }

        @Test
        @DisplayName("Should return error when delete fails")
        void shouldReturnErrorOnDeleteFailure() throws Exception {
            when(categoryService.delete(1L)).thenReturn(errorResult);

            mockMvc.perform(post("/v1/categories/delete/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }

    @Nested
    @DisplayName("POST /v1/categories/type-list")
    class TypeListTests {

        @Test
        @DisplayName("Should return type list")
        void shouldReturnTypeList() throws Exception {
            ResultDTO typeResult = new ResultDTO(List.of(
                    Map.of("key", "INCOME", "name", "Income"),
                    Map.of("key", "EXPENSE", "name", "Expense")));
            when(categoryService.typeList()).thenReturn(typeResult);

            mockMvc.perform(post("/v1/categories/type-list")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true))
                    .andExpect(jsonPath("$.object").isArray());

            verify(categoryService).typeList();
        }

        @Test
        @DisplayName("Should return error when type list fails")
        void shouldReturnErrorOnTypeListFailure() throws Exception {
            when(categoryService.typeList()).thenReturn(errorResult);

            mockMvc.perform(post("/v1/categories/type-list")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }
}
