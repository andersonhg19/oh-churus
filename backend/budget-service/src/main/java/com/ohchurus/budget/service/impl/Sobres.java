package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.util.Computables;
import com.ohchurus.budget.util.PeriodUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * EL ARRASTRE DE LOS SOBRES
 * ============================================================================
 *
 * LA REGLA, en una frase, y es ASIMETRICA a proposito:
 *
 *   Lo que SOBRA en una categoria se queda en ella para el mes siguiente.
 *   Lo que te PASASTE no se arrastra a la categoria: se descuenta de lo que
 *   tienes para repartir el mes que viene.
 *
 * POR QUE ASIMETRICA
 * ------------------
 * Arrastrar el sobregiro a la propia categoria castiga dos veces: te pasaste
 * 100.000 en Mercado y ademas el mes siguiente Mercado empieza con 100.000
 * menos, asi que para cuadrar tendrias que comer menos de lo normal.
 * Descontarlo del total a repartir dice la verdad —esa plata salio de algun
 * sitio— y te deja decidir DE DONDE sale, que es justo la decision que un
 * presupuesto tiene que ayudarte a tomar.
 *
 * POR QUE ESTO ES CODIGO PURO
 * ---------------------------
 * Entran asignaciones, movimientos y categorias; salen cifras. No toca base de
 * datos ni reloj. Por eso los casos raros —tres meses seguidos pasandote, un
 * mes sin ninguna asignacion, la categoria reembolsable— se prueban en un
 * parpadeo y sin levantar la aplicacion.
 *
 * POR QUE SE RECALCULA DESDE EL ORIGEN
 * ------------------------------------
 * No hay tabla de arrastre. Guardarlo seria un dato derivado que puede quedar
 * desincronizado de los movimientos que resume, y cuando eso pasa no hay forma
 * de saber cual de los dos miente — el mismo motivo por el que el saldo de una
 * cuenta tampoco se guarda. Ademas, editar un gasto de marzo obligaria a
 * reescribir todos los meses siguientes, y basta con que una de esas
 * escrituras falle para que el presupuesto quede mintiendo para siempre.
 *
 * El coste es recorrer los periodos desde el primero con datos. En una app
 * para dos personas eso son decenas de periodos, no millones.
 */
public final class Sobres {

    private Sobres() {}

    /** El estado de una categoria en un periodo. */
    public record Sobre(Long categoriaId, String nombre, boolean reembolsable,
                        BigDecimal asignado, BigDecimal arrastre,
                        BigDecimal gastado, BigDecimal disponible) {}

    /** El estado completo de un periodo. */
    public record Estado(LocalDate periodo, List<Sobre> sobres,
                         BigDecimal totalAsignado, BigDecimal totalGastado,
                         BigDecimal deudaArrastrada, BigDecimal paraRepartir) {}

    /**
     * Calcula el estado del periodo pedido recorriendo desde el primero con
     * datos.
     *
     * @param gastoPorMovimiento cuanto cuenta cada movimiento como gasto MIO.
     *        Se recibe ya resuelto (lo calcula RepartoDeGastos) en vez de
     *        pedirlo aqui, para que esta clase siga sin depender de nada.
     */
    public static Estado calcular(LocalDate periodoPedido,
                                  int diaDeCorte,
                                  List<BudgetAllocation> asignaciones,
                                  List<Movement> movimientos,
                                  Map<Long, Category> categorias,
                                  Map<Long, BigDecimal> gastoPorMovimiento) {

        Map<LocalDate, Map<Long, BigDecimal>> asignadoPorPeriodo = agruparAsignaciones(asignaciones);
        Map<LocalDate, Map<Long, BigDecimal>> gastadoPorPeriodo =
                agruparGastos(movimientos, categorias, gastoPorMovimiento, diaDeCorte);

        LocalDate primero = primerPeriodoConDatos(asignadoPorPeriodo, gastadoPorPeriodo, periodoPedido);

        /* El sobrante vivo de cada categoria, que viaja de un periodo al
           siguiente. Nunca es negativo: ahi esta la asimetria. */
        Map<Long, BigDecimal> arrastre = new HashMap<>();
        BigDecimal deudaArrastrada = BigDecimal.ZERO;
        Estado estado = null;

        LocalDate periodo = primero;
        while (!periodo.isAfter(periodoPedido)) {
            Map<Long, BigDecimal> asignado = asignadoPorPeriodo.getOrDefault(periodo, Map.of());
            Map<Long, BigDecimal> gastado = gastadoPorPeriodo.getOrDefault(periodo, Map.of());

            estado = unPeriodo(periodo, asignado, gastado, categorias, arrastre, deudaArrastrada);

            /* Lo que se lleva el periodo siguiente se decide AQUI, y es lo
               unico que hace falta recordar entre vueltas. */
            Map<Long, BigDecimal> siguiente = new HashMap<>();
            BigDecimal deudaDelPeriodo = BigDecimal.ZERO;
            for (Sobre s : estado.sobres()) {
                if (s.disponible().signum() >= 0) {
                    siguiente.put(s.categoriaId(), s.disponible());
                } else if (!s.reembolsable()) {
                    /* El sobregiro NO baja a la categoria: sale del total a
                       repartir del mes siguiente. Si la categoria espera un
                       reembolso, no sale de ningun sitio: la plata vuelve. */
                    deudaDelPeriodo = deudaDelPeriodo.add(s.disponible().abs());
                }
            }
            arrastre = siguiente;
            deudaArrastrada = deudaDelPeriodo;
            periodo = PeriodUtils.getEndOfPeriod(diaDeCorte, periodo).plusDays(1);
        }

        return estado != null ? estado : vacio(periodoPedido);
    }

