package com.ohchurus.budget.consultas;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LAS SIETE @Query DE MovementRepository, CONTRA UN POSTGRESQL DE VERDAD
 * ============================================================================
 *
 * Son las consultas de las que salen TODAS las cifras del panel, y hasta ahora
 * ninguna se habia ejecutado nunca contra PostgreSQL: la suite entera corre
 * sobre H2. Tres cosas de estas consultas viajan mal entre motores:
 *
 *   · `(:x IS NULL OR columna = :x)`. Un parametro que solo se compara con
 *     NULL no lleva tipo, y PostgreSQL responde "could not determine data type
 *     of parameter". H2 no protesta.
 *   · `CAST(:fecha AS java.time.LocalDate) IS NULL`. El casteo esta puesto
 *     justo para lo anterior; que siga bastando no lo comprobaba nadie.
 *   · La subconsulta `categoryId IN (SELECT ... FROM Category ...)` con una
 *     lista de hogares como parametro: dos parametros de coleccion en la misma
 *     consulta es donde mas se nota la diferencia de motor.
 *
 * Y hay una cuarta que no es de sintaxis sino de resultado: `Page` obliga a
 * Spring Data a DERIVAR una consulta de conteo que no esta escrita en ningun
 * fichero del proyecto. Aqui se ejecuta de verdad.
 *
 * Todas las comprobaciones miran QUE FILAS VUELVEN. Que la consulta "no
 * explote" no prueba nada: la lista vacia tampoco explota, y la lista vacia es
 * exactamente el bug que hace que el panel salga en cero.
 */
@DisplayName("Las consultas de movimientos funcionan en PostgreSQL")
class LasConsultasDeMovimientosCorrenEnPostgresTest extends PostgresDeVerdad {

    private static final Long ANA = 1L;
    private static final Long BETO = 2L;

    /** Un periodo cerrado, sin depender de la fecha de hoy. */
    private static final LocalDate DESDE = LocalDate.of(2026, 3, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 3, 31);

    @Autowired private MovementRepository movimientos;
    @Autowired private CategoryRepository categorias;
    @Autowired private com.ohchurus.budget.repository.ScheduledMovementRepository programados;
    @Autowired private HouseholdRepository hogares;

    private Long hogar;
    private Long deAna;         // personal de Ana
    private Long delHogar;      // compartida del hogar
    private Long deBeto;        // personal de Beto
    private Long delHogarMuerta;// compartida pero desactivada

    private List<Long> losHogaresDeAna;

    @BeforeEach
    void sembrarElHogar() {
        /*
         * Los programados se limpian aunque esta clase no cree ninguno.
         *
         * Las cuatro clases de consultas COMPARTEN el mismo contenedor de
         * PostgreSQL (ver PostgresDeVerdad), asi que las filas de una
         * sobreviven a la siguiente. Si la de programados corre antes, sus
         * filas siguen vivas y categorias.deleteAll() revienta con
         * FK_PROGRAMADO_CATEGORIA.
         *
         * No es teorico: exactamente eso tumbo el CI en
         * ElEsquemaCuadraConElCodigoTest, que estuvo en verde durante semanas
         * en Windows y se puso rojo en Linux — el orden por defecto de
         * surefire es el del sistema de ficheros y no coincide entre sistemas,
         * asi que basta anadir una clase nueva para cambiarlo.
         *
         * Un escenario tiene que dejar la base como la quiere, no como la
         * encontro.
         */
        movimientos.deleteAll();
        programados.deleteAll();
        categorias.deleteAll();
        hogares.deleteAll();

        hogar = hogares.save(Household.builder().name("Casa").active(true).build()).getId();
        losHogaresDeAna = List.of(hogar);

        deAna = categoria(ANA, null, "Mercado", true);
        delHogar = categoria(ANA, hogar, "Servicios", true);
        deBeto = categoria(BETO, null, "Gasolina", true);
        delHogarMuerta = categoria(ANA, hogar, "Vieja", false);

        // Dentro del periodo
        movimiento(ANA, deAna, "2026-03-05", "1000", true, true, null);
        movimiento(ANA, deAna, "2026-03-10", "2000", false, true, null);
        movimiento(BETO, delHogar, "2026-03-15", "3000", true, true, null);
        movimiento(BETO, delHogar, "2026-03-20", "4000", false, true, null);
        // Ruido que NO debe aparecerle a Ana
        movimiento(BETO, deBeto, "2026-03-25", "5000", true, true, null);
        movimiento(BETO, delHogarMuerta, "2026-03-18", "8000", false, true, null);
        movimiento(ANA, deAna, "2026-03-08", "7000", true, false, null);
        // Fuera del periodo
        movimiento(ANA, deAna, "2026-02-10", "6000", true, true, null);
        movimiento(ANA, deAna, "2026-01-15", "900", false, true, null);

        movimientos.flush();
    }

