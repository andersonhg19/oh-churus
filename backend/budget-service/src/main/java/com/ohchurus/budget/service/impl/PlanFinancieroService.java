package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Account;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.AccountKind;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.ScheduledKind;
import com.ohchurus.budget.repository.AccountRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.util.CalendarioDeRecurrencias;
import com.ohchurus.budget.util.CalendarioDeRecurrencias.Ocurrencia;
import com.ohchurus.budget.util.Computables;
import com.ohchurus.budget.util.PeriodUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EL PLAN: que pasa con la plata entre un pago y el siguiente.
 *
 * Todo lo de aqui se CALCULA desde los programados y los movimientos; no hay
 * tabla de plan. Es la misma decision que el saldo de las cuentas y el arrastre
 * de los sobres: un plan guardado es un dato derivado que se desincroniza en
 * cuanto alguien cambia el parqueadero de 210.000 a 150.000, y entonces no hay
 * forma de saber cual de los dos miente. Cambiar un monto, una fecha o una
 * frecuencia reorganiza el plan solo, porque el plan no existe hasta que se
 * pregunta.
 *
 * LAS CUATRO CLASES DE SALIDA (ver ScheduledKind y la V10) son lo que permite
 * no contar nada dos veces:
 *
 *   DEDUCTION resta del ingreso (el computador que se cobra de la nomina):
 *             nunca aparece ademas como "por pagar".
 *   PAYMENT   es una obligacion con fecha.
 *   SAVING    sale, pero no es consumo: se ensena aparte.
 *   PROVISION es plata SEPARADA. No genera pendiente; lo que se compre de
 *             verdad en esa categoria se descuenta de lo separado, y lo
 *             separado no se vuelve a contar como gasto.
 *
 * PRIVACIDAD. Los programados que se leen son los de findHouseholdActive: los
 * mios y los de categorias del hogar. Lo que el otro tenga en su categoria
 * privada no entra en ninguna cuenta de las que ve el resto.
 */
@Slf4j
@Service
public class PlanFinancieroService {

    /** Cuantos dias antes de un vencimiento se avisa. */
    static final int DIAS_DE_AVISO = 3;

    private final ScheduledMovementRepository programados;
    private final MovementRepository movimientos;
    private final CategoryRepository categorias;
    private final AccountRepository cuentas;
    private final SaldoDeCuenta saldos;
    private final HouseholdServiceImpl hogares;

    public PlanFinancieroService(ScheduledMovementRepository programados,
                                 MovementRepository movimientos,
                                 CategoryRepository categorias,
                                 AccountRepository cuentas,
                                 SaldoDeCuenta saldos,
                                 HouseholdServiceImpl hogares) {
        this.programados = programados;
        this.movimientos = movimientos;
        this.categorias = categorias;
        this.cuentas = cuentas;
        this.saldos = saldos;
        this.hogares = hogares;
    }

    // ========================================================================
    // Las piezas: un programado visible y cada una de sus ocurrencias

    /** Un programado con lo que hace falta saber de el, resuelto una vez. */
    record Linea(ScheduledMovement p, Category cat, Long pagador, ScheduledKind clase,
                 Map<LocalDate, Movement> porClave) {
        boolean ingreso() { return cat != null && cat.getType() == CategoryType.INCOME; }
        boolean compartida() { return cat != null && cat.getHouseholdId() != null; }
    }

    /** Una fecha concreta de un programado, con su estado. */
    record Fecha(Linea l, LocalDate fecha, LocalDate clave, BigDecimal monto, String estado, Long movimientoId) {}

    private List<Linea> visibles(Long yo) {
        List<Long> hIds = hogares.getHouseholdIds(yo);
        List<ScheduledMovement> lista = !hIds.isEmpty()
                ? programados.findHouseholdActive(yo, hIds)
                : programados.findByUserIdAndActiveTrue(yo);
        Map<Long, Category> cache = new HashMap<>();
        List<Linea> lineas = new ArrayList<>();
        for (ScheduledMovement p : lista) {
            Category c = cache.computeIfAbsent(p.getCategoryId(),
                    id -> categorias.findByIdAndActiveTrue(id).orElse(null));
            if (c == null) continue;
            Map<LocalDate, Movement> porClave = new HashMap<>();
            for (Movement m : movimientos.findByScheduledMovementId(p.getId())) {
                if (!Boolean.TRUE.equals(m.getActive())) continue;
                porClave.putIfAbsent(m.getPeriodStart() != null ? m.getPeriodStart() : m.getDate(), m);
            }
            lineas.add(new Linea(p, c, ScheduledMovementServiceImpl.pagadorDe(p),
                    ScheduledKind.de(p.getKind()), porClave));
        }
        return lineas;
    }

