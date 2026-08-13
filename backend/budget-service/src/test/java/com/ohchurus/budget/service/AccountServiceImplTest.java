package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.AccountSaveDTO;
import com.ohchurus.budget.dto.input.ReconcileDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Account;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.AccountKind;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.AccountRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.impl.AccountServiceImpl;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import com.ohchurus.budget.service.impl.SaldoDeCuenta;
import com.ohchurus.budget.util.CategoriasDelSistema;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los caminos por los que una cuenta dice QUE NO.
 *
 * El camino feliz —crear, leer, sumar el saldo— lo cubre de sobra
 * ElSaldoCuadraConElBancoTest, que levanta la app entera y lo ejercita por
 * HTTP. Aqui viven las negativas, que son las que nadie prueba a mano porque
 * hay que provocarlas: la cuenta ajena, la que no se puede borrar, el hogar
 * que no es tuyo. Cada una evita un descuadre distinto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cuentas: los caminos por los que dice que no")
class AccountServiceImplTest {

    private static final Long YO = 1L;
    private static final Long OTRO = 2L;

    @Mock private AccountRepository cuentas;
    @Mock private MovementRepository movimientos;
    @Mock private HouseholdServiceImpl hogares;
    @Mock private SaldoDeCuenta saldos;
    @Mock private CategoriasDelSistema categoriasDelSistema;
    @Mock private ControlAcceso acceso;

    @InjectMocks private AccountServiceImpl servicio;