    private Long categoria(Long duena, Long hogarId, String nombre, boolean viva) {
        return categorias.save(Category.builder()
                .userId(duena).householdId(hogarId).name(nombre)
                .type(CategoryType.EXPENSE).active(viva).build()).getId();
    }

    private Movement movimiento(Long duena, Long categoriaId, String fecha, String importe,
                                boolean confirmado, boolean vivo, Long programadoId) {
        return movimientos.save(Movement.builder()
                .userId(duena).categoryId(categoriaId).date(LocalDate.parse(fecha))
                .amount(new BigDecimal(importe)).isTransfer(false)
                .confirmed(confirmado).active(vivo).scheduledMovementId(programadoId)
                .build());
    }

    private static List<String> importesDe(List<Movement> lista) {
        return lista.stream().map(m -> m.getAmount().stripTrailingZeros().toPlainString()).toList();
    }

    // ========================================================================
    // 1. findAllWithFilters — el listado sin hogar

    @Test
    @DisplayName("findAllWithFilters: con todos los filtros nulos salen todos los movimientos vivos")
    void elListadoSinFiltrosDevuelveLosVivos() {
        Page<Movement> pagina = movimientos.findAllWithFilters(
                null, null, null, null, null, PageRequest.of(0, 50));

        assertThat(importesDe(pagina.getContent()))
                .as("cinco parametros nulos a la vez: si PostgreSQL no puede inferir su tipo, "
                        + "esta consulta ni se ejecuta")
                .containsExactlyInAnyOrder("1000", "2000", "3000", "4000", "5000", "8000", "6000", "900");
    }

    @Test
    @DisplayName("findAllWithFilters: filtrando por persona y periodo salen solo los suyos y de esas fechas")
    void elListadoFiltraPorPersonaYPeriodo() {
        Page<Movement> pagina = movimientos.findAllWithFilters(
                ANA, null, DESDE, HASTA, null, PageRequest.of(0, 50));

        assertThat(importesDe(pagina.getContent()))
                .as("el 7000 esta borrado, el 6000 y el 900 son de otro mes, y el resto no es de Ana")
                .containsExactly("2000", "1000");
    }

