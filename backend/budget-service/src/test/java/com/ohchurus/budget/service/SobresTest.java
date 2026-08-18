package com.ohchurus.budget.service;

import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.service.impl.Sobres;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LA REGLA ASIMETRICA DE LOS SOBRES
 * ============================================================================
 *
 *   Lo que SOBRA en una categoria se queda en ella para el mes siguiente.
 *   Lo que te PASASTE no se arrastra a la categoria: se descuenta de lo que
 *   tienes para repartir el mes que viene.
 *
 * Casi todas las pruebas de aqui son la misma pregunta con distintos numeros:
 * ¿que se lleva el mes siguiente? Y la respuesta cambia segun el signo, que es
 * justo lo que la hace facil de romper al refactorizar.
 *
 * POR QUE IMPORTA LA ASIMETRIA. Arrastrar el sobregiro a la propia categoria
 * castiga dos veces: te pasaste 100.000 en Mercado y ademas el mes siguiente
 * Mercado empieza con 100.000 menos, asi que para cuadrar tendrias que comer
 * menos de lo normal. Descontarlo del total a repartir dice la verdad —esa
 * plata salio de algun sitio— y te deja decidir DE DONDE sale.
 */
@DisplayName("Sobres: lo que sobra se queda, lo que te pasaste sale del total")
class SobresTest {

    private static final int DIA_DE_CORTE = 1;
    private static final Long MERCADO = 10L;
    private static final Long RESTAURANTES = 20L;

    private static final LocalDate ENERO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FEBRERO = LocalDate.of(2026, 2, 1);
    private static final LocalDate MARZO = LocalDate.of(2026, 3, 1);

    private final List<BudgetAllocation> asignaciones = new ArrayList<>();
    private final List<Movement> movimientos = new ArrayList<>();
    private final Map<Long, Category> categorias = new LinkedHashMap<>();
    private long siguienteId = 1;

    private void categoria(Long id, String nombre, boolean reembolsable) {
        categorias.put(id, Category.builder()
                .id(id).userId(1L).name(nombre).type(CategoryType.EXPENSE)
                .reimbursable(reembolsable).active(true).build());
    }

    private void asignar(Long categoriaId, LocalDate periodo, String cuanto) {
        asignaciones.add(BudgetAllocation.builder()
                .id(siguienteId++).userId(1L).categoryId(categoriaId)
                .periodStart(periodo).allocatedAmount(new BigDecimal(cuanto))
                .active(true).build());
    }

    private void gastar(Long categoriaId, LocalDate cuando, String cuanto) {
        movimientos.add(Movement.builder()
                .id(siguienteId++).userId(1L).categoryId(categoriaId)
                .date(cuando).amount(new BigDecimal(cuanto))
                .confirmed(true).active(true).build());
    }

    private Sobres.Estado estadoDe(LocalDate periodo) {
        return Sobres.calcular(periodo, DIA_DE_CORTE, asignaciones, movimientos, categorias, null);
    }

    private Sobres.Sobre sobre(Sobres.Estado estado, Long categoriaId) {
        return estado.sobres().stream()
                .filter(s -> s.categoriaId().equals(categoriaId))
                .findFirst().orElseThrow(() ->
                        new AssertionError("la categoria " + categoriaId + " no salio en el periodo"));
    }

    // ========================================================================

    @Nested
    @DisplayName("Lo que sobra")
    class LoQueSobra {

        @Test
        @DisplayName("se queda en la categoria para el mes siguiente")
        void elSobranteSeQueda() {
            categoria(MERCADO, "Mercado", false);
            asignar(MERCADO, ENERO, "500000");
            gastar(MERCADO, LocalDate.of(2026, 1, 15), "400000");
            asignar(MERCADO, FEBRERO, "500000");

            Sobres.Sobre febrero = sobre(estadoDe(FEBRERO), MERCADO);

            assertThat(febrero.arrastre())
                    .as("sobraron 100.000 en enero y tienen que aparecer en febrero")
                    .isEqualByComparingTo("100000");
            assertThat(febrero.disponible())
                    .as("500.000 asignados + 100.000 arrastrados")
                    .isEqualByComparingTo("600000");
        }

