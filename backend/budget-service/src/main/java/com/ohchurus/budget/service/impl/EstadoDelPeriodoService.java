package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Account;
import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.AccountKind;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.ScheduledKind;
import com.ohchurus.budget.repository.AccountRepository;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.impl.PlanFinancieroService.Fecha;
import com.ohchurus.budget.service.impl.PlanFinancieroService.Linea;
import com.ohchurus.budget.util.Computables;
import com.ohchurus.budget.util.PeriodUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * EL ESTADO DEL PERIODO: todas las cifras de un presupuesto conjunto, con UNA
 * definicion cada una, calculadas en un solo sitio.
 *
 * Antes cada pantalla sumaba a su manera: el Resumen con "mi parte", el
 * Consolidado con todo, el Presupuesto solo lo que tenia asignacion. La misma
 * pregunta —cuanto se gasto en Casa— tenia tres respuestas. Aqui se responde
 * una vez y las pantallas solo pintan.
 *
 * ALCANCE. Se pregunta por el HOGAR (lo comun, lo que ven los dos) o por una
 * PERSONA del hogar (lo que esa persona pago o le toca pagar). De otra persona
 * solo se ve lo comun: su categoria privada no entra en ninguna cifra ajena.
 *
 * LAS DEFINICIONES (las mismas que en la pantalla y en las pruebas):
 *
 *   asignado      la asignacion de la categoria si la tiene (HOGAR: las
 *                 comunes; PERSONA: su personal). Si no, lo planeado en sus
 *                 elementos: facturas, cuotas, ahorro y bolsas del periodo.
 *                 Para una PERSONA en una categoria comun es lo que ella tiene
 *                 a su cargo ahi.
 *   real          gastos CONFIRMADOS segun Computables (sin aperturas, sin
 *                 liquidaciones, sin sub-gastos, sin transferencias).
 *   comprometido  gastos PENDIENTES del periodo (facturas aun no pagadas).
 *   disponible    asignado - real - comprometido.
 *   AHORRO        una categoria cuyos programados son ahorro. Su "real" es lo
 *                 que se ahorro de verdad, y quedarse corto es INCUMPLIR la
 *                 meta, no "sobrar": "se esperaba ahorrar 3.000.000 y no se
 *                 ahorro nada" es exactamente lo que esta cifra dice.
 *   BOLSA         plata apartada sin factura (provision: mercado, mascotas).
 *                 Lo que se compre en la categoria se descuenta de ella; a fin
 *                 de mes lo esperado es haber gastado eso y no mas.
 *   libre         recibido - gastado - ahorrado - comprometido - lo que aun
 *                 queda apartado en bolsas. Es lo que se puede gastar sin
 *                 romper el plan.
 */
@Slf4j
@Service
public class EstadoDelPeriodoService {

    private final PlanFinancieroService plan;
    private final MovementRepository movimientos;
    private final CategoryRepository categorias;
    private final BudgetAllocationRepository asignaciones;
    private final AccountRepository cuentas;
    private final SaldoDeCuenta saldos;
    private final HouseholdServiceImpl hogares;

    public EstadoDelPeriodoService(PlanFinancieroService plan, MovementRepository movimientos,
                                   CategoryRepository categorias, BudgetAllocationRepository asignaciones,
                                   AccountRepository cuentas, SaldoDeCuenta saldos,
                                   HouseholdServiceImpl hogares) {
        this.plan = plan;
        this.movimientos = movimientos;
        this.categorias = categorias;
        this.asignaciones = asignaciones;
        this.cuentas = cuentas;
        this.saldos = saldos;
        this.hogares = hogares;
    }

    /** HOGAR, o una persona del hogar. */
    public record Alcance(boolean hogar, Long persona) {
        static Alcance de(String tipo, Long persona, Long yo) {
            if (tipo == null || "HOGAR".equalsIgnoreCase(tipo)) return new Alcance(true, null);
            return new Alcance(false, persona != null ? persona : yo);
        }
        String nombre() { return hogar ? "HOGAR" : "PERSONA"; }
    }

    // ========================================================================

