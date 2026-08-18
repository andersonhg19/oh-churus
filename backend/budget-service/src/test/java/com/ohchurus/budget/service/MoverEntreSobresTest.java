package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.impl.AccountServiceImpl;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import com.ohchurus.budget.service.impl.RepartoDeGastos;
import com.ohchurus.budget.util.ControlAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mover plata de un sobre a otro, y los sobres del periodo.
 *
 * La regla del arrastre la prueba SobresTest, que es codigo puro. Aqui estan
 * las NEGATIVAS de la operacion que hace util esa regla: cuando te pasaste en
 * Restaurantes, la respuesta no es sentirse mal, es decidir de que otro sobre
 * sale la plata. Y una operacion que mueve plata entre dos sitios tiene el
 * doble de formas de salir mal que una que la mueve a uno solo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sobres: mover entre dos, y las formas de que no se pueda")
class MoverEntreSobresTest {

    private static final Long YO = 1L;
    private static final Long OTRO = 2L;
    private static final Long MERCADO = 10L;
    private static final Long RESTAURANTES = 20L;
    private static final LocalDate HOY = LocalDate.now();

    @Mock private BudgetAllocationRepository allocationRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private HouseholdServiceImpl householdService;
    @Mock private ControlAcceso acceso;
    @Mock private AccountServiceImpl cuentas;
    @Mock private RepartoDeGastos reparto;

    @InjectMocks private BudgetAllocationServiceImpl servicio;

    @BeforeEach
    void soyYo() {
        lenient().when(acceso.usuarioActual()).thenReturn(YO);
        lenient().when(acceso.puedeVerCategoria(any())).thenReturn(true);
        lenient().when(householdService.getHouseholdIds(any())).thenReturn(List.of());
        lenient().when(reparto.misPartes(any(), any())).thenReturn(Map.of());
    }

    private Category categoria(Long id, Long duena) {
        return Category.builder().id(id).userId(duena).name("cat")
                .type(CategoryType.EXPENSE).active(true).build();
    }

    private BudgetAllocation asignacion(Long categoriaId, String cuanto) {
        return BudgetAllocation.builder()
                .id(categoriaId).userId(YO).categoryId(categoriaId)
                .periodStart(HOY.withDayOfMonth(1))
                .periodEnd(HOY.withDayOfMonth(28))
                .allocatedAmount(new BigDecimal(cuanto)).active(true).build();
    }

    private ResultDTO mover(String cuanto) {
        return servicio.move(MERCADO, RESTAURANTES, new BigDecimal(cuanto), 1, HOY);
    }

    // ========================================================================

    @Nested
    @DisplayName("Lo que no se puede mover")
    class LoQueNoSePuede {

        @Test
        @DisplayName("faltando datos no se mueve nada")
        void sinDatos() {
            assertThat(servicio.move(null, RESTAURANTES, BigDecimal.TEN, 1, HOY).isCorrect()).isFalse();
            assertThat(servicio.move(MERCADO, null, BigDecimal.TEN, 1, HOY).isCorrect()).isFalse();
            assertThat(servicio.move(MERCADO, RESTAURANTES, null, 1, HOY).isCorrect()).isFalse();
            verify(allocationRepository, never()).save(any());
        }

        @Test
        @DisplayName("de un sobre a si mismo no es mover, es no hacer nada")
        void alMismoSobre() {
            ResultDTO r = servicio.move(MERCADO, MERCADO, BigDecimal.TEN, 1, HOY);

            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("el mismo sobre");
        }

        @Test
        @DisplayName("un importe de cero o negativo se rechaza")
        void importeInvalido() {
            /* Mover -50.000 de A a B es mover 50.000 de B a A por la puerta de
               atras, saltandose la comprobacion de que en B haya tanto. */
            assertThat(servicio.move(MERCADO, RESTAURANTES, BigDecimal.ZERO, 1, HOY).isCorrect()).isFalse();
            assertThat(servicio.move(MERCADO, RESTAURANTES, new BigDecimal("-50000"), 1, HOY).isCorrect())
                    .isFalse();
            verify(allocationRepository, never()).save(any());
        }

