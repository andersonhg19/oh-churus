package com.ohchurus.budget.consultas;

import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LAS DOS @Query DE BudgetAllocationRepository, CONTRA POSTGRESQL
 * ============================================================================
 *
 * `findTodasParaElArrastre` es la que sostiene los sobres: trae TODAS las
 * asignaciones de una persona, de cualquier periodo, porque el disponible de
 * este mes depende de lo que sobro en todos los anteriores. Sustituyo a
 * `findExpiredActive`, que buscaba asignaciones "vencidas" para cerrarlas y
 * solo la llamaba un metodo al que no llamaba nadie.
 *
 * `findAllForUserAndHousehold` es la que decide que presupuestos ve cada
 * persona: las suyas mas las del hogar, para un periodo exacto.
 */
@DisplayName("Las consultas de presupuesto funcionan en PostgreSQL")
class LasConsultasDePresupuestoCorrenEnPostgresTest extends PostgresDeVerdad {

    private static final Long ANA = 1L;
    private static final Long BETO = 2L;

    private static final LocalDate MARZO = LocalDate.of(2026, 3, 1);
    private static final LocalDate FIN_DE_MARZO = LocalDate.of(2026, 3, 31);

    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private CategoryRepository categorias;
    @Autowired private HouseholdRepository hogares;
    @Autowired private MovementRepository movimientos;

    private Long hogar;
    private Long deAna;
    private Long delHogar;
    private List<Long> losHogaresDeAna;

    @BeforeEach
    void sembrarElPresupuesto() {
        movimientos.deleteAll();
        asignaciones.deleteAll();
        categorias.deleteAll();
        hogares.deleteAll();

        hogar = hogares.save(Household.builder().name("Casa").active(true).build()).getId();
        losHogaresDeAna = List.of(hogar);

        deAna = categoria(ANA, null, "Mercado");
        delHogar = categoria(ANA, hogar, "Servicios");
        Long deBeto = categoria(BETO, null, "Gasolina");

        asignacion(ANA, deAna, null, MARZO, FIN_DE_MARZO, "500000", true);
        asignacion(BETO, delHogar, hogar, MARZO, FIN_DE_MARZO, "300000", true);
        asignacion(BETO, deBeto, null, MARZO, FIN_DE_MARZO, "200000", true);
        asignacion(ANA, deAna, null, MARZO.plusMonths(1), FIN_DE_MARZO.plusMonths(1),
                "600000", true);
        asignacion(ANA, delHogar, hogar, MARZO, FIN_DE_MARZO, "100000", false);
    }

    private Long categoria(Long duena, Long hogarId, String nombre) {
        return categorias.saveAndFlush(Category.builder()
                .userId(duena).householdId(hogarId).name(nombre)
                .type(CategoryType.EXPENSE).active(true).build()).getId();
    }

    private void asignacion(Long duena, Long categoriaId, Long hogarId, LocalDate inicio,
                            LocalDate fin, String importe, boolean viva) {
        asignaciones.saveAndFlush(BudgetAllocation.builder()
                .userId(duena).categoryId(categoriaId).householdId(hogarId)
                .periodStart(inicio).periodEnd(fin)
                .allocatedAmount(new BigDecimal(importe)).active(viva).build());
    }

    private static List<String> importesDe(List<BudgetAllocation> lista) {
        return lista.stream()
                .map(a -> a.getAllocatedAmount().stripTrailingZeros().toPlainString()).toList();
    }

    // ========================================================================
    // 1. findAllForUserAndHousehold

    @Test
    @DisplayName("findAllForUserAndHousehold: las propias del periodo mas las del hogar")
    void seVenLasPropiasYLasDelHogar() {
        List<BudgetAllocation> visibles =
                asignaciones.findAllForUserAndHousehold(ANA, losHogaresDeAna, MARZO);

        assertThat(importesDe(visibles))
                .as("el 300000 lo puso Beto pero es del hogar, asi que Ana lo ve; el 200000 es "
                        + "personal de Beto; el 600000 es de abril; el 100000 esta borrado")
                .containsExactlyInAnyOrder("500000", "300000");
    }

    @Test
    @DisplayName("findAllForUserAndHousehold: el periodo es exacto, no un rango")
    void elPeriodoEsExacto() {
        List<BudgetAllocation> deAbril =
                asignaciones.findAllForUserAndHousehold(ANA, losHogaresDeAna, MARZO.plusMonths(1));

        assertThat(importesDe(deAbril)).containsExactly("600000");
    }

    // ========================================================================
    // 2. findTodasParaElArrastre

    /*
     * Aqui vivian tres pruebas de findExpiredActive. Se van con la consulta:
     * la usaba autoCloseExpired(), que no tenia endpoint ni @Scheduled, asi
     * que probaban un camino que nadie podia recorrer.
     *
     * Lo que las sustituye comprueba algo que si se ejecuta en cada apertura
     * de la pantalla de presupuesto.
     */

    @Test
    @DisplayName("findTodasParaElArrastre: trae los periodos ANTERIORES, no solo el actual")
    void elArrastreNecesitaElPasado() {
        List<BudgetAllocation> todas = asignaciones.findTodasParaElArrastre(ANA, List.of(hogar));

        assertThat(todas)
                .as("si solo devolviera el periodo actual, el arrastre seria siempre cero y "
                        + "lo que sobro el mes pasado desapareceria sin dejar rastro")
                .extracting(BudgetAllocation::getPeriodStart)
                .contains(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
    }

    @Test
    @DisplayName("findTodasParaElArrastre: vienen ordenadas de la mas vieja a la mas nueva")
    void vienenEnOrden() {
        /* El arrastre se calcula recorriendo periodos hacia adelante. Si la
           base las devolviera desordenadas habria que ordenarlas en memoria, y
           el dia que alguien se olvide el arrastre saldria mal sin avisar. */
        List<LocalDate> periodos = asignaciones.findTodasParaElArrastre(ANA, List.of(hogar))
                .stream().map(BudgetAllocation::getPeriodStart).toList();

        assertThat(periodos).isSorted();
    }

    @Test
    @DisplayName("findTodasParaElArrastre: la desactivada no cuenta")
    void laBorradaNoArrastra() {
        /* Borrar una asignacion tiene que significar que deja de contar; si
           siguiera entrando, el sobre arrastraria plata que el usuario quito
           a proposito. */
        List<BudgetAllocation> todas = asignaciones.findTodasParaElArrastre(ANA, List.of(hogar));

        assertThat(todas).allMatch(BudgetAllocation::getActive);
    }
}