    @Test
    @DisplayName("findAllWithFilters: ordena por fecha descendente, que es como lo pinta la app")
    void elListadoVieneDelMasNuevoAlMasViejo() {
        Page<Movement> pagina = movimientos.findAllWithFilters(
                ANA, null, null, null, null, PageRequest.of(0, 50));

        /* El ORDER BY vive dentro del JPQL. Si Spring Data lo pierde al montar
           la paginacion, la lista sale en el orden que le apetezca a PostgreSQL
           —que no es el de insercion— y la pantalla queda desordenada. */
        assertThat(pagina.getContent()).extracting(Movement::getDate)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("findAllWithFilters: el filtro de confirmado distingue pendientes de confirmados")
    void elListadoFiltraPorConfirmado() {
        Page<Movement> pendientes = movimientos.findAllWithFilters(
                ANA, null, null, null, false, PageRequest.of(0, 50));

        assertThat(importesDe(pendientes.getContent())).containsExactly("2000", "900");
    }

    @Test
    @DisplayName("findAllWithFilters: el filtro de categoria acota a una sola")
    void elListadoFiltraPorCategoria() {
        Page<Movement> pagina = movimientos.findAllWithFilters(
                null, deBeto, null, null, null, PageRequest.of(0, 50));

        assertThat(importesDe(pagina.getContent())).containsExactly("5000");
    }

    // ========================================================================
    // 2. findByScheduledMovementId — la idempotencia de las recurrencias

    /*
     * OJO CON LA HISTORIA DE ESTE BLOQUE, que explica por que no comprueba lo
     * que su titulo antiguo decia.
     *
     * Hasta la ola 3 la idempotencia la resolvia una consulta,
     * existeOcurrenciaDelPeriodo, que preguntaba "¿ya existe la del periodo
     * X?". Con las recurrencias reales eso dejo de servir: un programado
     * semanal tiene VARIAS ocurrencias dentro del mismo mes, asi que el
     * periodo ya no distingue una de otra. Ahora se traen todas las
     * ocurrencias del programado y las claves se comparan en memoria.
     *
     * Lo que se prueba aqui sigue siendo lo mismo, porque es lo que importa:
     * que de Postgres vuelvan TODAS las filas que hacen falta para no
     * duplicar. Dos de ellas son faciles de perder al reescribir.
     */

    @Test
    @DisplayName("findByScheduledMovementId: devuelve las ocurrencias con su periodo")
    void lasOcurrenciasVuelvenConSuPeriodo() {
        Movement generado = movimiento(ANA, deAna, "2026-03-03", "1500", false, true, 77L);
        generado.setPeriodStart(DESDE);
        movimientos.saveAndFlush(generado);

        assertThat(movimientos.findByScheduledMovementId(77L))
                .hasSize(1)
                .allMatch(m -> DESDE.equals(m.getPeriodStart()));
        assertThat(movimientos.findByScheduledMovementId(78L)).isEmpty();
    }

    @Test
    @DisplayName("findByScheduledMovementId: el pendiente BORRADO tambien vuelve")
    void laLapidaDelPendienteBorradoTambienVuelve() {
        /* El detalle que mas facil se pierde al reescribir esta consulta: si
           se le anade un "AND active = true", borrar un pendiente deja de
           significar "omite esta ocurrencia" y el arriendo reaparece en el
           siguiente refresco del panel, para siempre. La fila desactivada es
           la lapida que impide que resucite. */
        Movement enterrado = movimiento(ANA, deAna, "2026-03-03", "1500", false, false, 88L);
        enterrado.setPeriodStart(DESDE);
        movimientos.saveAndFlush(enterrado);

        assertThat(movimientos.findByScheduledMovementId(88L))
                .as("sin la lapida, el pendiente borrado vuelve en cada refresco")
                .hasSize(1);
    }

    @Test
    @DisplayName("findByScheduledMovementId: la ocurrencia heredada sin periodo tambien vuelve")
    void laOcurrenciaHeredadaTambienVuelve() {
        /* Las creadas antes de que existiera periodStart lo tienen nulo. Si
           la consulta las dejara fuera, el primer refresco tras el despliegue
           duplicaria todos los pendientes vivos. */
        movimiento(ANA, deAna, "2026-03-12", "1500", false, true, 99L);
        movimientos.flush();

        assertThat(movimientos.findByScheduledMovementId(99L))
                .hasSize(1)
                .allMatch(m -> m.getPeriodStart() == null);
    }

    // 3, 4 y 5. Las tres consultas de hogar sin paginar

    @Test
    @DisplayName("findHouseholdConfirmed: Ana ve lo suyo y lo compartido, y nada mas")
    void losConfirmadosDelHogarSumanLoCompartido() {
        List<Movement> confirmados = movimientos.findHouseholdConfirmed(ANA, losHogaresDeAna, DESDE, HASTA);

        assertThat(importesDe(confirmados))
                .as("de aqui sale el gasto del panel: el 3000 es de Beto pero en una categoria "
                        + "compartida, asi que cuenta; el 5000 es personal de Beto y no")
                .containsExactlyInAnyOrder("1000", "3000");
    }

    @Test
    @DisplayName("findHouseholdConfirmed: una categoria compartida DESACTIVADA deja de sumar")
    void laCategoriaCompartidaMuertaNoSuma() {
        List<Movement> pendientes = movimientos.findHouseholdPending(ANA, losHogaresDeAna, DESDE, HASTA);

        assertThat(importesDe(pendientes))
                .as("el 8000 cuelga de una categoria del hogar que esta borrada: si el "
                        + "active=true de la subconsulta se cae, el panel de Ana suma gastos "
                        + "de una categoria que ya no existe en ninguna pantalla")
                .containsExactlyInAnyOrder("2000", "4000");
    }

    @Test
    @DisplayName("findHouseholdAllPending: los pendientes de CUALQUIER fecha, tambien los atrasados")
    void losPendientesDeSiempreIncluyenLosAtrasados() {
        List<Movement> todos = movimientos.findHouseholdAllPending(ANA, losHogaresDeAna);

        assertThat(importesDe(todos))
                .as("el 900 es de enero: esta consulta no lleva fechas justo para que el "
                        + "panel pueda avisar de lo que quedo sin confirmar meses atras")
                .containsExactlyInAnyOrder("2000", "4000", "900");
    }

    @Test
    @DisplayName("las consultas de hogar aguantan la lista de hogares VACIA")
    void laListaDeHogaresVaciaNoRompeNada() {
        /* Los cinco servicios que llaman a estas consultas lo hacen dentro de un
           `if (!householdIds.isEmpty())`. Esa guarda existe porque una lista
           vacia en un `IN (...)` es sintaxis invalida en SQL, y nadie habia
           comprobado nunca que pasaba de verdad contra PostgreSQL: podia ser una
           excepcion esperando a la primera persona que no perteneciera a ningun
           hogar.
           Resulta que Hibernate la traduce a algo que PostgreSQL acepta y la
           persona sigue viendo lo suyo. Se deja escrito para que la guarda de
           los servicios se pueda quitar algun dia sabiendo que no se rompe
           nada, y para enterarnos si un cambio de version lo cambia. */
        List<Movement> soloLoSuyo = movimientos.findHouseholdConfirmed(ANA, List.of(), DESDE, HASTA);

        assertThat(importesDe(soloLoSuyo))
                .as("sin hogares, Ana tiene que seguir viendo sus propios confirmados")
                .containsExactly("1000");
    }

    // ========================================================================
    // 6. findAllWithFiltersAndHousehold — la unica con countQuery escrita a mano

    @Test
    @DisplayName("findAllWithFiltersAndHousehold: la pagina y su total salen los dos de PostgreSQL")
    void elListadoDeHogarPaginaYCuenta() {
        Page<Movement> primera = movimientos.findAllWithFiltersAndHousehold(
                ANA, losHogaresDeAna, null, null, null, null,
                PageRequest.of(0, 2, Sort.unsorted()));

        /* Esta es la unica consulta del proyecto con countQuery escrita a mano.
           Una countQuery que no case con la de datos da un total mentiroso: la
           lista se corta o el paginador ensena paginas vacias. */
        assertThat(primera.getTotalElements())
                .as("Ana ve 6 movimientos vivos: 1000, 2000, 6000 y 900 suyos, y 3000 y 4000 "
                        + "del hogar. El 8000 no, porque su categoria compartida esta borrada")
                .isEqualTo(6);
        assertThat(primera.getContent()).hasSize(2);
        assertThat(importesDe(primera.getContent())).containsExactly("4000", "3000");
    }

    @Test
    @DisplayName("findAllWithFiltersAndHousehold: los filtros se aplican tambien al total")
    void losFiltrosDelHogarTambienCuentan() {
        Page<Movement> pagina = movimientos.findAllWithFiltersAndHousehold(
                ANA, losHogaresDeAna, null, DESDE, HASTA, false,
                PageRequest.of(0, 10, Sort.unsorted()));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(importesDe(pagina.getContent())).containsExactly("4000", "2000");
    }

    // ========================================================================
    // 7. findHouseholdByPeriod

    @Test
    @DisplayName("findHouseholdByPeriod: todo lo del periodo, confirmado o no, del mas nuevo al mas viejo")
    void elPeriodoDelHogarTraeConfirmadosYPendientes() {
        List<Movement> delPeriodo = movimientos.findHouseholdByPeriod(ANA, losHogaresDeAna, DESDE, HASTA);

        assertThat(importesDe(delPeriodo)).containsExactly("4000", "3000", "2000", "1000");
        assertThat(delPeriodo).extracting(Movement::getDate)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }
}