    /**
     * Las fechas de una linea entre dos dias, con su estado:
     * PAGADO (confirmado), PENDIENTE, VENCIDO (paso la fecha sin confirmar),
     * PREVISTO (aun no generado) y PROVISION (plata que se separa).
     * El monto es el real si ya existe el movimiento: si el parqueadero de
     * este mes costo 150.000, el plan dice 150.000 y no 210.000.
     */
    private List<Fecha> fechas(Linea l, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        List<Fecha> out = new ArrayList<>();
        for (Ocurrencia o : CalendarioDeRecurrencias.ocurrenciasHasta(l.p(), hasta)) {
            if (o.fecha().isBefore(desde) || o.fecha().isAfter(hasta)) continue;
            Movement m = l.porClave().get(o.clave());
            if (m == null) m = l.porClave().get(o.fecha());
            BigDecimal monto = m != null && m.getAmount() != null ? m.getAmount()
                    : (l.p().getAmount() != null ? l.p().getAmount() : BigDecimal.ZERO);
            String estado;
            if (l.clase() == ScheduledKind.PROVISION) estado = "PROVISION";
            else if (m != null && Boolean.TRUE.equals(m.getConfirmed())) estado = "PAGADO";
            else if (o.fecha().isBefore(hoy)) estado = "VENCIDO";
            else if (m != null) estado = "PENDIENTE";
            else estado = "PREVISTO";
            out.add(new Fecha(l, o.fecha(), o.clave(), monto, estado, m != null ? m.getId() : null));
        }
        return out;
    }

    private Map<String, Object> aMapa(Fecha f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scheduledMovementId", f.l().p().getId());
        m.put("movementId", f.movimientoId());
        m.put("name", f.l().p().getName());
        m.put("date", f.fecha().toString());
        m.put("amount", f.monto());
        m.put("status", f.estado());
        m.put("kind", f.l().clase().name());
        m.put("income", f.l().ingreso());
        m.put("categoryId", f.l().cat().getId());
        m.put("categoryName", f.l().cat().getName());
        m.put("categoryIcon", f.l().cat().getIcon());
        m.put("shared", f.l().compartida());
        m.put("payerUserId", f.l().pagador());
        m.put("payerName", hogares.apodoDe(f.l().pagador()));
        m.put("notes", f.l().p().getNotes());
        return m;
    }

