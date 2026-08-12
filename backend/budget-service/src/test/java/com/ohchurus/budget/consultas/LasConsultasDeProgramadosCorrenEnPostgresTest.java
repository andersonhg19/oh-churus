package com.ohchurus.budget.consultas;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LAS CUATRO @Query DE ScheduledMovementRepository, CONTRA POSTGRESQL
 * ============================================================================
 *
 * De `findActiveInPeriod` salen los movimientos pendientes que el panel genera
 * cada mes. Su condicion es una comparacion de intervalos con un extremo que
 * puede ser nulo (`s.endDate IS NULL OR s.endDate >= :startDate`): un
 * programado sin fecha de fin es "para siempre". Comparar fechas con nulos de
 * por medio es de lo poco que H2 y PostgreSQL resuelven distinto, y aqui el
 * fallo no seria una excepcion sino un arriendo que deja de generarse.
 *
 * Las otras tres son los listados, con los mismos `:x IS NULL OR ...` de
 * siempre y, dos de ellas, con la subconsulta de categorias del hogar.
 */
@DisplayName("Las consultas de movimientos programados funcionan en PostgreSQL")
class LasConsultasDeProgramadosCorrenEnPostgresTest extends PostgresDeVerdad {

    private static final Long ANA = 1L;
    private static final Long BETO = 2L;

    private static final LocalDate DESDE = LocalDate.of(2026, 3, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 3, 31);

    @Autowired private ScheduledMovementRepository programados;
    @Autowired private CategoryRepository categorias;
    @Autowired private HouseholdRepository hogares;
    @Autowired private MovementRepository movimientos;

    private Long deAna;
    private Long delHogar;
    private Long deBeto;
    private List<Long> losHogaresDeAna;

    @BeforeEach
    void sembrarLosProgramados() {
        movimientos.deleteAll();
        programados.deleteAll();
        categorias.deleteAll();
        hogares.deleteAll();

        Long hogar = hogares.save(Household.builder().name("Casa").active(true).build()).getId();
        losHogaresDeAna = List.of(hogar);

        deAna = categoria(ANA, null, "Arriendo");
        delHogar = categoria(ANA, hogar, "Servicios");
        deBeto = categoria(BETO, null, "Gimnasio");

        programado(ANA, deAna, "Arriendo", Frequency.MONTHLY, "2026-01-01", null, true);
        programado(ANA, deAna, "Curso", Frequency.MONTHLY, "2026-01-01", "2026-02-28", true);
        programado(ANA, deAna, "Seguro", Frequency.ANNUAL, "2026-05-01", null, true);
        programado(ANA, deAna, "Antiguo", Frequency.WEEKLY, "2026-01-01", null, false);
        programado(BETO, delHogar, "Internet", Frequency.MONTHLY, "2026-01-01", null, true);
        programado(BETO, deBeto, "Gimnasio", Frequency.MONTHLY, "2026-01-01", null, true);
    }

    private Long categoria(Long duena, Long hogarId, String nombre) {
        return categorias.saveAndFlush(Category.builder()
                .userId(duena).householdId(hogarId).name(nombre)
                .type(CategoryType.EXPENSE).active(true).build()).getId();
    }

    private void programado(Long duena, Long categoriaId, String nombre, Frequency frecuencia,
                            String inicio, String fin, boolean vivo) {
        programados.saveAndFlush(ScheduledMovement.builder()
                .userId(duena).categoryId(categoriaId).name(nombre)
                .amount(new BigDecimal("100000")).frequency(frecuencia)
                .startDate(LocalDate.parse(inicio))
                .endDate(fin == null ? null : LocalDate.parse(fin))
                .active(vivo).build());
    }

    // ========================================================================
    // 1. findActiveInPeriod

    @Test
    @DisplayName("findActiveInPeriod: el programado SIN fecha de fin sigue vigente")
    void elProgramadoSinFinSigueVigente() {
        List<ScheduledMovement> vigentes = programados.findActiveInPeriod(ANA, DESDE, HASTA);

        assertThat(vigentes).extracting(ScheduledMovement::getName)
                .as("\"Curso\" acabo en febrero, \"Seguro\" empieza en mayo y \"Antiguo\" esta "
                        + "borrado. Si \"Arriendo\" se cae de esta lista, el pendiente del mes "
                        + "deja de generarse y nadie recibe un error: simplemente no aparece")
                .containsExactly("Arriendo");
    }

