package com.ohchurus.budget.esquema;

import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * EL ESQUEMA CUADRA CON EL CODIGO (Y NO SE FIA DE EL)
 * ============================================================================
 *
 * Los tres servicios arrancaban con ddl-auto=update y ni una sola clave
 * foranea, unica o check en la base de datos. Eso tenia dos consecuencias que
 * se notaban en la app:
 *
 *   · Un doble toque en Presupuesto insertaba DOS asignaciones para la misma
 *     categoria y periodo. A partir de ese momento el Optional del servicio
 *     encontraba dos filas y lanzaba NonUniqueResultException en esa categoria
 *     PARA SIEMPRE, sin forma de arreglarlo desde la app.
 *   · Con update, anadir un @Column(nullable=false) a una tabla con datos
 *     fallaba como WARN y la aplicacion arrancaba IGUAL, con el esquema
 *     desincronizado y sin que nadie se enterara.
 *
 * Esta prueba es la red de los dos. El solo hecho de que el contexto levante
 * ya prueba lo segundo: el esquema lo construye Flyway y ddl-auto=validate
 * compara tabla a tabla y columna a columna contra el mapeo; si alguien anade
 * un campo a una entidad y se olvida de la migracion, esto no arranca.
 *
 * Lo primero se prueba a mano, escribiendo en la base lo que el codigo daba
 * por imposible y exigiendo que la base lo rechace.
 *
 * QUE NO ES ESTA PRUEBA
 * ---------------------
 * No sustituye a la validacion en Java: la base es el ultimo muro, el que
 * aguanta cuando dos peticiones entran a la vez y las dos ven "aqui no hay
 * nada". Si esta prueba se pone roja, el muro se ha caido.
 */
@SpringBootTest
@DisplayName("El esquema valida contra el mapeo y sostiene las invariantes")
class ElEsquemaCuadraConElCodigoTest {

    private static final Long ANA = 1L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MovementRepository movimientos;
    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private CategoryRepository categorias;
    @Autowired private HouseholdRepository hogares;
    @Autowired private HouseholdMemberRepository miembros;

    private Long hogar;
    private Long categoria;
    private LocalDate periodo;

    @BeforeEach
    void prepararEscenario() {
        movimientos.deleteAll();
        asignaciones.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        hogar = hogares.save(Household.builder().name("Casa").active(true).build()).getId();
        categoria = categorias.save(Category.builder()
                .userId(ANA).name("Mercado").type(CategoryType.EXPENSE).active(true).build()).getId();
        periodo = LocalDate.now().withDayOfMonth(1);
    }

    private BudgetAllocation asignacion(boolean viva) {
        return BudgetAllocation.builder()
                .userId(ANA).categoryId(categoria)
                .periodStart(periodo).periodEnd(periodo.plusMonths(1).minusDays(1))
                .allocatedAmount(new BigDecimal("500000")).active(viva)
                .build();
    }

    private Movement movimiento(Long categoriaId, String importe) {
        return Movement.builder()
                .userId(ANA).categoryId(categoriaId).date(LocalDate.now())
                .amount(new BigDecimal(importe)).isTransfer(false).confirmed(true).active(true)
                .build();
    }

    // ========================================================================

    @Test
    @DisplayName("las migraciones se aplicaron: el esquema lo puso Flyway, no Hibernate")
    void lasMigracionesSeAplicaron() {
        Integer ultima = jdbc.queryForObject(
                // Flyway crea su tabla con el nombre entrecomillado en minuscula; sin
                // comillas H2 la busca en mayuscula y no la encuentra.
                "SELECT MAX(CAST(\"version\" AS INT)) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE",
                Integer.class);
        /* Se exige "de la V2 en adelante", no "exactamente la V2": lo que esta
           prueba defiende es que las invariantes esten puestas, y clavar el
           numero exacto la rompia cada vez que se anadia una migracion nueva
           —que es justo lo que debe poder hacerse sin pelearse con el test. */
        assertThat(ultima)
                .as("no se aplico la V2: sin ella la base vuelve a no tener ni una sola invariante")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("dos asignaciones VIVAS para la misma categoria y periodo: la base dice que no")
    void noCabenDosAsignacionesVivas() {
        asignaciones.saveAndFlush(asignacion(true));

        assertThatThrownBy(() -> asignaciones.saveAndFlush(asignacion(true)))
                .as("el doble toque volvio a colar la segunda fila: a partir de aqui esa "
                        + "categoria lanza NonUniqueResultException para siempre")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("las asignaciones APAGADAS no estorban: borrar y volver a presupuestar funciona")
    void lasApagadasNoEstorban() {
        /* El borrado de esta app es logico. Si la unicidad no fuera parcial,
           borrar una asignacion dejaria la categoria bloqueada para el resto
           del periodo: es el bug que se arreglaria creando otro. */
        asignaciones.saveAndFlush(asignacion(false));
        asignaciones.saveAndFlush(asignacion(false));

        assertThatCode(() -> asignaciones.saveAndFlush(asignacion(true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la misma persona no puede estar dos veces viva en el mismo hogar")
    void noCabenDosMembresiasVivas() {
        miembros.saveAndFlush(HouseholdMember.builder()
                .householdId(hogar).userId(ANA).role("OWNER").active(true).build());

        assertThatThrownBy(() -> miembros.saveAndFlush(HouseholdMember.builder()
                .householdId(hogar).userId(ANA).role("MEMBER").active(true).build()))
                .as("dos invitaciones a la vez metian a la misma persona dos veces")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un movimiento no puede apuntar a una categoria que no existe")
    void noHayMovimientosHuerfanos() {
        assertThatThrownBy(() -> movimientos.saveAndFlush(movimiento(999_999L, "1000")))
                .as("se pudo grabar un gasto en una categoria inexistente: no sale en ninguna "
                        + "pantalla pero resta del saldo")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un importe negativo invierte la suma entera: la base lo rechaza")
    void noHayImportesNegativos() {
        assertThatThrownBy(() -> movimientos.saveAndFlush(movimiento(categoria, "-1000")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un importe de cero sigue valiendo: un programado sin importe genera su pendiente en 0")
    void elCeroSigueValiendo() {
        assertThatCode(() -> movimientos.saveAndFlush(movimiento(categoria, "0")))
                .doesNotThrowAnyException();
    }
}