    private static BigDecimal suma(Collection<Fecha> l) {
        return l.stream().map(Fecha::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ========================================================================
    // MI QUINCENA

    /**
     * El plan de la quincena en curso y de la siguiente para quien pregunta.
     *
     * Una "quincena" va de un dia de pago al dia antes del siguiente. Los dias
     * de pago salen de MIS ingresos programados: a Samy le pagan el 1 y el 15,
     * y a Anderson una vez al mes, asi que su "quincena" es el mes entero y la
     * pantalla lo dice. No hay dias de pago escritos a mano en ningun sitio.
     */
    @Transactional(readOnly = true)
    public ResultDTO quincena(Long yo, LocalDate referencia) {
        try {
            LocalDate hoy = referencia != null ? referencia : LocalDate.now();
            List<Linea> todas = visibles(yo);
            List<Linea> mias = todas.stream().filter(l -> yo.equals(l.pagador())).collect(Collectors.toList());

            TreeSet<LocalDate> pagos = new TreeSet<>();
            for (Linea l : mias) {
                if (!l.ingreso()) continue;
                fechas(l, hoy.minusDays(70), hoy.plusDays(100), hoy).forEach(f -> pagos.add(f.fecha()));
            }

            LocalDate inicio = Optional.ofNullable(pagos.floor(hoy)).orElse(hoy);
            LocalDate siguiente = pagos.higher(inicio);
            LocalDate fin = siguiente != null ? siguiente.minusDays(1) : inicio.plusMonths(1).minusDays(1);
            /* El pago que ABRE la siguiente quincena es fin+1; el que la cierra
               es el de despues de ese. Con higher(fin) salia el mismo dia de
               apertura y la siguiente quincena duraba cero dias. */
            LocalDate despues = pagos.higher(fin.plusDays(1));
            LocalDate finSiguiente = despues != null ? despues.minusDays(1) : fin.plusMonths(1);

            Map<String, Object> actual = ventana(yo, mias, inicio, fin, hoy);
            Map<String, Object> proxima = ventana(yo, mias, fin.plusDays(1), finSiguiente, hoy);

            // Lo que quedo sin confirmar de antes de esta quincena.
            List<Map<String, Object>> deAntes = new ArrayList<>();
            for (Linea l : mias) {
                if (l.ingreso() || l.clase() == ScheduledKind.PROVISION) continue;
                for (Fecha f : fechas(l, inicio.minusDays(90), inicio.minusDays(1), hoy)) {
                    if ("VENCIDO".equals(f.estado())) deAntes.add(aMapa(f));
                }
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("today", hoy.toString());
            r.put("payDates", pagos.stream().map(LocalDate::toString).collect(Collectors.toList()));
            r.put("hasIncome", !pagos.isEmpty());
            r.put("current", actual);
            r.put("next", proxima);
            r.put("overdueFromBefore", deAntes);
            r.put("available", disponible(yo, mias, inicio, fin, hoy));
            r.put("installments", cuotas(mias, hoy));
            r.put("alerts", alertas(mias, hoy, actual));
            return new ResultDTO(r);
        } catch (Exception e) {
            log.error("Error building quincena for user {}: {}", yo, e.getMessage(), e);
            return new ResultDTO(false, "Error building quincena", 500);
        }
    }

    /** El plan de una ventana de fechas: lo que entra, lo que sale y lo que queda. */
    private Map<String, Object> ventana(Long yo, List<Linea> mias, LocalDate inicio, LocalDate fin, LocalDate hoy) {
        List<Fecha> ingresos = new ArrayList<>(), descuentos = new ArrayList<>(), pagos = new ArrayList<>(),
                ahorro = new ArrayList<>(), provisiones = new ArrayList<>();
        for (Linea l : mias) {
            for (Fecha f : fechas(l, inicio, fin, hoy)) {
                if (l.ingreso()) ingresos.add(f);
                else switch (l.clase()) {
                    case DEDUCTION -> descuentos.add(f);
                    case SAVING -> ahorro.add(f);
                    case PROVISION -> provisiones.add(f);
                    default -> pagos.add(f);
                }
            }
        }
        Comparator<Fecha> porFecha = Comparator.comparing(Fecha::fecha).thenComparing(f -> f.l().p().getName());
        List.of(ingresos, descuentos, pagos, ahorro, provisiones).forEach(x -> x.sort(porFecha));

        BigDecimal bruto = suma(ingresos);
        BigDecimal desc = suma(descuentos);
        BigDecimal efectivo = bruto.subtract(desc);
        BigDecimal obligaciones = suma(pagos);
        BigDecimal ahorrado = suma(ahorro);
        BigDecimal separado = suma(provisiones);
        BigDecimal queda = efectivo.subtract(obligaciones).subtract(ahorrado).subtract(separado);
        BigDecimal pagado = suma(pagos.stream().filter(f -> "PAGADO".equals(f.estado())).collect(Collectors.toList()));

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("start", inicio.toString());
        v.put("end", fin.toString());
        v.put("grossIncome", bruto);
        v.put("deductions", desc);
        v.put("netIncome", efectivo);
        v.put("payments", obligaciones);
        v.put("paymentsPaid", pagado);
        v.put("paymentsLeft", obligaciones.subtract(pagado));
        v.put("savings", ahorrado);
        v.put("provisions", separado);
        /* Lo que queda despues de pagar, ahorrar y separar: el respaldo. Si sale
           negativo la quincena no alcanza, y se dice con la cifra: no se ajusta
           nada en silencio. */
        v.put("leftover", queda);
        v.put("tight", queda.signum() < 0);
        v.put("incomeItems", ingresos.stream().map(this::aMapa).collect(Collectors.toList()));
        v.put("deductionItems", descuentos.stream().map(this::aMapa).collect(Collectors.toList()));
        v.put("paymentItems", pagos.stream().map(this::aMapa).collect(Collectors.toList()));
        v.put("savingItems", ahorro.stream().map(this::aMapa).collect(Collectors.toList()));
        v.put("provisionItems", provisiones.stream().map(this::aMapa).collect(Collectors.toList()));
        return v;
    }

    /**
     * CUANTO PUEDO GASTAR HOY sin tocar lo comprometido.
     *
     * Parte del saldo CONFIRMADO de mis cuentas propias —lo que el banco
     * deberia decir— y le resta lo que todavia tiene que salir antes del
     * siguiente pago: obligaciones y ahorro sin pagar, y lo que queda de cada
     * provision (lo separado menos lo ya comprado en esa categoria). El saldo
     * del banco no es plata disponible: esta cifra si.
     *
     * Las deducciones no se restan: ya las desconto la nomina.
     */
    private Map<String, Object> disponible(Long yo, List<Linea> mias, LocalDate inicio, LocalDate fin, LocalDate hoy) {
        BigDecimal enCuentas = BigDecimal.ZERO;
        for (Account a : cuentas.findByUserIdAndActiveTrueOrderByNameAsc(yo)) {
            if (a.getKind() == AccountKind.OWN) enCuentas = enCuentas.add(saldos.confirmado(a.getId()));
        }
        BigDecimal porPagar = BigDecimal.ZERO;
        Map<Long, BigDecimal> separadoPorCategoria = new HashMap<>();
        for (Linea l : mias) {
            if (l.ingreso() || l.clase() == ScheduledKind.DEDUCTION) continue;
            for (Fecha f : fechas(l, inicio, fin, hoy)) {
                if (l.clase() == ScheduledKind.PROVISION) {
                    separadoPorCategoria.merge(l.cat().getId(), f.monto(), BigDecimal::add);
                } else if (!"PAGADO".equals(f.estado())) {
                    porPagar = porPagar.add(f.monto());
                }
            }
        }
        BigDecimal reservado = BigDecimal.ZERO;
        if (!separadoPorCategoria.isEmpty()) {
            Map<Long, BigDecimal> comprado = new HashMap<>();
            for (Movement m : movimientos.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(yo, inicio, fin)) {
                if (m.getScheduledMovementId() != null || !Computables.suma(m)) continue;
                comprado.merge(m.getCategoryId(), Computables.importe(m), BigDecimal::add);
            }
            for (Map.Entry<Long, BigDecimal> e : separadoPorCategoria.entrySet()) {
                BigDecimal resto = e.getValue().subtract(comprado.getOrDefault(e.getKey(), BigDecimal.ZERO));
                if (resto.signum() > 0) reservado = reservado.add(resto);
            }
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("accountsBalance", enCuentas);
        d.put("stillToPay", porPagar);
        d.put("reservedInProvisions", reservado);
        d.put("freeToSpend", enCuentas.subtract(porPagar).subtract(reservado));
        return d;
    }

    /**
     * Las financiaciones: lo que tiene fecha de fin. Cuantas cuotas faltan,
     * cuanto falta en total, cuando termina y cuanto se libera al mes.
     * Al terminar, el programado deja de generar y el historial se queda.
     */
    private List<Map<String, Object>> cuotas(List<Linea> mias, LocalDate hoy) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Linea l : mias) {
            if (l.p().getEndDate() == null || l.ingreso()) continue;
            if (l.p().getEndDate().isBefore(hoy)) continue;
            List<Fecha> todas = fechas(l, l.p().getStartDate(), l.p().getEndDate(), hoy);
            List<Fecha> faltan = todas.stream().filter(f -> !"PAGADO".equals(f.estado())).collect(Collectors.toList());
            long pagadas = todas.size() - faltan.size();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("scheduledMovementId", l.p().getId());
            c.put("name", l.p().getName());
            c.put("installmentAmount", l.p().getAmount());
            c.put("totalInstallments", todas.size());
            c.put("paidInstallments", pagadas);
            c.put("remainingInstallments", faltan.size());
            c.put("remainingTotal", suma(faltan));
            c.put("nextDate", faltan.isEmpty() ? null : faltan.get(0).fecha().toString());
            c.put("endDate", l.p().getEndDate().toString());
            c.put("monthlyFreed", mensualizado(l.p()));
            c.put("kind", l.clase().name());
            out.add(c);
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("endDate"))));
        return out;
    }

