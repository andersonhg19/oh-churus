package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Card;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.ScheduledKind;
import com.ohchurus.budget.repository.CardRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.util.ControlAcceso;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * LO QUE SE DEBE: las tarjetas de credito y lo que se paga en cuotas.
 *
 * Dos clases de deuda, sin intereses ni tablas de amortizacion a proposito
 * (la app no es el banco):
 *   · TARJETA: lo comprado con ella que aun no se ha pagado. Cada compra ya
 *     conto como gasto de su categoria en su mes; pagar la tarjeta solo la
 *     deja saldada y no vuelve a contar.
 *   · EN CUOTAS: un programado con fecha de fin (el credito, el computador,
 *     Exito). Se ve cuantas cuotas van, cuantas faltan y cuanto falta. Se
 *     puede ADELANTAR una cuota: se paga hoy la ultima que queda.
 */
@Slf4j
@Service
@Transactional
public class DeudasService {

    private final CardRepository tarjetas;
    private final MovementRepository movimientos;
    private final CategoryRepository categorias;
    private final HouseholdServiceImpl hogares;
    private final PlanFinancieroService plan;
    private final ControlAcceso acceso;

    public DeudasService(CardRepository tarjetas, MovementRepository movimientos, CategoryRepository categorias,
                         HouseholdServiceImpl hogares, PlanFinancieroService plan, ControlAcceso acceso) {
        this.tarjetas = tarjetas;
        this.movimientos = movimientos;
        this.categorias = categorias;
        this.hogares = hogares;
        this.plan = plan;
        this.acceso = acceso;
    }

    // ===== TARJETAS =========================================================

    public ResultDTO guardarTarjeta(Long yo, Long id, String nombre, String color, Integer diaDePago, Integer diaDeCorte) {
        Card t;
        if (id != null) {
            Optional<Card> existente = tarjetas.findByIdAndActiveTrue(id).filter(c -> yo.equals(c.getUserId()));
            if (existente.isEmpty()) return new ResultDTO(false, "Card not found", 404);
            t = existente.get();
        } else {
            List<Long> hIds = hogares.getHouseholdIds(yo);
            t = Card.builder().userId(yo).householdId(hIds.isEmpty() ? null : hIds.get(0)).active(true).build();
        }
        t.setName(nombre.trim());
        t.setColor(color);
        t.setPaymentDay(diaDePago);
        t.setCutoffDay(diaDeCorte);
        Card guardada = tarjetas.save(t);
        log.info("Card saved: id={} user={}", guardada.getId(), yo);
        return new ResultDTO(resumen(guardada, yo, LocalDate.now()));
    }

    /** Solo se borra una tarjeta saldada: si no, la deuda desapareceria. */
    public ResultDTO borrarTarjeta(Long yo, Long id) {
        Optional<Card> t = tarjetas.findByIdAndActiveTrue(id).filter(c -> yo.equals(c.getUserId()));
        if (t.isEmpty()) return new ResultDTO(false, "Card not found", 404);
        boolean debe = movimientos.findByCardIdAndActiveTrueOrderByDateAsc(id).stream()
                .anyMatch(m -> !Boolean.TRUE.equals(m.getCardPaid()));
        if (debe) return new ResultDTO(false, "La tarjeta todavia tiene compras sin pagar", 400);
        t.get().setActive(false);
        tarjetas.save(t.get());
        return new ResultDTO(true, "Card deleted", 0);
    }

    /** Pagar la tarjeta: queda saldado lo comprado (y las cuotas) hasta esa fecha. */
    public ResultDTO pagarTarjeta(Long yo, Long id, LocalDate hasta) {
        Optional<Card> t = tarjetas.findByIdAndActiveTrue(id).filter(c -> yo.equals(c.getUserId()));
        if (t.isEmpty()) return new ResultDTO(false, "Card not found", 404);
        LocalDate corte = hasta != null ? hasta : ultimoCorte(t.get(), LocalDate.now());
        BigDecimal total = BigDecimal.ZERO;
        int n = 0;
        for (Movement m : movimientos.findByCardIdAndActiveTrueOrderByDateAsc(id)) {
            if (Boolean.TRUE.equals(m.getCardPaid()) || m.getDate().isAfter(corte)) continue;
            m.setCardPaid(true);
            movimientos.save(m);
            total = total.add(m.getAmount());
            n++;
        }
        log.info("Card {} paid up to {}: {} items, {}", id, corte, n, total);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("paidItems", n);
        r.put("paidTotal", total);
        r.put("card", resumen(t.get(), yo, LocalDate.now()));
        return new ResultDTO(r);
    }