    // ========================================================================

    private static Estado unPeriodo(LocalDate periodo,
                                    Map<Long, BigDecimal> asignado,
                                    Map<Long, BigDecimal> gastado,
                                    Map<Long, Category> categorias,
                                    Map<Long, BigDecimal> arrastre,
                                    BigDecimal deudaArrastrada) {

        /* Una categoria entra en la lista si tiene asignacion, gasto o
           arrastre. Las tres cosas importan: una categoria con 80.000
           arrastrados y sin asignar este mes SIGUE teniendo 80.000
           disponibles, y no ensenarla seria esconderle plata al usuario. */
        Map<Long, Boolean> aparecen = new LinkedHashMap<>();
        asignado.keySet().forEach(id -> aparecen.put(id, true));
        gastado.keySet().forEach(id -> aparecen.put(id, true));
        arrastre.forEach((id, v) -> {
            if (v.signum() != 0) aparecen.put(id, true);
        });

        List<Sobre> sobres = new ArrayList<>();
        BigDecimal totalAsignado = BigDecimal.ZERO;
        BigDecimal totalGastado = BigDecimal.ZERO;

        for (Long categoriaId : aparecen.keySet()) {
            Category categoria = categorias.get(categoriaId);
            BigDecimal a = asignado.getOrDefault(categoriaId, BigDecimal.ZERO);
            BigDecimal g = gastado.getOrDefault(categoriaId, BigDecimal.ZERO);
            BigDecimal r = arrastre.getOrDefault(categoriaId, BigDecimal.ZERO);

            sobres.add(new Sobre(
                    categoriaId,
                    categoria != null ? categoria.getName() : null,
                    categoria != null && Boolean.TRUE.equals(categoria.getReimbursable()),
                    a, r, g, a.add(r).subtract(g)));

            totalAsignado = totalAsignado.add(a);
            totalGastado = totalGastado.add(g);
        }

        /* Lo que queda por repartir arranca en la deuda que traia el mes
           anterior, en negativo. El gasto no se resta aqui porque ya vive
           dentro de cada sobre; restarlo otra vez lo contaria dos veces. */
        BigDecimal paraRepartir = deudaArrastrada.negate();

        return new Estado(periodo, sobres, totalAsignado, totalGastado,
                deudaArrastrada, paraRepartir);
    }

    private static Map<LocalDate, Map<Long, BigDecimal>> agruparAsignaciones(
            List<BudgetAllocation> asignaciones) {
        Map<LocalDate, Map<Long, BigDecimal>> porPeriodo = new HashMap<>();
        if (asignaciones == null) return porPeriodo;
        for (BudgetAllocation a : asignaciones) {
            if (Boolean.FALSE.equals(a.getActive()) || a.getPeriodStart() == null) continue;
            porPeriodo.computeIfAbsent(a.getPeriodStart(), k -> new LinkedHashMap<>())
                    .merge(a.getCategoryId(),
                            a.getAllocatedAmount() == null ? BigDecimal.ZERO : a.getAllocatedAmount(),
                            BigDecimal::add);
        }
        return porPeriodo;
    }

    private static Map<LocalDate, Map<Long, BigDecimal>> agruparGastos(
            List<Movement> movimientos, Map<Long, Category> categorias,
            Map<Long, BigDecimal> gastoPorMovimiento, int diaDeCorte) {

        Map<LocalDate, Map<Long, BigDecimal>> porPeriodo = new HashMap<>();
        if (movimientos == null) return porPeriodo;

        for (Movement m : movimientos) {
            /* Misma regla que en todo el resto de la app: ver Computables. Los
               hijos son detalle del padre, y ni la transferencia ni la
               apertura ni la liquidacion son gasto. */
            if (!Computables.suma(m)) continue;
            if (!Boolean.TRUE.equals(m.getConfirmed())) continue;

            Category categoria = categorias.get(m.getCategoryId());
            if (categoria == null || categoria.getType() != CategoryType.EXPENSE) continue;

            LocalDate periodo = PeriodUtils.getStartOfPeriod(diaDeCorte, m.getDate());
            BigDecimal cuanto = gastoPorMovimiento != null
                    ? gastoPorMovimiento.getOrDefault(m.getId(), Computables.importe(m))
                    : Computables.importe(m);

            porPeriodo.computeIfAbsent(periodo, k -> new LinkedHashMap<>())
                    .merge(m.getCategoryId(), cuanto, BigDecimal::add);
        }
        return porPeriodo;
    }

    /**
     * El primer periodo desde el que hay que empezar a contar.
     *
     * Si no hay nada anterior, se empieza en el periodo pedido: recorrer desde
     * el ano 1 para una cuenta recien creada seria absurdo, y ademas el bucle
     * tardaria lo indecible en llegar.
     */
    private static LocalDate primerPeriodoConDatos(Map<LocalDate, ?> asignaciones,
                                                   Map<LocalDate, ?> gastos,
                                                   LocalDate periodoPedido) {
        LocalDate primero = periodoPedido;
        for (LocalDate p : asignaciones.keySet()) if (p.isBefore(primero)) primero = p;
        for (LocalDate p : gastos.keySet()) if (p.isBefore(primero)) primero = p;
        return primero;
    }

    private static Estado vacio(LocalDate periodo) {
        return new Estado(periodo, List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