    /** Lo que un programado pesa en un mes: un bimestral de 150.000 son 75.000. */
    static BigDecimal mensualizado(ScheduledMovement p) {
        BigDecimal a = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
        if (p.getFrequency() == null) return a;
        return switch (p.getFrequency()) {
            case DAILY -> a.multiply(BigDecimal.valueOf(30));
            case WEEKLY -> a.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 0, java.math.RoundingMode.HALF_UP);
            case BIWEEKLY -> a.multiply(BigDecimal.valueOf(26)).divide(BigDecimal.valueOf(12), 0, java.math.RoundingMode.HALF_UP);
            case MONTHLY -> a;
            case BIMONTHLY -> a.divide(BigDecimal.valueOf(2), 0, java.math.RoundingMode.HALF_UP);
            case QUARTERLY -> a.divide(BigDecimal.valueOf(3), 0, java.math.RoundingMode.HALF_UP);
            case SEMIANNUAL -> a.divide(BigDecimal.valueOf(6), 0, java.math.RoundingMode.HALF_UP);
            case ANNUAL -> a.divide(BigDecimal.valueOf(12), 0, java.math.RoundingMode.HALF_UP);
        };
    }

    private List<Map<String, Object>> alertas(List<Linea> mias, LocalDate hoy, Map<String, Object> actual) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Linea l : mias) {
            if (l.ingreso() || l.clase() == ScheduledKind.PROVISION) continue;
            for (Fecha f : fechas(l, hoy.minusDays(60), hoy.plusDays(DIAS_DE_AVISO), hoy)) {
                if ("VENCIDO".equals(f.estado())) {
                    out.add(alerta("VENCIDO", f.l().p().getName() + " vencio el " + f.fecha(), f));
                } else if (!"PAGADO".equals(f.estado()) && !f.fecha().isBefore(hoy)) {
                    out.add(alerta("PROXIMO", f.l().p().getName() + " vence el " + f.fecha(), f));
                }
            }
            if (l.p().getEndDate() != null && !l.p().getEndDate().isBefore(hoy)) {
                long faltan = fechas(l, hoy, l.p().getEndDate(), hoy).size();
                if (faltan > 0 && faltan <= 2) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("type", "CUOTA_TERMINA");
                    a.put("message", l.p().getName() + ": quedan " + faltan + " cuota(s); al terminar se liberan "
                            + mensualizado(l.p()).toPlainString() + " al mes");
                    a.put("scheduledMovementId", l.p().getId());
                    out.add(a);
                }
            }
        }
        if (Boolean.TRUE.equals(actual.get("tight"))) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("type", "QUINCENA_AJUSTADA");
            a.put("message", "Esta quincena no alcanza: faltan "
                    + ((BigDecimal) actual.get("leftover")).negate().toPlainString()
                    + ". Revisa que provision se puede mover sin incumplir un vencimiento.");
            out.add(a);
        }
        return out;
    }

    private Map<String, Object> alerta(String tipo, String mensaje, Fecha f) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", tipo);
        a.put("message", mensaje);
        a.put("scheduledMovementId", f.l().p().getId());
        a.put("movementId", f.movimientoId());
        a.put("date", f.fecha().toString());
        a.put("amount", f.monto());
        return a;
    }

    // ========================================================================
    // CALENDARIO

    /** Todas las fechas visibles para mi entre dos dias, agrupables por dia. */
    @Transactional(readOnly = true)
    public ResultDTO calendario(Long yo, LocalDate desde, LocalDate hasta) {
        try {
            LocalDate hoy = LocalDate.now();
            LocalDate d = desde != null ? desde : hoy.withDayOfMonth(1);
            LocalDate h = hasta != null ? hasta : d.plusMonths(1).minusDays(1);
            if (h.isAfter(d.plusDays(400))) h = d.plusDays(400);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Linea l : visibles(yo)) {
                for (Fecha f : fechas(l, d, h, hoy)) items.add(aMapa(f));
            }
            items.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("date")))
                    .thenComparing(m -> String.valueOf(m.get("name"))));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("from", d.toString());
            r.put("to", h.toString());
            r.put("items", items);
            return new ResultDTO(r);
        } catch (Exception e) {
            log.error("Error building calendar: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error building calendar", 500);
        }
    }

    // ========================================================================
    // RESPONSABILIDADES DEL HOGAR

    /**
     * Quien paga que en el periodo de presupuesto. De los demas solo cuenta lo
     * de categorias COMPARTIDAS: lo privado de otro no se suma ni se nombra.
     */
    @Transactional(readOnly = true)
    public ResultDTO responsabilidades(Long yo, Integer diaDeCorte, LocalDate referencia) {
        try {
            LocalDate ref = referencia != null ? referencia : LocalDate.now();
            int dia = diaDeCorte != null ? diaDeCorte : 1;
            LocalDate inicio = PeriodUtils.getStartOfPeriod(dia, ref);
            LocalDate fin = PeriodUtils.getEndOfPeriod(dia, inicio);
            LocalDate hoy = LocalDate.now();

            Map<Long, List<Fecha>> porPersona = new LinkedHashMap<>();
            for (Long id : hogares.companerosDeHogar(yo)) porPersona.put(id, new ArrayList<>());
            for (Linea l : visibles(yo)) {
                if (!l.compartida() && !yo.equals(l.pagador())) continue;
                porPersona.computeIfAbsent(l.pagador(), k -> new ArrayList<>())
                        .addAll(fechas(l, inicio, fin, hoy));
            }

            List<Map<String, Object>> personas = new ArrayList<>();
            BigDecimal comunTotal = BigDecimal.ZERO;
            for (Map.Entry<Long, List<Fecha>> e : porPersona.entrySet()) {
                List<Fecha> f = e.getValue();
                List<Fecha> ingresos = f.stream().filter(x -> x.l().ingreso()).collect(Collectors.toList());
                List<Fecha> salidas = f.stream().filter(x -> !x.l().ingreso()).collect(Collectors.toList());
                List<Fecha> comunes = salidas.stream().filter(x -> x.l().compartida()).collect(Collectors.toList());
                Map<String, BigDecimal> porClase = new LinkedHashMap<>();
                for (ScheduledKind k : ScheduledKind.values()) {
                    porClase.put(k.name(), suma(comunes.stream().filter(x -> x.l().clase() == k).collect(Collectors.toList())));
                }
                BigDecimal comun = suma(comunes);
                comunTotal = comunTotal.add(comun);
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("userId", e.getKey());
                p.put("name", hogares.apodoDe(e.getKey()));
                p.put("isMe", yo.equals(e.getKey()));
                p.put("income", suma(ingresos.stream().filter(x -> x.l().compartida() || yo.equals(e.getKey())).collect(Collectors.toList())));
                p.put("sharedTotal", comun);
                p.put("sharedByKind", porClase);
                p.put("sharedPaid", suma(comunes.stream().filter(x -> "PAGADO".equals(x.estado())).collect(Collectors.toList())));
                p.put("items", salidas.stream().filter(x -> x.l().compartida() || yo.equals(e.getKey()))
                        .sorted(Comparator.comparing(Fecha::fecha)).map(this::aMapa).collect(Collectors.toList()));
                personas.add(p);
            }
            for (Map<String, Object> p : personas) {
                BigDecimal mio = (BigDecimal) p.get("sharedTotal");
                p.put("sharePct", comunTotal.signum() > 0
                        ? mio.multiply(BigDecimal.valueOf(100)).divide(comunTotal, 1, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("periodStart", inicio.toString());
            r.put("periodEnd", fin.toString());
            r.put("sharedTotal", comunTotal);
            r.put("people", personas);
            return new ResultDTO(r);
        } catch (Exception e) {
            log.error("Error building responsibilities: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error building responsibilities", 500);
        }
    }

    // ========================================================================
    // PROYECCION

    /**
     * Los proximos N periodos de presupuesto para mi: lo que entra, lo que
     * sale por clase y lo que queda, y que cuotas terminan en cada uno. Asi se
     * ve como mejora la disponibilidad a medida que se acaban los creditos.
     */
    @Transactional(readOnly = true)
    public ResultDTO proyeccion(Long yo, Integer diaDeCorte, int periodos) {
        try {
            int dia = diaDeCorte != null ? diaDeCorte : 1;
            int n = Math.max(1, Math.min(periodos, 12));
            LocalDate hoy = LocalDate.now();
            List<Linea> mias = visibles(yo).stream().filter(l -> yo.equals(l.pagador())).collect(Collectors.toList());
            List<Map<String, Object>> out = new ArrayList<>();
            LocalDate inicio = PeriodUtils.getStartOfPeriod(dia, hoy);
            for (int i = 0; i < n; i++) {
                LocalDate fin = PeriodUtils.getEndOfPeriod(dia, inicio);
                Map<String, Object> v = ventana(yo, mias, inicio, fin, hoy);
                v.keySet().removeIf(k -> k.endsWith("Items"));
                List<String> terminan = new ArrayList<>();
                BigDecimal libera = BigDecimal.ZERO;
                for (Linea l : mias) {
                    LocalDate f = l.p().getEndDate();
                    if (f != null && !f.isBefore(inicio) && !f.isAfter(fin)) {
                        terminan.add(l.p().getName());
                        libera = libera.add(mensualizado(l.p()));
                    }
                }
                v.put("endingInstallments", terminan);
                v.put("freedNextPeriod", libera);
                out.add(v);
                inicio = fin.plusDays(1);
            }
            return new ResultDTO(out);
        } catch (Exception e) {
            log.error("Error building projection: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error building projection", 500);
        }
    }
}