        @Test
        @DisplayName("no se puede sacar de un sobre que no es tuyo")
        void elOrigenAjeno() {
            /* La trampa de esta operacion: toca DOS categorias y es facil
               acordarse solo del destino. Con esa mitad, se le vacia el
               presupuesto a otra persona sin tocar un solo movimiento. */
            when(categoryRepository.findByIdAndActiveTrue(MERCADO))
                    .thenReturn(Optional.of(categoria(MERCADO, OTRO)));
            when(categoryRepository.findByIdAndActiveTrue(RESTAURANTES))
                    .thenReturn(Optional.of(categoria(RESTAURANTES, YO)));
            when(acceso.puedeVerCategoria(any())).thenAnswer(inv -> {
                Category c = inv.getArgument(0);
                return YO.equals(c.getUserId());
            });

            assertThat(mover("50000").isCorrect()).isFalse();
            verify(allocationRepository, never()).save(any());
        }

        @Test
        @DisplayName("ni meter en un sobre que no es tuyo")
        void elDestinoAjeno() {
            when(categoryRepository.findByIdAndActiveTrue(MERCADO))
                    .thenReturn(Optional.of(categoria(MERCADO, YO)));
            when(categoryRepository.findByIdAndActiveTrue(RESTAURANTES))
                    .thenReturn(Optional.of(categoria(RESTAURANTES, OTRO)));
            when(acceso.puedeVerCategoria(any())).thenAnswer(inv -> {
                Category c = inv.getArgument(0);
                return YO.equals(c.getUserId());
            });

            assertThat(mover("50000").isCorrect()).isFalse();
            verify(allocationRepository, never()).save(any());
        }

        @Test
        @DisplayName("una categoria que no existe se trata igual que una ajena")
        void categoriaInexistente() {
            when(categoryRepository.findByIdAndActiveTrue(any())).thenReturn(Optional.empty());
            assertThat(mover("50000").isCorrect()).isFalse();
        }

        @Test
        @DisplayName("no se puede mover mas de lo que hay en el sobre de origen")
        void noHayTanto() {
            /* Sin esto se inventaria plata: el destino subiria 500.000 y el
               origen quedaria en negativo, que es un estado que la regla
               asimetrica no contempla siquiera. */
            prepararCategorias();
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(any(), any()))
                    .thenReturn(List.of(asignacion(MERCADO, "100000")));

            ResultDTO r = mover("500000");

            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("no hay tanto");
            verify(allocationRepository, never()).save(any());
        }

        @Test
        @DisplayName("mover desde un sobre que no tiene asignacion tampoco inventa plata")
        void origenSinAsignacion() {
            prepararCategorias();
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(any(), any()))
                    .thenReturn(List.of());

