package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.util.Computables;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuanta plata hay en una cuenta.
 *
 * LA AFIRMACION FALSABLE
 * ----------------------
 * Esta clase es la razon de ser de la funcionalidad. Antes de las cuentas, la
 * app era una lista de deseos: podias olvidarte de anotar tres gastos y nada
 * te lo decia. Con saldo, la app AFIRMA algo comprobable —"en tu cuenta hay
 * 1.240.000"— y el banco lo confirma o lo desmiente. Ese es el momento en que
 * el usuario empieza a fiarse de lo que ve.
 *
 * Por eso el saldo por defecto cuenta SOLO LO CONFIRMADO. Un pendiente es algo
 * que todavia no ha pasado; meterlo en el saldo lo volveria incomparable con
 * el extracto y la afirmacion dejaria de ser falsable, que es justo lo unico
 * que la hace valer. El proyectado se ofrece aparte, y bien nombrado.
 *
 * POR QUE SE SUMA EN JAVA Y NO EN SQL
 * -----------------------------------
 * Un GROUP BY habria sido mas rapido, y aqui la rapidez no es el problema:
 * hablamos de decenas de movimientos por persona. El problema es que la regla
 * de que movimiento cuenta viviria en dos sitios —Computables y una cadena
 * SQL— y este proyecto ya sabe como acaba eso: el panel tenia dos reglas
 * distintas dentro del MISMO metodo y por eso "Gastos" y "Presupuesto" decian
 * cifras que no cuadraban. Una regla, un sitio. Si algun dia sobran los datos,
 * se optimiza; hasta entonces, se prefiere que no pueda mentir.
 */
@Component
public class SaldoDeCuenta {

    private final MovementRepository movimientos;
    private final CategoryRepository categorias;

    public SaldoDeCuenta(MovementRepository movimientos, CategoryRepository categorias) {
        this.movimientos = movimientos;
        this.categorias = categorias;
    }

    /** Lo que el banco deberia decir hoy: solo movimientos confirmados. */
    public BigDecimal confirmado(Long cuentaId) {
        return calcular(cuentaId, null, true);
    }

    /** Lo confirmado mas lo pendiente: en que quedaria la cuenta si todo ocurre. */
    public BigDecimal proyectado(Long cuentaId) {
        return calcular(cuentaId, null, false);
    }

    /**
     * El saldo confirmado a una fecha concreta, para conciliar contra un
     * extracto que no es el de hoy. Esto es lo que hace posible que la
     * apertura sea un movimiento fechado y no un campo.
     */
    public BigDecimal confirmadoHasta(Long cuentaId, LocalDate hasta) {
        return calcular(cuentaId, hasta, true);
    }

    private BigDecimal calcular(Long cuentaId, LocalDate hasta, boolean soloConfirmados) {
        if (cuentaId == null) return BigDecimal.ZERO;

        List<Movement> deLaCuenta = movimientos.findByAccountIdAndActiveTrue(cuentaId);
        Map<Long, CategoryType> tipos = new HashMap<>();

        BigDecimal saldo = BigDecimal.ZERO;
        for (Movement m : deLaCuenta) {
            if (!Computables.afectaSaldo(m)) continue;
            if (soloConfirmados && !Boolean.TRUE.equals(m.getConfirmed())) continue;
            if (hasta != null && m.getDate() != null && m.getDate().isAfter(hasta)) continue;

            CategoryType tipo = tipos.computeIfAbsent(m.getCategoryId(), id ->
                    categorias.findByIdAndActiveTrue(id).map(Category::getType).orElse(null));

            /* Sin categoria no se sabe el signo, y sumar a ciegas es peor que
               no sumar: un gasto contado como ingreso descuadra el doble de su
               importe. Se omite, y la conciliacion lo hara aflorar. */
            if (tipo == null) continue;

            saldo = tipo == CategoryType.INCOME
                    ? saldo.add(Computables.importe(m))
                    : saldo.subtract(Computables.importe(m));
        }
        return saldo;
    }
}
