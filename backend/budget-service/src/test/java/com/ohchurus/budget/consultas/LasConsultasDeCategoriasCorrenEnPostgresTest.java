package com.ohchurus.budget.consultas;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LAS TRES @Query DE CategoryRepository, CONTRA UN POSTGRESQL DE VERDAD
 * ============================================================================
 *
 * Dos de las tres buscan por nombre con
 * `LOWER(CAST(c.name AS string)) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))`,
 * y ese casteo no esta de adorno: sin el, PostgreSQL no sabe de que tipo es
 * `:name` cuando ademas se le compara con NULL y contesta "could not determine
 * data type of parameter". En H2 la consulta funcionaria igual con o sin
 * casteo, asi que el dia que alguien lo "limpie" por parecer redundante, solo
 * esta prueba lo va a notar.
 *
 * La tercera, `findPersonalAndHousehold`, es la que decide QUE CATEGORIAS VE
 * cada persona: si devolviera de mas, alguien veria las categorias de otro.
 */
@DisplayName("Las consultas de categorias funcionan en PostgreSQL")
class LasConsultasDeCategoriasCorrenEnPostgresTest extends PostgresDeVerdad {

    private static final Long ANA = 1L;
    private static final Long BETO = 2L;

    @Autowired private CategoryRepository categorias;
    @Autowired private com.ohchurus.budget.repository.ScheduledMovementRepository programados;
    @Autowired private HouseholdRepository hogares;
    @Autowired private MovementRepository movimientos;

    private List<Long> losHogaresDeAna;

    @BeforeEach
    void sembrarElArbol() {
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

        Long hogar = hogares.save(Household.builder().name("Casa").active(true).build()).getId();
        Long otroHogar = hogares.save(Household.builder().name("Finca").active(true).build()).getId();
        losHogaresDeAna = List.of(hogar);

        categoria(ANA, null, "Mercado", CategoryType.EXPENSE, true);
        categoria(ANA, null, "Salario", CategoryType.INCOME, true);
        categoria(ANA, hogar, "SERVICIOS del hogar", CategoryType.EXPENSE, true);
        categoria(ANA, hogar, "Mercado viejo", CategoryType.EXPENSE, false);
        categoria(BETO, null, "Gasolina", CategoryType.EXPENSE, true);
        categoria(BETO, otroHogar, "Finca", CategoryType.EXPENSE, true);
    }

    private void categoria(Long duena, Long hogarId, String nombre, CategoryType tipo, boolean viva) {
        categorias.saveAndFlush(Category.builder()
                .userId(duena).householdId(hogarId).name(nombre).type(tipo).active(viva).build());
    }

    // ========================================================================
    // 1. findAllWithFilters

    @Test
    @DisplayName("findAllWithFilters: sin filtros salen todas las vivas, de quien sean")
    void sinFiltrosSalenTodasLasVivas() {
        Page<Category> pagina = categorias.findAllWithFilters(null, null, null, PageRequest.of(0, 50));

        assertThat(pagina.getContent()).extracting(Category::getName)
                .as("tres parametros nulos: el caso que PostgreSQL rechaza si pierde el CAST")
                .containsExactlyInAnyOrder("Mercado", "Salario", "SERVICIOS del hogar",
                        "Gasolina", "Finca");
    }

    @Test
    @DisplayName("findAllWithFilters: el nombre busca por trozo y sin distinguir mayusculas")
    void elNombreBuscaPorTrozoSinDistinguirMayusculas() {
        Page<Category> pagina = categorias.findAllWithFilters(
                ANA, "servicios", null, PageRequest.of(0, 50));

        assertThat(pagina.getContent()).extracting(Category::getName)
                .as("la categoria se llama \"SERVICIOS del hogar\" y se busco \"servicios\": "
                        + "el LOWER de los dos lados depende de la intercalacion del motor")
                .containsExactly("SERVICIOS del hogar");
    }

    @Test
    @DisplayName("findAllWithFilters: el tipo separa ingresos de gastos")
    void elTipoSeparaIngresosDeGastos() {
        Page<Category> pagina = categorias.findAllWithFilters(
                ANA, null, CategoryType.INCOME, PageRequest.of(0, 50));

        /* El tipo es un enum guardado como texto con un CHECK en la base. Que
           Hibernate lo traduzca bien al comparar es cosa del dialecto. */
        assertThat(pagina.getContent()).extracting(Category::getName).containsExactly("Salario");
    }

    @Test
    @DisplayName("findAllWithFilters: la cuenta de la paginacion sale de PostgreSQL")
    void laPaginacionCuentaBien() {
        Page<Category> primera = categorias.findAllWithFilters(
                ANA, null, null, PageRequest.of(0, 2));

        assertThat(primera.getTotalElements())
                .as("Ana tiene tres categorias vivas; la borrada no cuenta")
                .isEqualTo(3);
        assertThat(primera.getContent()).hasSize(2);
    }

    // ========================================================================
    // 2. findPersonalAndHousehold

    @Test
    @DisplayName("findPersonalAndHousehold: las propias mas las del hogar, y ni una de otro")
    void seVenLasPropiasYLasDelHogar() {
        List<Category> visibles = categorias.findPersonalAndHousehold(ANA, losHogaresDeAna);

        assertThat(visibles).extracting(Category::getName)
                .as("\"Finca\" es de un hogar al que Ana no pertenece y \"Gasolina\" es personal "
                        + "de Beto: si aparecen, se acaba de abrir un agujero de aislamiento")
                .containsExactlyInAnyOrder("Mercado", "Salario", "SERVICIOS del hogar");
    }

    // ========================================================================
    // 3. findAllWithFiltersAndHousehold

    @Test
    @DisplayName("findAllWithFiltersAndHousehold: mezcla lo personal y lo compartido y sigue filtrando")
    void elListadoDeHogarFiltraYPagina() {
        Page<Category> pagina = categorias.findAllWithFiltersAndHousehold(
                ANA, losHogaresDeAna, null, CategoryType.EXPENSE, PageRequest.of(0, 50));

        assertThat(pagina.getContent()).extracting(Category::getName)
                .containsExactlyInAnyOrder("Mercado", "SERVICIOS del hogar");
        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findAllWithFiltersAndHousehold: el filtro de nombre alcanza tambien a las compartidas")
    void elNombreAlcanzaALasCompartidas() {
        Page<Category> pagina = categorias.findAllWithFiltersAndHousehold(
                ANA, losHogaresDeAna, "HOGAR", null, PageRequest.of(0, 50));

        assertThat(pagina.getContent()).extracting(Category::getName)
                .containsExactly("SERVICIOS del hogar");
    }
}
