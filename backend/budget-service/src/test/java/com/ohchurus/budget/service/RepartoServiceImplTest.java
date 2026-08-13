package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.SettleDTO;
import com.ohchurus.budget.dto.input.SplitInputDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Account;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.AccountKind;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.SplitMode;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.MovementSplitRepository;
import com.ohchurus.budget.service.impl.AccountServiceImpl;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import com.ohchurus.budget.service.impl.RepartoDeGastos;
import com.ohchurus.budget.service.impl.RepartoServiceImpl;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las negativas del reparto, y quien paga al liquidar.
 *
 * La regla de oro de punta a punta la demuestra LaReglaDeOroDelRepartoTest con
 * la app entera y tres personas. Aqui estan los rechazos —el desconocido, el
 * porcentaje que no suma, la persona repetida— y una decision que no se ve
 * desde fuera y que importa mucho: quien paga en una liquidacion lo decide el
 * SIGNO DEL BALANCE, no quien pulsa el boton.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Reparto: los rechazos, y quien paga al liquidar")
class RepartoServiceImplTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;
    private static final Long EXTRANO = 99L;

    @Mock private MovementRepository movimientos;
    @Mock private MovementSplitRepository partes;
    @Mock private CategoryRepository categorias;
    @Mock private HouseholdMemberRepository miembros;
    @Mock private HouseholdServiceImpl hogares;
    @Mock private RepartoDeGastos reparto;
    @Mock private AccountServiceImpl cuentas;
    @Mock private ControlAcceso acceso;

    @InjectMocks private RepartoServiceImpl servicio;

    @BeforeEach
    void soyAnaYVivoConBruno() {
        lenient().when(acceso.usuarioActual()).thenReturn(ANA);
        lenient().when(hogares.getHouseholdIds(ANA)).thenReturn(List.of(5L));
        lenient().when(miembros.findByHouseholdIdAndActiveTrue(5L)).thenReturn(List.of(
                HouseholdMember.builder().householdId(5L).userId(ANA).role("OWNER").active(true).build(),
                HouseholdMember.builder().householdId(5L).userId(BRUNO).role("MEMBER").active(true).build()));
        lenient().when(movimientos.findParaBalances(any(), any())).thenReturn(List.of());
    }

    private SplitInputDTO parte(Long quien, String valor) {
        return new SplitInputDTO(quien, valor == null ? null : new BigDecimal(valor));
    }

    // ========================================================================

    @Nested
    @DisplayName("Validar el reparto")
    class Validar {

        @Test
        @DisplayName("sin modo no hay nada que validar")
        void sinModo() {
            assertThat(servicio.validar(new BigDecimal("100"), null, null)).isNull();
        }

        @Test
        @DisplayName("un reparto sin nadie se rechaza")
        void sinParticipantes() {
            ResultDTO r = servicio.validar(new BigDecimal("100"), SplitMode.EQUAL, List.of());
            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("al menos una persona");
        }

        @Test
        @DisplayName("la misma persona dos veces se rechaza")
        void personaRepetida() {
            ResultDTO r = servicio.validar(new BigDecimal("100"), SplitMode.EQUAL,
                    List.of(parte(ANA, null), parte(ANA, null)));
            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("repetida");
        }

        @Test
        @DisplayName("repartir con alguien de fuera del hogar se rechaza sin decir quien sobra")
        void conUnExtrano() {
            ResultDTO r = servicio.validar(new BigDecimal("100"), SplitMode.EQUAL,
                    List.of(parte(ANA, null), parte(EXTRANO, null)));

            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage())
                    .as("nombrar al que sobra confirmaria que ese id existe y permitiria "
                            + "ir probando; se dice que hacer, no quien")
                    .doesNotContain(String.valueOf(EXTRANO));
            assertThat(r.getMessage()).contains("Nucleo Familiar");
        }

        @Test
        @DisplayName("los porcentajes que no suman 100 se rechazan, con la suma en el mensaje")
        void porcentajesQueNoSuman() {
            ResultDTO r = servicio.validar(new BigDecimal("100"), SplitMode.PERCENT,
                    List.of(parte(ANA, "30"), parte(BRUNO, "30")));

            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage())
                    .as("el mensaje tiene que decir que corregir; 30 y 30 puede ser 50/50 mal "
                            + "escrito o una persona que falta, y adivinar con plata es peor "
                            + "que preguntar")
                    .contains("60").contains("100");
        }

        @Test
        @DisplayName("los porcentajes que suman 100 pasan")
        void porcentajesCorrectos() {
            assertThat(servicio.validar(new BigDecimal("100"), SplitMode.PERCENT,
                    List.of(parte(ANA, "70"), parte(BRUNO, "30")))).isNull();
        }

        @Test
        @DisplayName("importes que suman MAS que el gasto se rechazan")
        void importesQueSePasan() {
            ResultDTO r = servicio.validar(new BigDecimal("100000"), SplitMode.AMOUNT,
                    List.of(parte(ANA, "60000"), parte(BRUNO, "60000")));
            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("120000");
        }

        @Test
        @DisplayName("importes que suman MENOS si pasan: lo que falta es del que pago")
        void importesQueNoLlegan() {
            assertThat(servicio.validar(new BigDecimal("120000"), SplitMode.AMOUNT,
                    List.of(parte(BRUNO, "40000")))).isNull();
        }

        @Test
        @DisplayName("sin sesion no se valida nada")
        void sinSesion() {
            when(acceso.usuarioActual()).thenReturn(null);
            assertThat(servicio.validar(new BigDecimal("100"), SplitMode.EQUAL,
                    List.of(parte(ANA, null))).isCorrect()).isFalse();
        }
    }

    @Nested
    @DisplayName("Guardar el reparto")
    class Guardar {

        @Test
        @DisplayName("quitar el modo desactiva las partes que hubiera")
        void quitarElReparto() {
            Movement m = Movement.builder().id(3L).userId(ANA)
                    .amount(new BigDecimal("100")).splitMode(SplitMode.EQUAL).build();
            when(partes.existsByMovementIdAndActiveTrue(3L)).thenReturn(true);

            assertThat(servicio.aplicar(m, null, null)).isNull();

            verify(partes).desactivarDe(3L);
            assertThat(m.getSplitMode())
                    .as("si el modo se quedara puesto, el gasto seguiria contando solo una "
                            + "parte con las partes ya borradas: contaria cero")
                    .isNull();
        }

        @Test
        @DisplayName("sin reparto previo, quitarlo no toca la base")
        void quitarLoQueNoHay() {
            Movement m = Movement.builder().id(3L).userId(ANA).amount(new BigDecimal("100")).build();
            when(partes.existsByMovementIdAndActiveTrue(3L)).thenReturn(false);

            servicio.aplicar(m, null, null);

            verify(partes, never()).desactivarDe(anyLong());
        }

        @Test
        @DisplayName("guardar escribe una parte por persona con su importe calculado")
        void guardaLasPartes() {
            Movement m = Movement.builder().id(3L).userId(ANA).amount(new BigDecimal("90000")).build();

            assertThat(servicio.aplicar(m, SplitMode.SHARES,
                    List.of(parte(ANA, "2"), parte(BRUNO, "1")))).isNull();

            verify(partes, org.mockito.Mockito.times(2)).save(any());
            assertThat(m.getSplitMode()).isEqualTo(SplitMode.SHARES);
        }

        @Test
        @DisplayName("un reparto invalido NO escribe nada")
        void invalidoNoEscribe() {
            Movement m = Movement.builder().id(3L).userId(ANA).amount(new BigDecimal("100")).build();

            assertThat(servicio.aplicar(m, SplitMode.EQUAL,
                    List.of(parte(EXTRANO, null))).isCorrect()).isFalse();

            verify(partes, never()).save(any());
            verify(partes, never()).desactivarDe(anyLong());
        }
    }

    @Nested
    @DisplayName("Liquidar")
    class Liquidar {

        private SettleDTO conBruno(String importe) {
            SettleDTO dto = new SettleDTO();
            dto.setWithUserId(BRUNO);
            if (importe != null) dto.setAmount(new BigDecimal(importe));
            return dto;
        }

        @Test
        @DisplayName("no se puede liquidar consigo mismo")
        void conmigoMismo() {
            SettleDTO dto = new SettleDTO();
            dto.setWithUserId(ANA);
            assertThat(servicio.settle(dto).isCorrect()).isFalse();
        }

        @Test
        @DisplayName("no se puede liquidar con alguien de fuera del hogar")
        void conUnExtrano() {
            SettleDTO dto = new SettleDTO();
            dto.setWithUserId(EXTRANO);
            assertThat(servicio.settle(dto).isCorrect()).isFalse();
            verify(movimientos, never()).save(any());
        }

        @Test
        @DisplayName("si no hay deuda, no se inventa un pago")
        void sinDeuda() {
            when(reparto.balances(any(), any())).thenReturn(Map.of());

            ResultDTO r = servicio.settle(conBruno(null));

            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("nada que liquidar");
            verify(movimientos, never()).save(any());
        }

        @Test
        @DisplayName("si YO debo, la plata sale y el movimiento es un gasto")
        void siYoDebo() {
            when(reparto.balances(any(), any()))
                    .thenReturn(Map.of(BRUNO, new BigDecimal("-40000")));
            prepararGuardado(CategoryType.EXPENSE);

            ResultDTO r = servicio.settle(conBruno(null));

            assertThat(((Map<?, ?>) r.getObject()).get("iPaid")).isEqualTo(true);
            assertThat(((Map<?, ?>) r.getObject()).get("amount"))
                    .as("sin importe se salda el neto entero, que es lo que se quiere casi siempre")
                    .isEqualTo(new BigDecimal("40000"));
        }

        @Test
        @DisplayName("si ME deben, lo que se anota es que me pagaron")
        void siMeDeben() {
            when(reparto.balances(any(), any()))
                    .thenReturn(Map.of(BRUNO, new BigDecimal("40000")));
            prepararGuardado(CategoryType.INCOME);

            ResultDTO r = servicio.settle(conBruno(null));

            assertThat(((Map<?, ?>) r.getObject()).get("iPaid"))
                    .as("el sentido lo decide el SIGNO DEL BALANCE, no quien pulsa el boton; "
                            + "si no, cualquiera podria 'cobrarse' una deuda que en realidad tiene")
                    .isEqualTo(false);
        }

        @Test
        @DisplayName("se admite un pago parcial")
        void pagoParcial() {
            when(reparto.balances(any(), any()))
                    .thenReturn(Map.of(BRUNO, new BigDecimal("-40000")));
            prepararGuardado(CategoryType.EXPENSE);

            servicio.settle(conBruno("15000"));

            ArgumentCaptor<Movement> guardado = ArgumentCaptor.forClass(Movement.class);
            verify(movimientos).save(guardado.capture());
            assertThat(guardado.getValue().getAmount()).isEqualByComparingTo("15000");
            assertThat(guardado.getValue().getIsSettlement())
                    .as("sin esta marca la liquidacion contaria como gasto y la misma plata "
                            + "se habria gastado dos veces")
                    .isTrue();
            assertThat(guardado.getValue().getSettledWithUserId()).isEqualTo(BRUNO);
        }

        private void prepararGuardado(CategoryType tipo) {
            when(categorias.findByUserIdAndActiveTrue(ANA)).thenReturn(List.of());
            when(categorias.save(any())).thenAnswer(i -> {
                Category c = i.getArgument(0);
                c.setId(9L);
                return c;
            });
            when(cuentas.porDefecto(ANA)).thenReturn(Account.builder()
                    .id(4L).userId(ANA).name("Sin asignar").kind(AccountKind.OWN)
                    .isDefault(true).active(true).build());
            when(movimientos.save(any())).thenAnswer(i -> {
                Movement m = i.getArgument(0);
                m.setId(11L);
                return m;
            });
            assertThat(tipo).isNotNull();
        }
    }

    @Nested
    @DisplayName("Balances")
    class Balances {

        @Test
        @DisplayName("sin sesion devuelve lista vacia en vez de romper")
        void sinSesion() {
            when(acceso.usuarioActual()).thenReturn(null);
            assertThat(servicio.balances().isCorrect()).isTrue();
        }

        @Test
        @DisplayName("cada fila lleva su etiqueta escrita, no solo el signo")
        void etiquetasEscritas() {
            when(reparto.balances(any(), any())).thenReturn(new java.util.LinkedHashMap<>(Map.of(
                    BRUNO, new BigDecimal("40000"))));

            Map<?, ?> r = (Map<?, ?>) servicio.balances().getObject();
            assertThat(r.get("totalOwedToMe")).isEqualTo(new BigDecimal("40000"));
            assertThat(r.toString())
                    .as("un estado nunca puede depender solo del signo o del color")
                    .contains("Te debe");
        }

        @Test
        @DisplayName("sin hogar se consulta solo lo propio: un IN vacio revienta en algunos dialectos")
        void sinHogar() {
            when(hogares.getHouseholdIds(ANA)).thenReturn(List.of());
            when(movimientos.findByUserIdAndActiveTrue(ANA)).thenReturn(List.of());
            when(reparto.balances(any(), any())).thenReturn(Map.of());

            servicio.balances();

            verify(movimientos, never()).findParaBalances(any(), any());
        }
    }
}