            assertThat(mover("50000").isCorrect()).isFalse();
            verify(allocationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Lo que si se puede")
    class LoQueSiSePuede {

        @Test
        @DisplayName("mover baja el origen y sube el destino por el mismo importe")
        void moverCuadra() {
            prepararCategorias();
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(any(), any()))
                    .thenReturn(List.of(asignacion(MERCADO, "500000"), asignacion(RESTAURANTES, "200000")));
            when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertThat(mover("50000").isCorrect()).isTrue();

            ArgumentCaptor<BudgetAllocation> guardadas =
                    ArgumentCaptor.forClass(BudgetAllocation.class);
            verify(allocationRepository, times(2)).save(guardadas.capture());

            BigDecimal origen = guardadas.getAllValues().get(0).getAllocatedAmount();
            BigDecimal destino = guardadas.getAllValues().get(1).getAllocatedAmount();
            assertThat(origen)
                    .as("mover no puede crear ni destruir plata: lo que baja de un lado "
                            + "sube exactamente igual del otro")
                    .isEqualByComparingTo("450000");
            assertThat(destino).isEqualByComparingTo("250000");
        }

        @Test
        @DisplayName("si el destino no tenia presupuesto, se le crea")
        void destinoSinAsignacionPrevia() {
            /* Mover HACIA algo que aun no tiene presupuesto es justo lo que se
               quiere poder hacer: te pasaste en Restaurantes y decides que
               este mes salga de un sobre que todavia no habias abierto. */
            prepararCategorias();
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(any(), any()))
                    .thenReturn(List.of(asignacion(MERCADO, "500000")));
            when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertThat(mover("50000").isCorrect()).isTrue();

            ArgumentCaptor<BudgetAllocation> guardadas =
                    ArgumentCaptor.forClass(BudgetAllocation.class);
            verify(allocationRepository, times(2)).save(guardadas.capture());
            BudgetAllocation nueva = guardadas.getAllValues().get(1);
            assertThat(nueva.getCategoryId()).isEqualTo(RESTAURANTES);
            assertThat(nueva.getAllocatedAmount()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("mover justo lo que hay deja el sobre en cero, no en error")
        void moverTodo() {
            prepararCategorias();
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(any(), any()))
                    .thenReturn(List.of(asignacion(MERCADO, "100000")));
            when(allocationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertThat(mover("100000").isCorrect()).isTrue();
        }
    }

    @Nested
    @DisplayName("El estado de los sobres")
    class EstadoDeLosSobres {

        @Test
        @DisplayName("sin sesion devuelve vacio en vez de romper")
        void sinSesion() {
            when(acceso.usuarioActual()).thenReturn(null);
            assertThat(servicio.envelopes(1, HOY).isCorrect()).isTrue();
        }

        @Test
        @DisplayName("sin hogar se consulta solo lo propio: un IN vacio revienta en algunos dialectos")
        void sinHogar() {
            when(allocationRepository.findByUserIdAndActiveTrueOrderByPeriodStartAsc(YO))
                    .thenReturn(List.of());
            when(movementRepository.findByUserIdAndActiveTrue(YO)).thenReturn(List.of());
            when(categoryRepository.findByUserIdAndActiveTrue(YO)).thenReturn(List.of());

            assertThat(servicio.envelopes(1, HOY).isCorrect()).isTrue();
            verify(allocationRepository, never()).findTodasParaElArrastre(any(), any());
        }

        @Test
        @DisplayName("con hogar se traen tambien las asignaciones compartidas")
        void conHogar() {
            when(householdService.getHouseholdIds(YO)).thenReturn(List.of(5L));
            when(allocationRepository.findTodasParaElArrastre(YO, List.of(5L))).thenReturn(List.of());
            when(movementRepository.findParaBalances(YO, List.of(5L))).thenReturn(List.of());
            when(categoryRepository.findByUserIdAndActiveTrue(YO)).thenReturn(List.of());
            when(categoryRepository.findByHouseholdIdAndActiveTrue(5L)).thenReturn(List.of());

            assertThat(servicio.envelopes(1, HOY).isCorrect()).isTrue();
            verify(allocationRepository, never()).findByUserIdAndActiveTrueOrderByPeriodStartAsc(any());
        }

        @Test
        @DisplayName("sin dia de corte ni fecha usa valores por defecto en vez de romper")
        void sinParametros() {
            when(allocationRepository.findByUserIdAndActiveTrueOrderByPeriodStartAsc(YO))
                    .thenReturn(List.of());
            when(movementRepository.findByUserIdAndActiveTrue(YO)).thenReturn(List.of());
            when(categoryRepository.findByUserIdAndActiveTrue(YO)).thenReturn(List.of());

            assertThat(servicio.envelopes(null, null).isCorrect()).isTrue();
        }
    }

    private void prepararCategorias() {
        when(categoryRepository.findByIdAndActiveTrue(MERCADO))
                .thenReturn(Optional.of(categoria(MERCADO, YO)));
        when(categoryRepository.findByIdAndActiveTrue(RESTAURANTES))
                .thenReturn(Optional.of(categoria(RESTAURANTES, YO)));
    }
}