    @Test
    @DisplayName("findActiveInPeriod: el que termino ANTES del periodo se queda fuera")
    void elTerminadoSeQuedaFuera() {
        List<ScheduledMovement> deFebrero = programados.findActiveInPeriod(
                ANA, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertThat(deFebrero).extracting(ScheduledMovement::getName)
                .as("en febrero el \"Curso\" todavia estaba vivo: el solape se calcula con los "
                        + "dos extremos, no solo con la fecha de inicio")
                .containsExactlyInAnyOrder("Arriendo", "Curso");
    }

    @Test
    @DisplayName("findActiveInPeriod: el que empieza DESPUES del periodo tampoco cuenta")
    void elQueEmpiezaDespuesTampocoCuenta() {
        List<ScheduledMovement> deMayo = programados.findActiveInPeriod(
                ANA, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(deMayo).extracting(ScheduledMovement::getName)
                .containsExactlyInAnyOrder("Arriendo", "Seguro");
    }

    // ========================================================================
    // 2. findAllWithFilters

    @Test
    @DisplayName("findAllWithFilters: sin filtros salen todos los vivos")
    void sinFiltrosSalenTodosLosVivos() {
        Page<ScheduledMovement> pagina = programados.findAllWithFilters(
                null, null, null, PageRequest.of(0, 50));

        assertThat(pagina.getContent()).extracting(ScheduledMovement::getName)
                .containsExactlyInAnyOrder("Arriendo", "Curso", "Seguro", "Internet", "Gimnasio");
    }

    @Test
    @DisplayName("findAllWithFilters: el filtro de frecuencia compara un enum guardado como texto")
    void elFiltroDeFrecuenciaFunciona() {
        Page<ScheduledMovement> pagina = programados.findAllWithFilters(
                ANA, null, Frequency.ANNUAL, PageRequest.of(0, 50));

        assertThat(pagina.getContent()).extracting(ScheduledMovement::getName)
                .containsExactly("Seguro");
    }

    @Test
    @DisplayName("findAllWithFilters: la cuenta de la paginacion sale de PostgreSQL")
    void laPaginacionCuentaBien() {
        Page<ScheduledMovement> primera = programados.findAllWithFilters(
                ANA, deAna, null, PageRequest.of(0, 2));

        assertThat(primera.getTotalElements()).isEqualTo(3);
        assertThat(primera.getContent()).hasSize(2);
    }

    // ========================================================================
    // 3. findAllWithFiltersAndHousehold

    @Test
    @DisplayName("findAllWithFiltersAndHousehold: Ana ve tambien el programado de Beto en la categoria compartida")
    void elListadoDeHogarIncluyeLoCompartido() {
        Page<ScheduledMovement> pagina = programados.findAllWithFiltersAndHousehold(
                ANA, losHogaresDeAna, null, null, PageRequest.of(0, 50));

        assertThat(pagina.getContent()).extracting(ScheduledMovement::getName)
                .as("\"Internet\" lo creo Beto, pero cuelga de una categoria del hogar; "
                        + "\"Gimnasio\" es personal suyo y no debe verse")
                .containsExactlyInAnyOrder("Arriendo", "Curso", "Seguro", "Internet");
        assertThat(pagina.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("findAllWithFiltersAndHousehold: acotar por categoria acota tambien lo compartido")
    void elListadoDeHogarAcotaPorCategoria() {
        Page<ScheduledMovement> pagina = programados.findAllWithFiltersAndHousehold(
                ANA, losHogaresDeAna, delHogar, null, PageRequest.of(0, 50));

        assertThat(pagina.getContent()).extracting(ScheduledMovement::getName)
                .containsExactly("Internet");
    }

    // ========================================================================
    // 4. findHouseholdActive

    @Test
    @DisplayName("findHouseholdActive: los vivos de Ana mas los del hogar, sin paginar")
    void losVivosDelHogarSinPaginar() {
        List<ScheduledMovement> vivos = programados.findHouseholdActive(ANA, losHogaresDeAna);

        assertThat(vivos).extracting(ScheduledMovement::getName)
                .as("\"Antiguo\" esta borrado y \"Gimnasio\" es personal de Beto")
                .containsExactlyInAnyOrder("Arriendo", "Curso", "Seguro", "Internet");
    }
}