    @BeforeEach
    void soyYo() {
        lenient().when(acceso.usuarioActual()).thenReturn(YO);
        lenient().when(acceso.esMio(YO)).thenReturn(true);
        lenient().when(acceso.esMio(OTRO)).thenReturn(false);
        lenient().when(acceso.esDeMiHogar(any())).thenReturn(false);
        lenient().when(saldos.confirmado(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(saldos.proyectado(any())).thenReturn(BigDecimal.ZERO);
    }

    private Account cuentaDe(Long dueno, boolean porDefecto) {
        return Account.builder().id(10L).userId(dueno).name("Ahorros")
                .kind(AccountKind.OWN).isDefault(porDefecto).active(true).build();
    }

    // ========================================================================

    @Nested
    @DisplayName("Propiedad")
    class Propiedad {

        @Test
        @DisplayName("leer la cuenta de otro responde 'no existe', no 'no puedes'")
        void leerAjena() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(OTRO, false)));

            ResultDTO r = servicio.getById(10L);

            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage())
                    .as("decir 'no puedes' confirmaria que ese id existe, y con ids "
                            + "consecutivos eso deja contar las cuentas de otra persona")
                    .isEqualTo("Account not found");
        }

        @Test
        @DisplayName("editar la cuenta de otro no la toca")
        void editarAjena() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(OTRO, false)));

            AccountSaveDTO dto = new AccountSaveDTO();
            dto.setId(10L);
            dto.setName("Secuestrada");
            dto.setKind(AccountKind.OWN);

            assertThat(servicio.saveAndUpdate(dto).isCorrect()).isFalse();
            verify(cuentas, never()).save(any());
        }

        @Test
        @DisplayName("una cuenta que no existe se trata igual que una ajena")
        void inexistente() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());
            assertThat(servicio.getById(10L).isCorrect()).isFalse();
            assertThat(servicio.delete(10L).isCorrect()).isFalse();
        }

        @Test
        @DisplayName("no se puede compartir una cuenta con un hogar que no es tuyo")
        void hogarAjeno() {
            AccountSaveDTO dto = new AccountSaveDTO();
            dto.setName("Colada");
            dto.setKind(AccountKind.OWN);
            dto.setHouseholdId(99L);

            ResultDTO r = servicio.saveAndUpdate(dto);

            assertThat(r.isCorrect())
                    .as("le meteria a otra pareja una cuenta dentro de sus cifras")
                    .isFalse();
            verify(cuentas, never()).save(any());
        }

        @Test
        @DisplayName("sin sesion no se crea nada")
        void sinSesion() {
            when(acceso.usuarioActual()).thenReturn(null);
            AccountSaveDTO dto = new AccountSaveDTO();
            dto.setName("X");
            dto.setKind(AccountKind.OWN);

            assertThat(servicio.saveAndUpdate(dto).isCorrect()).isFalse();
            assertThat(servicio.getAll().isCorrect()).isTrue();   // lista vacia, no error
        }
    }

    @Nested
    @DisplayName("Borrar")
    class Borrar {

        @Test
        @DisplayName("no se borra la cuenta por defecto: es donde caen los movimientos sin cuenta")
        void laPorDefectoNoSeBorra() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(YO, true)));

            ResultDTO r = servicio.delete(10L);

            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("por defecto");
            verify(cuentas, never()).save(any());
        }

        @Test
        @DisplayName("no se borra una cuenta con movimientos dentro")
        void conMovimientosDentro() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(YO, false)));
            when(movimientos.existsByAccountIdAndActiveTrue(10L)).thenReturn(true);

            ResultDTO r = servicio.delete(10L);

            assertThat(r.isCorrect())
                    .as("sus movimientos quedarian fuera de todo saldo pero dentro del "
                            + "presupuesto: descuadre invisible")
                    .isFalse();
            assertThat(r.getMessage()).contains("movimientos");
        }

        @Test
        @DisplayName("una cuenta vacia y no por defecto si se borra, y en logico")
        void vaciaSeBorra() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(YO, false)));
            when(movimientos.existsByAccountIdAndActiveTrue(10L)).thenReturn(false);

            assertThat(servicio.delete(10L).isCorrect()).isTrue();

            ArgumentCaptor<Account> guardada = ArgumentCaptor.forClass(Account.class);
            verify(cuentas).save(guardada.capture());
            assertThat(guardada.getValue().getActive())
                    .as("el borrado es logico en toda la app, para poder auditar")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Conciliar")
    class Conciliar {

        private ReconcileDTO peticion(String cuanto, Boolean aplicar) {
            ReconcileDTO dto = new ReconcileDTO();
            dto.setAccountId(10L);
            dto.setRealBalance(new BigDecimal(cuanto));
            dto.setApply(aplicar);
            return dto;
        }

        @Test
        @DisplayName("no se puede conciliar la cuenta de otro")
        void ajena() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(OTRO, false)));
            assertThat(servicio.reconcile(peticion("1", true)).isCorrect()).isFalse();
            verify(movimientos, never()).save(any());
        }

        @Test
        @DisplayName("cuando cuadra no escribe nada, aunque se pida aplicar")
        void cuandoCuadra() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(YO, false)));
            when(saldos.confirmadoHasta(any(), any())).thenReturn(new BigDecimal("500000"));

            ResultDTO r = servicio.reconcile(peticion("500000", true));

            assertThat(r.isCorrect()).isTrue();
            verify(movimientos, never()).save(any());
        }

        @Test
        @DisplayName("sin apply solo informa: preguntar no escribe")
        void soloInforma() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(YO, false)));
            when(saldos.confirmadoHasta(any(), any())).thenReturn(new BigDecimal("500000"));

            servicio.reconcile(peticion("450000", null));

            verify(movimientos, never()).save(any());
        }

        @Test
        @DisplayName("si al banco le SOBRA, el ajuste es un ingreso")
        void ajusteAFavor() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(YO, false)));
            when(saldos.confirmadoHasta(any(), any())).thenReturn(new BigDecimal("450000"));
            when(categoriasDelSistema.ajuste(YO, true)).thenReturn(categoria(CategoryType.INCOME));
            when(movimientos.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.reconcile(peticion("500000", true));

            verify(categoriasDelSistema).ajuste(YO, true);
        }

        @Test
        @DisplayName("si al banco le FALTA, el ajuste es un gasto")
        void ajusteEnContra() {
            when(cuentas.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(cuentaDe(YO, false)));
            when(saldos.confirmadoHasta(any(), any())).thenReturn(new BigDecimal("500000"));
            when(categoriasDelSistema.ajuste(YO, false)).thenReturn(categoria(CategoryType.EXPENSE));
            when(movimientos.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.reconcile(peticion("450000", true));

            ArgumentCaptor<Movement> ajuste = ArgumentCaptor.forClass(Movement.class);
            verify(movimientos).save(ajuste.capture());
            assertThat(ajuste.getValue().getAmount())
                    .as("el importe del ajuste es la diferencia, en positivo: el signo lo "
                            + "pone el tipo de la categoria")
                    .isEqualByComparingTo("50000");
        }

        private Category categoria(CategoryType tipo) {
            return Category.builder().id(7L).userId(YO).name("Ajuste").type(tipo).active(true).build();
        }
    }

    @Nested
    @DisplayName("Listado y catalogo")
    class ListadoYCatalogo {

        @Test
        @DisplayName("sin hogar se usa la consulta simple: un IN vacio revienta en algunos dialectos")
        void sinHogar() {
            when(hogares.getHouseholdIds(YO)).thenReturn(List.of());
            when(cuentas.findByUserIdAndActiveTrueOrderByNameAsc(YO))
                    .thenReturn(List.of(cuentaDe(YO, true)));

            assertThat(servicio.getAll().isCorrect()).isTrue();
            verify(cuentas, never()).findVisibles(any(), any());
        }

        @Test
        @DisplayName("con hogar se usa la consulta que incluye las compartidas")
        void conHogar() {
            when(hogares.getHouseholdIds(YO)).thenReturn(List.of(5L));
            when(cuentas.findVisibles(YO, List.of(5L))).thenReturn(List.of(cuentaDe(YO, false)));

            assertThat(servicio.getAll().isCorrect()).isTrue();
            verify(cuentas, never()).findByUserIdAndActiveTrueOrderByNameAsc(any());
        }

        @Test
        @DisplayName("el catalogo de clases trae las dos, con su etiqueta en castellano")
        void catalogo() {
            assertThat(servicio.kindList().getObject().toString())
                    .contains("OWN").contains("Propia")
                    .contains("LIABILITY").contains("Pasivo");
        }
    }

    @Nested
    @DisplayName("La cuenta por defecto")
    class PorDefecto {

        @Test
        @DisplayName("si no existe se crea: quien estrena la app no vivio la migracion")
        void seCreaSiFalta() {
            when(cuentas.findByUserIdAndIsDefaultTrueAndActiveTrue(YO)).thenReturn(Optional.empty());
            when(cuentas.save(any())).thenAnswer(i -> i.getArgument(0));

            Account creada = servicio.porDefecto(YO);

            assertThat(creada.getIsDefault()).isTrue();
            assertThat(creada.getName()).isEqualTo(AccountServiceImpl.SIN_ASIGNAR);
        }

        @Test
        @DisplayName("si ya existe se reutiliza, no se crea otra")
        void seReutiliza() {
            when(cuentas.findByUserIdAndIsDefaultTrueAndActiveTrue(YO))
                    .thenReturn(Optional.of(cuentaDe(YO, true)));

            assertThat(servicio.porDefecto(YO).getId()).isEqualTo(10L);
            verify(cuentas, never()).save(any());
        }
    }

    @Nested
    @DisplayName("La apertura")
    class Apertura {

        @Test
        @DisplayName("un saldo inicial de cero no genera movimiento")
        void ceroNoGeneraNada() {
            when(cuentas.save(any())).thenAnswer(i -> i.getArgument(0));

            AccountSaveDTO dto = new AccountSaveDTO();
            dto.setName("Nueva");
            dto.setKind(AccountKind.OWN);
            dto.setOpeningBalance(BigDecimal.ZERO);

            servicio.saveAndUpdate(dto);

            verify(movimientos, never()).save(any());
        }

        @Test
        @DisplayName("un saldo inicial negativo usa la categoria de apertura en contra")
        void aperturaEnContra() {
            when(cuentas.save(any())).thenAnswer(i -> i.getArgument(0));
            when(categoriasDelSistema.apertura(YO, false))
                    .thenReturn(Category.builder().id(8L).userId(YO).name("Apertura")
                            .type(CategoryType.EXPENSE).active(true).build());

            AccountSaveDTO dto = new AccountSaveDTO();
            dto.setName("Tarjeta");
            dto.setKind(AccountKind.LIABILITY);
            dto.setOpeningBalance(new BigDecimal("-400000"));
            dto.setOpeningDate(LocalDate.now());

            servicio.saveAndUpdate(dto);

            ArgumentCaptor<Movement> apertura = ArgumentCaptor.forClass(Movement.class);
            verify(movimientos).save(apertura.capture());
            assertThat(apertura.getValue().getIsOpening()).isTrue();
            assertThat(apertura.getValue().getAmount())
                    .as("el importe va en positivo; el signo lo pone el tipo de la categoria")
                    .isEqualByComparingTo("400000");
        }
    }
}