        @Test
        @DisplayName("se acumula mes tras mes si nadie lo gasta")
        void seAcumula() {
            categoria(MERCADO, "Mercado", false);
            for (LocalDate p : List.of(ENERO, FEBRERO, MARZO)) asignar(MERCADO, p, "500000");
            gastar(MERCADO, LocalDate.of(2026, 1, 15), "400000");
            gastar(MERCADO, LocalDate.of(2026, 2, 15), "450000");

            Sobres.Sobre marzo = sobre(estadoDe(MARZO), MERCADO);

            /*
             * 150.000, no 250.000. Escribi 250.000 la primera vez sumando
             * "100.000 de enero + 150.000 de febrero" y me contradijo la
             * prueba, con razon: el arrastre de febrero YA lleva dentro el de
             * enero.
             *
             *   enero    500.000 - 400.000                     = sobran 100.000
             *   febrero  500.000 + 100.000 arrastrados - 450.000 = sobran 150.000
             *
             * El arrastre es un saldo que viaja, no una lista de sobrantes que
             * se van sumando. Confundir las dos cosas es exactamente como se
             * acaba contando la misma plata dos veces.
             */
            assertThat(marzo.arrastre())
                    .as("el arrastre es un saldo que viaja, no la suma de los sobrantes")
                    .isEqualByComparingTo("150000");
        }

        @Test
        @DisplayName("una categoria sin asignar este mes SIGUE mostrando lo que arrastra")
        void elArrastreSolo() {
            /* Si no apareciera, la app le estaria escondiendo plata al usuario:
               tiene 100.000 disponibles en Mercado y la pantalla diria que no
               tiene nada. */
            categoria(MERCADO, "Mercado", false);
            asignar(MERCADO, ENERO, "500000");
            gastar(MERCADO, LocalDate.of(2026, 1, 15), "400000");

            Sobres.Sobre febrero = sobre(estadoDe(FEBRERO), MERCADO);

            assertThat(febrero.asignado()).isEqualByComparingTo("0");
            assertThat(febrero.disponible()).isEqualByComparingTo("100000");
        }
    }

    @Nested
    @DisplayName("Lo que te pasaste")
    class LoQueTePasaste {

        @Test
        @DisplayName("NO se arrastra a la categoria: no te castiga dos veces")
        void elSobregiroNoBajaALaCategoria() {
            categoria(MERCADO, "Mercado", false);
            asignar(MERCADO, ENERO, "500000");
            gastar(MERCADO, LocalDate.of(2026, 1, 15), "600000");
            asignar(MERCADO, FEBRERO, "500000");

            Sobres.Sobre febrero = sobre(estadoDe(FEBRERO), MERCADO);

            assertThat(febrero.arrastre())
                    .as("si arrastrara -100.000, en febrero habria que comer menos de lo "
                            + "normal para cuadrar: el sistema castigaria dos veces")
                    .isEqualByComparingTo("0");
            assertThat(febrero.disponible()).isEqualByComparingTo("500000");
        }

        @Test
        @DisplayName("sale del total a repartir del mes siguiente")
        void elSobregiroSaleDelTotal() {
            categoria(MERCADO, "Mercado", false);
            asignar(MERCADO, ENERO, "500000");
            gastar(MERCADO, LocalDate.of(2026, 1, 15), "600000");

            Sobres.Estado febrero = estadoDe(FEBRERO);

            assertThat(febrero.deudaArrastrada())
                    .as("esa plata salio de algun sitio y hay que decirlo")
                    .isEqualByComparingTo("100000");
            assertThat(febrero.paraRepartir())
                    .as("empiezas febrero con 100.000 menos para repartir")
                    .isEqualByComparingTo("-100000");
        }

