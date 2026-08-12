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
 * `findExpiredActive` es la que cierra sola los presupuestos del periodo
 * anterior, y tiene una rareza que merece una prueba propia: compara un enum
 * de dominio con un LITERAL de texto escrito dentro del JPQL
 * (`a.status = 'ACTIVE'`). La columna es un VARCHAR, asi que funciona, pero es
 * el tipo de comparacion que un motor puede resolver distinto que otro —y si
 * dejara de encontrar filas, el sintoma no seria un error sino presupuestos
 * que se quedan abiertos para siempre y siguen contando en el mes siguiente.
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

        asignacion(ANA, deAna, null, MARZO, FIN_DE_MARZO, "500000", "ACTIVE", true);
        asignacion(BETO, delHogar, hogar, MARZO, FIN_DE_MARZO, "300000", "ACTIVE", true);
        asignacion(BETO, deBeto, null, MARZO, FIN_DE_MARZO, "200000", "ACTIVE", true);
        asignacion(ANA, deAna, null, MARZO.plusMonths(1), FIN_DE_MARZO.plusMonths(1),
                "600000", "ACTIVE", true);
        asignacion(ANA, delHogar, hogar, MARZO, FIN_DE_MARZO, "100000", "ACTIVE", false);
    }

    private Long categoria(Long duena, Long hogarId, String nombre) {
        return categorias.saveAndFlush(Category.builder()
                .userId(duena).householdId(hogarId).name(nombre)
                .type(CategoryType.EXPENSE).active(true).build()).getId();
    }

    private void asignacion(Long duena, Long categoriaId, Long hogarId, LocalDate inicio,
                            LocalDate fin, String importe, String estado, boolean viva) {
        asignaciones.saveAndFlush(BudgetAllocation.builder()
                .userId(duena).categoryId(categoriaId).householdId(hogarId)
                .periodStart(inicio).periodEnd(fin)
                .allocatedAmount(new BigDecimal(importe)).status(estado).active(viva).build());
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
    // 2. findExpiredActive

    @Test
    @DisplayName("findExpiredActive: recoge las de periodos ya cerrados y solo esas")
    void seRecogenLasDePeriodosCerrados() {
        List<BudgetAllocation> caducadas = asignaciones.findExpiredActive(LocalDate.of(2026, 4, 15));

        assertThat(importesDe(caducadas))
                .as("las tres de marzo vivas y en ACTIVE han caducado a mitad de abril; "
                        + "la de abril no, y la borrada tampoco")
                .containsExactlyInAnyOrder("500000", "300000", "200000");
    }

    @Test
    @DisplayName("findExpiredActive: el dia del cierre no cuenta como caducado")
    void elUltimoDiaTodaviaNoCaduca() {
        /* La condicion es `periodEnd < :today`, estricta. El ultimo dia del
           periodo la persona todavia esta presupuestando: cerrarselo ese mismo
           dia seria quitarle el presupuesto mientras lo usa. */
        List<BudgetAllocation> caducadas = asignaciones.findExpiredActive(FIN_DE_MARZO);

        assertThat(caducadas).isEmpty();
    }

    @Test
    @DisplayName("findExpiredActive: una asignacion ya CERRADA no se vuelve a recoger")
    void laYaCerradaNoSeRepesca() {
        /* El literal 'ACTIVE' del JPQL es lo unico que impide que el cierre
           automatico vuelva a pasar por encima de lo que ya cerro. */
        asignacion(ANA, deAna, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                "700000", "CLOSED", true);

        List<BudgetAllocation> caducadas = asignaciones.findExpiredActive(LocalDate.of(2026, 4, 15));

        assertThat(importesDe(caducadas)).doesNotContain("700000");
    }
}
