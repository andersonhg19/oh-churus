package com.ohchurus.budget.util;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.CategoryRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Las categorias que la app necesita para poder anotar cosas mecanicas:
 * la apertura de una cuenta y el ajuste de una conciliacion.
 *
 * POR QUE EXISTEN
 * ---------------
 * Un movimiento necesita categoria (la columna es NOT NULL), y con razon: sin
 * categoria no hay presupuesto. Pero a nadie se le puede pedir que elija una
 * categoria para decir "esta cuenta empezo con 2.000.000" — eso no es un gasto
 * de nada, es un hecho.
 *
 * POR QUE SON NORMALES Y NO UN TIPO ESPECIAL
 * ------------------------------------------
 * Son categorias corrientes: se pueden renombrar, salen en los informes y
 * respetan las mismas reglas de propiedad que las demas. Anadir un concepto de
 * "categoria del sistema" al modelo obligaria a acordarse de excluirlas en
 * cada consulta, y esa es la clase de excepcion que se olvida en la sexta.
 *
 * POR QUE SE CREAN TARDE
 * ----------------------
 * Solo se crea la que hace falta, cuando hace falta. Quien nunca abra una
 * tarjeta de credito no tendra nunca la categoria de saldo en contra
 * ensuciandole el selector.
 *
 * EL SIGNO
 * --------
 * En este modelo el importe siempre es positivo y el signo lo pone el TIPO de
 * la categoria (INCOME suma, EXPENSE resta). Por eso cada concepto necesita su
 * pareja: una apertura de 2.000.000 en la cuenta de ahorros es INCOME, y una
 * apertura de 500.000 de deuda en la tarjeta es EXPENSE. Es la misma idea con
 * el signo al otro lado.
 */
@Component
public class CategoriasDelSistema {

    public static final String APERTURA = "Apertura de cuenta";
    public static final String APERTURA_EN_CONTRA = "Apertura de cuenta (saldo en contra)";
    public static final String AJUSTE_SOBRA = "Ajuste de saldo (sobraba)";
    public static final String AJUSTE_FALTA = "Ajuste de saldo (faltaba)";

    private static final String ICONO = "tune";
    private static final String COLOR = "#78909C";

    private final CategoryRepository categorias;

    public CategoriasDelSistema(CategoryRepository categorias) {
        this.categorias = categorias;
    }

    /** La categoria de apertura que corresponde al signo del saldo inicial. */
    public Category apertura(Long userId, boolean aFavor) {
        return aFavor
                ? obtener(userId, APERTURA, CategoryType.INCOME)
                : obtener(userId, APERTURA_EN_CONTRA, CategoryType.EXPENSE);
    }

    /** La categoria de ajuste que corresponde al sentido de la diferencia. */
    public Category ajuste(Long userId, boolean sobraba) {
        return sobraba
                ? obtener(userId, AJUSTE_SOBRA, CategoryType.INCOME)
                : obtener(userId, AJUSTE_FALTA, CategoryType.EXPENSE);
    }

    /**
     * La busca por nombre y tipo entre las del usuario; si no existe, la crea.
     *
     * Se busca tambien por TIPO y no solo por nombre: si alguien renombra su
     * categoria de gastos a "Ajuste de saldo (sobraba)", no queremos apropiarnos
     * de ella y meterle dentro los ajustes.
     */
    private Category obtener(Long userId, String nombre, CategoryType tipo) {
        List<Category> suyas = categorias.findByUserIdAndActiveTrue(userId);
        return suyas.stream()
                .filter(c -> nombre.equals(c.getName()) && tipo == c.getType())
                .findFirst()
                .orElseGet(() -> categorias.save(Category.builder()
                        .userId(userId)
                        .name(nombre)
                        .type(tipo)
                        .icon(ICONO)
                        .color(COLOR)
                        .active(true)
                        .build()));
    }
}