        @Test
        @DisplayName("la deuda no se hereda dos veces: es del mes anterior, no de todos")
        void laDeudaNoSeAcumulaEternamente() {
            /* En marzo ya no debe nada de enero: la deuda de enero se pago
               contra el total a repartir de febrero. Arrastrarla otra vez
               seria cobrarsela dos veces. */
            categoria(MERCADO, "Mercado", false);
            asignar(MERCADO, ENERO, "500000");
            gastar(MERCADO, LocalDate.of(2026, 1, 15), "600000");
            asignar(MERCADO, FEBRERO, "500000");
            gastar(MERCADO, LocalDate.of(2026, 2, 15), "500000");

            assertThat(estadoDe(MARZO).deudaArrastrada()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("La unica excepcion: es dinero que me van a devolver")
    class DineroQueMeVanADevolver {

        @Test
        @DisplayName("una categoria reembolsable no descuenta su sobregiro del total")
        void elReembolsoNoRompeElMes() {
            /* El caso real: pusiste la cuenta del almuerzo del equipo y la
               empresa te lo devuelve. Sin el interruptor, tu presupuesto
               entero aparece roto por una plata que ni siquiera era tuya. */
            categoria(RESTAURANTES, "Restaurantes", true);
            asignar(RESTAURANTES, ENERO, "100000");
            gastar(RESTAURANTES, LocalDate.of(2026, 1, 20), "500000");

            Sobres.Estado febrero = estadoDe(FEBRERO);

            assertThat(febrero.deudaArrastrada())
                    .as("los 400.000 vuelven: no salen de ningun otro sobre")
                    .isEqualByComparingTo("0");
            assertThat(febrero.paraRepartir()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("y la de al lado, que no es reembolsable, si descuenta")
        void soloAfectaALaMarcada() {
            categoria(RESTAURANTES, "Restaurantes", true);
            categoria(MERCADO, "Mercado", false);
            asignar(RESTAURANTES, ENERO, "100000");
            gastar(RESTAURANTES, LocalDate.of(2026, 1, 20), "500000");
            asignar(MERCADO, ENERO, "500000");
            gastar(MERCADO, LocalDate.of(2026, 1, 15), "530000");

            assertThat(estadoDe(FEBRERO).deudaArrastrada())
                    .as("solo los 30.000 de Mercado; los de Restaurantes vuelven")
                    .isEqualByComparingTo("30000");
        }
    }

    @Nested
    @DisplayName("Bordes")
    class Bordes {

        @Test
        @DisplayName("sin nada devuelve un periodo vacio en vez de romper")
        void sinDatos() {
            Sobres.Estado estado = estadoDe(FEBRERO);
            assertThat(estado.sobres()).isEmpty();
            assertThat(estado.paraRepartir()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("un pendiente todavia no gasta")
        void elPendienteNoGasta() {
            categoria(MERCADO, "Mercado", false);
            asignar(MERCADO, ENERO, "500000");
            movimientos.add(Movement.builder()
                    .id(siguienteId++).userId(1L).categoryId(MERCADO)
                    .date(LocalDate.of(2026, 1, 15)).amount(new BigDecimal("400000"))
                    .confirmed(false).active(true).build());

            assertThat(sobre(estadoDe(ENERO), MERCADO).gastado())
                    .as("un gasto que aun no ha ocurrido no puede vaciar el sobre")
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("el sub-gasto es detalle del padre y no vacia el sobre dos veces")
        void elHijoNoCuentaAparte() {
            categoria(MERCADO, "Mercado", false);
            asignar(MERCADO, ENERO, "500000");
            gastar(MERCADO, LocalDate.of(2026, 1, 15), "400000");
            Movement padre = movimientos.get(movimientos.size() - 1);
            movimientos.add(Movement.builder()
                    .id(siguienteId++).userId(1L).categoryId(MERCADO)
                    .date(LocalDate.of(2026, 1, 15)).amount(new BigDecimal("150000"))
                    .parentMovementId(padre.getId())
                    .confirmed(true).active(true).build());

            assertThat(sobre(estadoDe(ENERO), MERCADO).gastado())
                    .as("si el hijo contara aparte, el sobre diria 550.000 gastados de una "
                            + "compra que costo 400.000")
                    .isEqualByComparingTo("400000");
        }

        @Test
        @DisplayName("de un gasto repartido solo entra MI parte")
        void soloMiParte() {
            /* La union con la ola 3.2: el reparto ya resuelto entra por
               parametro. Sin esto, poner la cuenta de una cena entre tres
               vaciaria el sobre con plata que te van a devolver. */
            categoria(RESTAURANTES, "Restaurantes", false);
            asignar(RESTAURANTES, ENERO, "200000");
            gastar(RESTAURANTES, LocalDate.of(2026, 1, 20), "120000");
            Long idDeLaCena = movimientos.get(movimientos.size() - 1).getId();

            Sobres.Estado enero = Sobres.calcular(ENERO, DIA_DE_CORTE, asignaciones, movimientos,
                    categorias, Map.of(idDeLaCena, new BigDecimal("40000")));

            assertThat(sobre(enero, RESTAURANTES).gastado()).isEqualByComparingTo("40000");
            assertThat(sobre(enero, RESTAURANTES).disponible()).isEqualByComparingTo("160000");
        }

        @Test
        @DisplayName("un ingreso no gasta el sobre de nadie")
        void elIngresoNoGasta() {
            categorias.put(99L, Category.builder()
                    .id(99L).userId(1L).name("Sueldo").type(CategoryType.INCOME)
                    .reimbursable(false).active(true).build());
            asignar(MERCADO, ENERO, "500000");
            categoria(MERCADO, "Mercado", false);
            gastar(99L, LocalDate.of(2026, 1, 5), "3000000");

            assertThat(estadoDe(ENERO).totalGastado()).isEqualByComparingTo("0");
        }
    }
}