    @Transactional(readOnly = true)
    public ResultDTO estado(Long yo, Integer diaDeCorte, LocalDate referencia, String tipo, Long persona) {
        try {
            Alcance alcance = Alcance.de(tipo, persona, yo);
            if (!alcance.hogar() && !hogares.companerosDeHogar(yo).contains(alcance.persona())) {
                return new ResultDTO(false, "Esa persona no es de tu hogar", 400);
            }
            int corte = diaDeCorte != null && diaDeCorte >= 1 && diaDeCorte <= 28 ? diaDeCorte : 1;
            LocalDate ini = PeriodUtils.getStartOfPeriod(corte, referencia != null ? referencia : LocalDate.now());
            return new ResultDTO(calcular(yo, corte, ini, alcance, plan.visibles(yo), true));
        } catch (Exception e) {
            log.error("Error computing period state: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error calculando el estado del periodo", 500);
        }
    }

    /**
     * Presupuestado contra real de varios periodos seguidos, terminando en el
     * de referencia: "para mascotas esperabamos 500.000 y gastamos 700.000",
     * mes a mes, y cuanto se ahorro de lo que se queria ahorrar.
     */
    @Transactional(readOnly = true)
    public ResultDTO historial(Long yo, Integer diaDeCorte, LocalDate referencia, int periodos,
                               String tipo, Long persona) {
        try {
            Alcance alcance = Alcance.de(tipo, persona, yo);
            if (!alcance.hogar() && !hogares.companerosDeHogar(yo).contains(alcance.persona())) {
                return new ResultDTO(false, "Esa persona no es de tu hogar", 400);
            }
            int corte = diaDeCorte != null && diaDeCorte >= 1 && diaDeCorte <= 28 ? diaDeCorte : 1;
            int n = Math.max(1, Math.min(periodos, 12));
            LocalDate ini = PeriodUtils.getStartOfPeriod(corte, referencia != null ? referencia : LocalDate.now());
            List<Linea> lineas = plan.visibles(yo);
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = n - 1; i >= 0; i--) {
                LocalDate desde = PeriodUtils.getStartOfPeriod(corte, ini.minusMonths(i));
                Map<String, Object> e = calcular(yo, corte, desde, alcance, lineas, false);
                Map<String, Object> resumen = new LinkedHashMap<>();
                for (String k : List.of("periodStart", "periodEnd", "closed", "totals")) resumen.put(k, e.get(k));
                List<Map<String, Object>> cats = new ArrayList<>();
                for (Object o : (List<?>) e.get("categories")) {
                    Map<?, ?> c = (Map<?, ?>) o;
                    Map<String, Object> r = new LinkedHashMap<>();
                    for (String k : List.of("categoryId", "categoryName", "categoryIcon", "categoryColor", "kind",
                            "allocated", "actual", "difference", "status")) r.put(k, c.get(k));
                    cats.add(r);
                }
                resumen.put("categories", cats);
                out.add(resumen);
            }
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("scope", alcance.nombre());
            res.put("personUserId", alcance.persona());
            res.put("periods", out);
            return new ResultDTO(res);
        } catch (Exception e) {
            log.error("Error computing history: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error calculando el historial", 500);
        }
    }

    // ========================================================================

    /** Lo que se sabe de una categoria en el periodo, mientras se suma. */
    private static final class Acum {
        Category cat;
        BigDecimal asignado = BigDecimal.ZERO;
        BigDecimal real = BigDecimal.ZERO;
        BigDecimal comprometido = BigDecimal.ZERO;
        BigDecimal realDeElementos = BigDecimal.ZERO;
        BigDecimal bolsa = BigDecimal.ZERO;
        BigDecimal planDeLineas = BigDecimal.ZERO;
        boolean tieneAsignacion;
        boolean ahorro;
        boolean pagoOFactura;
        Map<Long, BigDecimal[]> porPersona = new LinkedHashMap<>();
        List<Map<String, Object>> elementos = new ArrayList<>();
    }

    Map<String, Object> calcular(Long yo, int corte, LocalDate ini, Alcance alcance,
                                 List<Linea> lineas, boolean detalle) {
        LocalDate fin = PeriodUtils.getEndOfPeriod(corte, ini);
        LocalDate hoy = LocalDate.now();
        List<Long> hIds = hogares.getHouseholdIds(yo);

        Map<Long, Category> cache = new HashMap<>();
        Map<Long, Acum> porCat = new LinkedHashMap<>();

        // --- 1. asignaciones (HOGAR: las comunes; PERSONA: su personal) ------
        List<BudgetAllocation> asig = !hIds.isEmpty()
                ? asignaciones.findAllForUserAndHousehold(yo, hIds, ini)
                : asignaciones.findByUserIdAndPeriodStartAndActiveTrue(yo, ini);
        for (BudgetAllocation a : asig) {
            boolean comun = a.getHouseholdId() != null;
            boolean entra = alcance.hogar() ? comun : (!comun && alcance.persona().equals(a.getUserId()));
            if (!entra) continue;
            Acum c = acum(porCat, cache, a.getCategoryId());
            if (c == null) continue;
            c.asignado = c.asignado.add(nz(a.getAllocatedAmount()));
            c.tieneAsignacion = true;
        }

        // --- 2. programados: elementos, ingresos esperados, responsabilidades
        BigDecimal ingresoEsperado = BigDecimal.ZERO;
        BigDecimal deducciones = BigDecimal.ZERO;
        Map<Long, BigDecimal[]> ingresosPorPersona = new LinkedHashMap<>(); // [esperado, recibido]
        Map<Long, BigDecimal[]> responsables = new LinkedHashMap<>();       // [facturas, bolsas, ahorro, pagado]
        int facturasTotal = 0, facturasPagadas = 0, facturasVencidas = 0;
        Set<Long> movimientosDeIngresoProgramados = new HashSet<>();
        List<Map<String, Object>> ingresosDetalle = new ArrayList<>();

        for (Linea l : lineas) {
            boolean comun = l.compartida();
            List<Fecha> fs = plan.fechas(l, ini, fin, hoy);
            if (fs.isEmpty()) continue;

            // responsabilidades: SIEMPRE de los dos, sobre lo que yo veo
            if (!l.ingreso() && l.clase() != ScheduledKind.DEDUCTION) {
                BigDecimal[] r = responsables.computeIfAbsent(l.pagador(), k -> ceros(4));
                for (Fecha f : fs) {
                    if (l.clase() == ScheduledKind.PROVISION) r[1] = r[1].add(f.monto());
                    else if (l.clase() == ScheduledKind.SAVING) r[2] = r[2].add(f.monto());
                    else r[0] = r[0].add(f.monto());
                    if ("PAGADO".equals(f.estado())) r[3] = r[3].add(f.monto());
                }
            }

            boolean entra = alcance.hogar() ? comun : alcance.persona().equals(l.pagador());
            if (!entra) continue;

            if (l.ingreso()) {
                for (Fecha f : fs) {
                    ingresoEsperado = ingresoEsperado.add(f.monto());
                    ingresosPorPersona.computeIfAbsent(l.pagador(), k -> ceros(2))[0] =
                            ingresosPorPersona.get(l.pagador())[0].add(f.monto());
                    if (f.movimientoId() != null) movimientosDeIngresoProgramados.add(f.movimientoId());
                    if (detalle) ingresosDetalle.add(elemento(f));
                }
                continue;
            }
            if (l.clase() == ScheduledKind.DEDUCTION) {
                for (Fecha f : fs) deducciones = deducciones.add(f.monto());
            }

            Acum c = acum(porCat, cache, l.cat().getId());
            if (c == null) continue;
            if (l.clase() == ScheduledKind.SAVING) c.ahorro = true;
            else if (l.clase() != ScheduledKind.PROVISION) c.pagoOFactura = true;
            for (Fecha f : fs) {
                c.planDeLineas = c.planDeLineas.add(f.monto());
                if (l.clase() == ScheduledKind.PROVISION) {
                    c.bolsa = c.bolsa.add(f.monto());
                } else {
                    facturasTotal++;
                    if ("PAGADO".equals(f.estado())) {
                        facturasPagadas++;
                        c.realDeElementos = c.realDeElementos.add(f.monto());
                    } else if ("VENCIDO".equals(f.estado())) {
                        facturasVencidas++;
                    }
                }
                if (detalle) c.elementos.add(elemento(f));
            }
        }

        // --- 3. movimientos reales del periodo -------------------------------
        List<Movement> confirmados = !hIds.isEmpty()
                ? movimientos.findHouseholdConfirmed(yo, hIds, ini, fin)
                : movimientos.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(yo, ini, fin);
        List<Movement> pendientes = !hIds.isEmpty()
                ? movimientos.findHouseholdPending(yo, hIds, ini, fin)
                : movimientos.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(yo, ini, fin);

        BigDecimal ingresoRecibido = BigDecimal.ZERO;
        BigDecimal ingresoExtra = BigDecimal.ZERO;
        int pendientesCount = 0;
        for (Movement m : confirmados) {
            if (!Computables.suma(m)) continue;
            Category cat = cache.computeIfAbsent(m.getCategoryId(), id -> categorias.findByIdAndActiveTrue(id).orElse(null));
            if (cat == null || !entra(alcance, cat, m)) continue;
            BigDecimal v = Computables.importe(m);
            if (cat.getType() == CategoryType.INCOME) {
                ingresoRecibido = ingresoRecibido.add(v);
                BigDecimal[] p = ingresosPorPersona.computeIfAbsent(m.getUserId(), k -> ceros(2));
                p[1] = p[1].add(v);
                /* Un ingreso que no venia de ningun programado (una venta, un
                   bono) tambien es esperado: ya llego. */
                if (!movimientosDeIngresoProgramados.contains(m.getId())) {
                    ingresoExtra = ingresoExtra.add(v);
                    p[0] = p[0].add(v);
                }
                continue;
            }
            Acum c = acum(porCat, cache, cat.getId());
            c.real = c.real.add(v);
            c.porPersona.computeIfAbsent(m.getUserId(), k -> ceros(2))[0] =
                    c.porPersona.get(m.getUserId())[0].add(v);
        }
        for (Movement m : pendientes) {
            if (!Computables.suma(m)) continue;
            Category cat = cache.computeIfAbsent(m.getCategoryId(), id -> categorias.findByIdAndActiveTrue(id).orElse(null));
            if (cat == null || !entra(alcance, cat, m)) continue;
            pendientesCount++;
            if (cat.getType() == CategoryType.INCOME) continue;
            Acum c = acum(porCat, cache, cat.getId());
            BigDecimal v = Computables.importe(m);
            c.comprometido = c.comprometido.add(v);
            c.porPersona.computeIfAbsent(m.getUserId(), k -> ceros(2))[1] =
                    c.porPersona.get(m.getUserId())[1].add(v);
        }
        ingresoEsperado = ingresoEsperado.add(ingresoExtra);

        // --- 4. por categoria: estado, diferencia, quien --------------------
        boolean cerrado = fin.isBefore(hoy);
        BigDecimal totAsignado = BigDecimal.ZERO, totReal = BigDecimal.ZERO, totComprometido = BigDecimal.ZERO;
        BigDecimal totAhorroMeta = BigDecimal.ZERO, totAhorrado = BigDecimal.ZERO, totSinPresupuesto = BigDecimal.ZERO;
        BigDecimal totBolsaRestante = BigDecimal.ZERO;
        int dentro = 0, excedidas = 0, metasCumplidas = 0, metasIncumplidas = 0;
        List<Map<String, Object>> cats = new ArrayList<>();
        for (Acum c : porCat.values()) {
            if (c.cat.getType() == CategoryType.INCOME) continue;
            /* Sin asignacion explicita, lo presupuestado es lo planeado en sus
               elementos (facturas, cuotas, ahorro, bolsas). Es tambien lo que
               le toca a una persona en una categoria comun: la asignacion de
               lo comun es del hogar, no de nadie en particular. */
            if (!c.tieneAsignacion) c.asignado = c.planDeLineas;
            if (c.asignado.signum() == 0 && c.real.signum() == 0 && c.comprometido.signum() == 0 && c.elementos.isEmpty()) continue;
            boolean esAhorro = c.ahorro && !c.pagoOFactura;
            BigDecimal disponible = c.asignado.subtract(c.real).subtract(c.comprometido);
            BigDecimal diferencia = c.asignado.subtract(c.real);
            String estado;
            if (esAhorro) {
                if (c.real.compareTo(c.asignado) >= 0 && c.asignado.signum() > 0) { estado = "META_CUMPLIDA"; metasCumplidas++; }
                else if (cerrado) { estado = "META_INCUMPLIDA"; metasIncumplidas++; }
                else estado = "EN_CURSO";
                totAhorroMeta = totAhorroMeta.add(c.asignado);
                totAhorrado = totAhorrado.add(c.real);
            } else {
                if (c.asignado.signum() == 0 && c.real.signum() > 0) { estado = "SIN_PRESUPUESTO"; excedidas++; totSinPresupuesto = totSinPresupuesto.add(c.real); }
                else if (c.real.compareTo(c.asignado) > 0) { estado = "EXCEDIDO"; excedidas++; }
                else if (c.real.add(c.comprometido).compareTo(c.asignado) > 0) estado = "EN_RIESGO";
                else { estado = "DENTRO"; dentro++; }
            }
            /* Lo que se gasto en la categoria por fuera de sus facturas
               consume primero la bolsa, si la tiene. */
            BigDecimal otros = c.real.subtract(c.realDeElementos).max(BigDecimal.ZERO);
            BigDecimal bolsaRestante = c.bolsa.subtract(otros).max(BigDecimal.ZERO);
            totBolsaRestante = totBolsaRestante.add(bolsaRestante);

            totAsignado = totAsignado.add(c.asignado);
            if (!esAhorro) totReal = totReal.add(c.real);
            totComprometido = totComprometido.add(c.comprometido);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("categoryId", c.cat.getId());
            r.put("categoryName", c.cat.getName());
            r.put("categoryIcon", c.cat.getIcon());
            r.put("categoryColor", c.cat.getColor());
            r.put("shared", c.cat.getHouseholdId() != null);
            r.put("kind", esAhorro ? "SAVING" : "EXPENSE");
            r.put("allocated", c.asignado);
            r.put("actual", c.real);
            r.put("committed", c.comprometido);
            r.put("available", disponible);
            r.put("difference", diferencia);
            r.put("executionPct", pct(c.real, c.asignado));
            r.put("status", estado);
            r.put("hasAllocation", c.tieneAsignacion);
            r.put("plannedInItems", c.planDeLineas);
            r.put("provision", c.bolsa);
            r.put("provisionLeft", bolsaRestante);
            r.put("otherSpending", otros);
            r.put("byPerson", personas(c.porPersona));
            if (detalle) r.put("items", c.elementos);
            cats.add(r);
        }
        cats.sort(Comparator.comparing((Map<String, Object> m) -> ((BigDecimal) m.get("allocated")).max((BigDecimal) m.get("actual"))).reversed());

        // --- 5. totales ------------------------------------------------------
        BigDecimal libre = ingresoRecibido.subtract(totReal).subtract(totAhorrado)
                .subtract(totComprometido).subtract(totBolsaRestante);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("incomeExpected", ingresoEsperado);
        t.put("incomeReceived", ingresoRecibido);
        t.put("incomePending", ingresoEsperado.subtract(ingresoRecibido).max(BigDecimal.ZERO));
        t.put("payrollDeductions", deducciones);
        t.put("budgeted", totAsignado);
        t.put("unassigned", ingresoEsperado.subtract(totAsignado));
        t.put("spent", totReal);
        t.put("committed", totComprometido);
        t.put("savingsGoal", totAhorroMeta);
        t.put("saved", totAhorrado);
        t.put("savingsRatePct", pct(totAhorrado, ingresoRecibido));
        t.put("unbudgetedSpending", totSinPresupuesto);
        t.put("provisionsLeft", totBolsaRestante);
        t.put("freeToSpend", libre);
        t.put("executionPct", pct(totReal, totAsignado.subtract(totAhorroMeta)));

        Map<String, Object> cumplimiento = new LinkedHashMap<>();
        cumplimiento.put("billsTotal", facturasTotal);
        cumplimiento.put("billsPaid", facturasPagadas);
        cumplimiento.put("billsOverdue", facturasVencidas);
        cumplimiento.put("categoriesWithin", dentro);
        cumplimiento.put("categoriesOver", excedidas);
        cumplimiento.put("savingsGoalsMet", metasCumplidas);
        cumplimiento.put("savingsGoalsMissed", metasIncumplidas);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("scope", alcance.nombre());
        res.put("personUserId", alcance.persona());
        res.put("personName", alcance.persona() != null ? hogares.apodoDe(alcance.persona()) : null);
        res.put("periodStart", ini.toString());
        res.put("periodEnd", fin.toString());
        res.put("closed", cerrado);
        res.put("totals", t);
        res.put("compliance", cumplimiento);
        res.put("categories", cats);
        if (detalle) {
            res.put("pendingCount", pendientesCount);
            res.put("incomeByPerson", ingresos(ingresosPorPersona));
            res.put("incomeItems", ingresosDetalle);
            res.put("responsibilities", responsabilidades(responsables));
            res.put("funds", fondos(yo, alcance));
        }
        return res;
    }

    // ========================================================================

    private boolean entra(Alcance alcance, Category cat, Movement m) {
        boolean comun = cat.getHouseholdId() != null;
        return alcance.hogar() ? comun : alcance.persona().equals(m.getUserId());
    }

    private Acum acum(Map<Long, Acum> porCat, Map<Long, Category> cache, Long categoryId) {
        Category cat = cache.computeIfAbsent(categoryId, id -> categorias.findByIdAndActiveTrue(id).orElse(null));
        if (cat == null) return null;
        return porCat.computeIfAbsent(categoryId, id -> {
            Acum a = new Acum();
            a.cat = cat;
            return a;
        });
    }

    private Map<String, Object> elemento(Fecha f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scheduledMovementId", f.l().p().getId());
        m.put("movementId", f.movimientoId());
        m.put("name", f.l().p().getName());
        m.put("date", f.fecha().toString());
        m.put("amount", f.monto());
        m.put("status", f.estado());
        m.put("kind", f.l().clase().name());
        m.put("payerUserId", f.l().pagador());
        m.put("payerName", hogares.apodoDe(f.l().pagador()));
        return m;
    }

    private List<Map<String, Object>> personas(Map<Long, BigDecimal[]> mapa) {
        List<Map<String, Object>> out = new ArrayList<>();
        mapa.forEach((u, v) -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("userId", u);
            p.put("name", hogares.apodoDe(u));
            p.put("actual", v[0]);
            p.put("committed", v[1]);
            out.add(p);
        });
        return out;
    }

    private List<Map<String, Object>> ingresos(Map<Long, BigDecimal[]> mapa) {
        List<Map<String, Object>> out = new ArrayList<>();
        mapa.forEach((u, v) -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("userId", u);
            p.put("name", hogares.apodoDe(u));
            p.put("expected", v[0]);
            p.put("received", v[1]);
            out.add(p);
        });
        return out;
    }

    private List<Map<String, Object>> responsabilidades(Map<Long, BigDecimal[]> mapa) {
        BigDecimal total = mapa.values().stream().map(v -> v[0].add(v[1]).add(v[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> out = new ArrayList<>();
        mapa.forEach((u, v) -> {
            BigDecimal suyo = v[0].add(v[1]).add(v[2]);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("userId", u);
            p.put("name", hogares.apodoDe(u));
            p.put("bills", v[0]);
            p.put("provisions", v[1]);
            p.put("savings", v[2]);
            p.put("total", suyo);
            p.put("paid", v[3]);
            p.put("pending", v[0].add(v[2]).subtract(v[3]).max(BigDecimal.ZERO));
            p.put("sharePct", pct(suyo, total));
            out.add(p);
        });
        return out;
    }

    /** HOGAR: las cuentas comunes y las mias. PERSONA: las suyas, si soy yo. */
    private Map<String, Object> fondos(Long yo, Alcance alcance) {
        List<Map<String, Object>> lista = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Long de = alcance.hogar() ? yo : alcance.persona();
        if (alcance.hogar() || yo.equals(de)) {
            for (Account a : cuentas.findByUserIdAndActiveTrueOrderByNameAsc(de)) {
                if (a.getKind() != AccountKind.OWN && a.getKind() != null) continue;
                BigDecimal saldo = saldos.confirmado(a.getId());
                total = total.add(saldo);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("accountId", a.getId());
                m.put("name", a.getName());
                m.put("balance", saldo);
                lista.add(m);
            }
        }
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("total", total);
        f.put("accounts", lista);
        return f;
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private static BigDecimal[] ceros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static double pct(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() <= 0) return 0;
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }
}