    /** Mis tarjetas: las que se pueden elegir en "Pagado con". */
    @Transactional(readOnly = true)
    public ResultDTO misTarjetas(Long yo) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Card c : tarjetas.findByUserIdAndActiveTrueOrderByNameAsc(yo)) out.add(resumen(c, yo, LocalDate.now()));
        return new ResultDTO(out);
    }

    // ===== DEUDAS ===========================================================

    /** Todo lo que se debe y que veo: tarjetas (mias y del hogar) y cuotas. */
    @Transactional(readOnly = true)
    public ResultDTO deudas(Long yo, LocalDate referencia) {
        LocalDate hoy = referencia != null ? referencia : LocalDate.now();
        List<Map<String, Object>> cards = new ArrayList<>();
        BigDecimal enTarjetas = BigDecimal.ZERO;
        for (Card c : tarjetas.visibles(yo, conHogares(yo))) {
            Map<String, Object> r = resumen(c, yo, hoy);
            enTarjetas = enTarjetas.add((BigDecimal) r.get("owed"));
            cards.add(r);
        }
        List<Map<String, Object>> loans = new ArrayList<>();
        BigDecimal enCuotas = BigDecimal.ZERO;
        BigDecimal cuotaDelMes = BigDecimal.ZERO;
        for (Map<String, Object> d : plan.cuotas(plan.visibles(yo), hoy)) {
            String clase = String.valueOf(d.get("kind"));
            if (ScheduledKind.SAVING.name().equals(clase) || ScheduledKind.PROVISION.name().equals(clase)) continue;
            /* Una deuda ya pagada no es deuda: no se lista ni suma en "este mes"
               (QA, ola 7.1: Exito pagado seguia sumando 580.000). */
            if (((Number) d.get("remainingInstallments")).intValue() == 0) continue;
            enCuotas = enCuotas.add((BigDecimal) d.get("remainingTotal"));
            cuotaDelMes = cuotaDelMes.add(PlanFinancieroService.nz((BigDecimal) d.get("monthlyFreed")));
            loans.add(d);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalOwed", enTarjetas.add(enCuotas));
        r.put("owedOnCards", enTarjetas);
        r.put("owedInInstallments", enCuotas);
        r.put("monthlyInstallments", cuotaDelMes);
        r.put("cards", cards);
        r.put("loans", loans);
        return new ResultDTO(r);
    }

    /**
     * ADELANTAR una cuota: se paga HOY la ultima cuota que falta. La deuda
     * termina un mes antes y la cuota cuenta como gasto de este mes, que es
     * cuando salio la plata. Cuando el plan llegue a ese ultimo mes, ya la
     * encuentra pagada y no la vuelve a pedir.
     */
    public ResultDTO adelantarCuota(Long yo, Long programadoId) {
        LocalDate hoy = LocalDate.now();
        PlanFinancieroService.Linea linea = plan.visibles(yo).stream()
                .filter(l -> l.p().getId().equals(programadoId)).findFirst().orElse(null);
        if (linea == null || linea.p().getEndDate() == null || linea.ingreso()) {
            return new ResultDTO(false, "Debt not found", 404);
        }
        if (!yo.equals(linea.pagador()) && !linea.compartida()) {
            return new ResultDTO(false, "Debt not found", 404);
        }
        List<PlanFinancieroService.Fecha> faltan = plan.fechas(linea, linea.p().getStartDate(), linea.p().getEndDate(), hoy)
                .stream().filter(f -> !"PAGADO".equals(f.estado()) && !"LIBERADO".equals(f.estado())).toList();
        if (faltan.isEmpty()) return new ResultDTO(false, "Ya no quedan cuotas por pagar", 400);
        PlanFinancieroService.Fecha ultima = faltan.get(faltan.size() - 1);
        Movement m;
        if (ultima.movimientoId() != null) {
            m = movimientos.findById(ultima.movimientoId()).orElseThrow();
            m.setDate(hoy);
            m.setConfirmed(true);
        } else {
            m = Movement.builder()
                    .userId(yo)
                    .categoryId(linea.p().getCategoryId())
                    .date(hoy)
                    .amount(linea.p().getAmount() != null ? linea.p().getAmount() : BigDecimal.ZERO)
                    .description(linea.p().getName() + " (cuota adelantada)")
                    .scheduledMovementId(linea.p().getId())
                    .periodStart(ultima.clave())
                    .confirmed(true)
                    .active(true)
                    .build();
        }
        Movement guardado = movimientos.save(m);
        log.info("Installment advanced: scheduled={} occurrence={} movement={}", programadoId, ultima.clave(), guardado.getId());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("movementId", guardado.getId());
        r.put("amount", guardado.getAmount());
        r.put("remainingInstallments", faltan.size() - 1);
        r.put("newEndDate", faltan.size() > 1 ? faltan.get(faltan.size() - 2).fecha().toString() : null);
        return new ResultDTO(r);
    }

    // ===== apoyo ============================================================

    /** El ultimo dia de corte que ya paso (hoy incluido); sin corte, hoy. */
    static LocalDate ultimoCorte(Card c, LocalDate hoy) {
        if (c.getCutoffDay() == null) return hoy;
        LocalDate esteMes = hoy.withDayOfMonth(Math.min(c.getCutoffDay(), hoy.lengthOfMonth()));
        if (!esteMes.isAfter(hoy)) return esteMes;
        LocalDate anterior = hoy.minusMonths(1);
        return anterior.withDayOfMonth(Math.min(c.getCutoffDay(), anterior.lengthOfMonth()));
    }

    /** El proximo dia N del mes desde hoy (hoy incluido). */
    static LocalDate proximoDia(int dia, LocalDate hoy) {
        LocalDate esteMes = hoy.withDayOfMonth(Math.min(dia, hoy.lengthOfMonth()));
        if (!esteMes.isBefore(hoy)) return esteMes;
        LocalDate siguiente = hoy.plusMonths(1);
        return siguiente.withDayOfMonth(Math.min(dia, siguiente.lengthOfMonth()));
    }

    private List<Long> conHogares(Long yo) {
        List<Long> h = hogares.getHouseholdIds(yo);
        return h.isEmpty() ? List.of(-1L) : h;
    }

    /**
     * Lo que se debe en una tarjeta, agrupado por compra. De la tarjeta de la
     * pareja solo se ve lo comprado en categorias comunes: lo privado sigue
     * siendo privado aunque la tarjeta sea visible.
     */
    private Map<String, Object> resumen(Card c, Long yo, LocalDate hoy) {
        boolean mia = yo.equals(c.getUserId());
        BigDecimal debe = BigDecimal.ZERO, yaToca = BigDecimal.ZERO, esteCiclo = BigDecimal.ZERO;
        /* Con dia de corte: lo comprado hasta el ultimo corte es el extracto
           que toca pagar; lo de despues va en el siguiente. Sin corte, todo lo
           comprado hasta hoy. */
        LocalDate corte = ultimoCorte(c, hoy);
        Map<Long, Map<String, Object>> compras = new LinkedHashMap<>();
        Map<Long, Category> cache = new HashMap<>();
        for (Movement m : movimientos.findByCardIdAndActiveTrueOrderByDateAsc(c.getId())) {
            if (!mia && !acceso.puedeVer(m.getUserId(), m.getCategoryId())) continue;
            boolean pagada = Boolean.TRUE.equals(m.getCardPaid());
            Long clave = m.getPurchaseId() != null ? m.getPurchaseId() : m.getId();
            Map<String, Object> compra = compras.computeIfAbsent(clave, k -> {
                Category cat = cache.computeIfAbsent(m.getCategoryId(), id -> categorias.findById(id).orElse(null));
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("purchaseId", k);
                x.put("description", m.getDescription());
                x.put("categoryId", m.getCategoryId());
                x.put("categoryName", cat != null ? cat.getName() : null);
                x.put("categoryIcon", cat != null ? cat.getIcon() : null);
                x.put("categoryColor", cat != null ? cat.getColor() : null);
                x.put("date", m.getDate().toString());
                x.put("total", BigDecimal.ZERO);
                x.put("owed", BigDecimal.ZERO);
                x.put("installments", m.getInstallmentTotal() != null ? m.getInstallmentTotal() : 1);
                x.put("paidInstallments", 0);
                x.put("nextDate", null);
                x.put("nextAmount", null);
                return x;
            });
            compra.put("total", ((BigDecimal) compra.get("total")).add(m.getAmount()));
            if (pagada) {
                compra.put("paidInstallments", (Integer) compra.get("paidInstallments") + 1);
                continue;
            }
            compra.put("owed", ((BigDecimal) compra.get("owed")).add(m.getAmount()));
            if (compra.get("nextDate") == null) {
                compra.put("nextDate", m.getDate().toString());
                compra.put("nextAmount", m.getAmount());
            }
            debe = debe.add(m.getAmount());
            if (!m.getDate().isAfter(corte)) yaToca = yaToca.add(m.getAmount());
            else if (!m.getDate().isAfter(hoy)) esteCiclo = esteCiclo.add(m.getAmount());
        }
        List<Map<String, Object>> abiertas = new ArrayList<>();
        for (Map<String, Object> x : compras.values()) {
            if (((BigDecimal) x.get("owed")).signum() > 0) abiertas.add(x);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", c.getId());
        r.put("name", c.getName());
        r.put("color", c.getColor());
        r.put("paymentDay", c.getPaymentDay());
        r.put("cutoffDay", c.getCutoffDay());
        r.put("lastCutoff", c.getCutoffDay() != null ? corte.toString() : null);
        r.put("nextPaymentDate", c.getPaymentDay() != null ? proximoDia(c.getPaymentDay(), hoy).toString() : null);
        r.put("currentCycle", esteCiclo);
        r.put("userId", c.getUserId());
        r.put("ownerName", hogares.apodoDe(c.getUserId()));
        r.put("mine", mia);
        r.put("owed", debe);
        r.put("dueNow", yaToca);
        r.put("upcoming", debe.subtract(yaToca).subtract(esteCiclo));
        r.put("purchases", abiertas);
        return r;
    }
}
