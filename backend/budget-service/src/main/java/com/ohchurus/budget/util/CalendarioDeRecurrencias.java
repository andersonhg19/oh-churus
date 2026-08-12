package com.ohchurus.budget.util;

import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.enums.WeekendPolicy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * ============================================================================
 * EL CALENDARIO DE UNA RECURRENCIA
 * ============================================================================
 *
 * Dada la definicion de un programado, dice QUE DIAS le tocan. Nada mas: no
 * sabe de base de datos, ni de reloj, ni de DTOs. Recibe la fecha limite y
 * devuelve fechas, asi que se puede probar un programado diario a diez anos
 * vista en un microsegundo.
 *
 * LA DECISION QUE LO SOSTIENE: SE ENUMERA DESDE EL ANCLA
 * ------------------------------------------------------
 * La ocurrencia numero n se calcula SIEMPRE como "ancla + n periodos", nunca
 * como "la anterior + un periodo". Es la misma leccion que el motor de tiempo
 * del proyecto hermano: sumar incrementos acumula error, contar desde una marca
 * fija no.
 *
 * Aqui el error no era de milisegundos, era de frecuencia entera. El generador
 * viejo recorria PERIODOS DE PRESUPUESTO —o sea meses— y por cada uno se
 * preguntaba "¿aplica?", que solo se puede responder si o no. Con eso DAILY,
 * WEEKLY y BIWEEKLY generaban lo mismo que MONTHLY: un movimiento al mes. Tres
 * de las ocho frecuencias del catalogo eran mentira.
 *
 * Y hay un segundo error que el ancla evita gratis: un programado el dia 31
 * enumerado "el anterior + un mes" se va al 28 en febrero y YA NO VUELVE al 31.
 * Desde el ancla, febrero es el 28 y marzo vuelve a ser el 31.
 *
 * LAS TRES FECHAS DE UNA OCURRENCIA
 * ---------------------------------
 * Cada ocurrencia lleva tres fechas y conviene no confundirlas:
 *
 *  · CANONICA — la que sale del ancla. No depende de nada mas.
 *  · FECHA — la canonica despues de la politica de fin de semana. Es la que se
 *    graba en el movimiento y la que el usuario ve y puede mover.
 *  · CLAVE — con la que se decide si la ocurrencia ya existe. Ver mas abajo.
 */
public final class CalendarioDeRecurrencias {

    /**
     * Tope duro de ocurrencias que se enumeran de un tiron por programado.
     *
     * No es una regla de producto, es un cinturon: un programado DIARIO con el
     * ancla mal puesta en 1990 no puede colgar el panel. 4000 son once anos de
     * un diario, mucho mas de lo que cualquiera va a tener sin materializar.
     */
    public static final int TOPE_DE_ENUMERACION = 4000;

    /** Las frecuencias que caben varias veces en el mismo mes. */
    private static final Set<Frequency> SUB_MENSUALES =
            EnumSet.of(Frequency.DAILY, Frequency.WEEKLY, Frequency.BIWEEKLY);

    private CalendarioDeRecurrencias() {}

    /**
     * Una ocurrencia ya resuelta.
     *
     * @param canonica la que sale del ancla, sin tocar
     * @param fecha    la canonica con la politica de fin de semana aplicada
     * @param clave    la que identifica la ocurrencia (se graba en periodStart)
     */
    public record Ocurrencia(LocalDate canonica, LocalDate fecha, LocalDate clave) {}

    /**
     * Todas las ocurrencias del programado desde su ancla hasta {@code hasta},
     * recortadas ademas por su fecha de fin si la tiene.
     */
    public static List<Ocurrencia> ocurrenciasHasta(ScheduledMovement programado, LocalDate hasta) {
        if (programado == null || programado.getStartDate() == null
                || programado.getFrequency() == null || hasta == null) {
            return Collections.emptyList();
        }

        LocalDate ancla = programado.getStartDate();
        LocalDate limite = programado.getEndDate() != null && programado.getEndDate().isBefore(hasta)
                ? programado.getEndDate()
                : hasta;
        if (limite.isBefore(ancla)) {
            return Collections.emptyList();
        }

        List<Ocurrencia> ocurrencias = new ArrayList<>();
        /* El indice puede correr un poco mas que el tope: las primeras vueltas
           de un mensual con dia fijo anterior al del ancla no producen nada. */
        for (int n = 0; n < TOPE_DE_ENUMERACION + 12 && ocurrencias.size() < TOPE_DE_ENUMERACION; n++) {
            LocalDate canonica = canonicaDeLaOcurrencia(programado, n);
            if (canonica.isAfter(limite)) {
                break;
            }
            if (canonica.isBefore(ancla)) {
                continue;
            }
            ocurrencias.add(new Ocurrencia(
                    canonica,
                    aplicarPoliticaDeFinDeSemana(canonica, programado.getWeekendPolicy()),
                    claveDeLaOcurrencia(programado.getFrequency(), canonica)));
        }
        return ocurrencias;
    }

    /**
     * La ocurrencia cuya clave es exactamente {@code clave}, o null si esa
     * clave no pertenece a este programado.
     *
     * Existe para que materializar una propuesta no sea una via para inventar
     * movimientos: el cliente manda una clave y aqui se comprueba que el
     * calendario del programado la contiene de verdad.
     */
    public static Ocurrencia buscarPorClave(ScheduledMovement programado, LocalDate clave) {
        if (programado == null || clave == null || programado.getFrequency() == null) {
            return null;
        }
        LocalDate hasta = esSubMensual(programado.getFrequency())
                ? clave
                : YearMonth.from(clave).atEndOfMonth();
        return ocurrenciasHasta(programado, hasta).stream()
                .filter(o -> clave.equals(o.clave()))
                .findFirst()
                .orElse(null);
    }

    /**
     * La clave con la que se reconoce una ocurrencia ya generada.
     *
     * Se parte en dos porque el usuario dice dos cosas distintas segun la
     * frecuencia:
     *
     *  · Mensual y mas espaciadas: "esto pasa una vez ESTE MES". Que dia es un
     *    detalle que puede cambiar de opinion, asi que la clave es el mes. Si
     *    la clave fuera el dia exacto, cambiar el cobro del arriendo del 5 al
     *    25 generaria un SEGUNDO arriendo ese mes. Eso ya paso una vez y por
     *    eso existe LasRecurrenciasNoSeDuplicanTest.
     *  · Diaria, semanal y quincenal: caben varias en el mismo mes, asi que el
     *    mes no distingue nada. Ahi la clave es la fecha canonica: es lo unico
     *    que separa una ocurrencia de la siguiente.
     *
     * En los dos casos la clave sale del ANCLA, no de la fecha del movimiento:
     * mover el pendiente de sitio no puede hacer que se vuelva a generar.
     */
    public static LocalDate claveDeLaOcurrencia(Frequency frecuencia, LocalDate canonica) {
        return esSubMensual(frecuencia) ? canonica : YearMonth.from(canonica).atDay(1);
    }

    /**
     * La ventana de fechas que cubre una clave. Solo se usa para reconocer las
     * ocurrencias GENERADAS ANTES de que existiera periodStart, que lo tienen
     * nulo y solo se pueden identificar por su fecha.
     */
    public static LocalDate[] ventanaDeLaClave(Frequency frecuencia, LocalDate clave) {
        if (esSubMensual(frecuencia)) {
            return new LocalDate[]{clave, clave};
        }
        YearMonth mes = YearMonth.from(clave);
        return new LocalDate[]{mes.atDay(1), mes.atEndOfMonth()};
    }

    /** Sabado o domingo se mueven —o no— segun lo que el programado diga. */
    public static LocalDate aplicarPoliticaDeFinDeSemana(LocalDate fecha, WeekendPolicy politica) {
        if (politica == null || politica == WeekendPolicy.KEEP) {
            return fecha;
        }
        DayOfWeek dia = fecha.getDayOfWeek();
        if (dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY) {
            return fecha;
        }
        return politica == WeekendPolicy.PREVIOUS_BUSINESS_DAY
                ? fecha.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY))
                : fecha.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    public static boolean esSubMensual(Frequency frecuencia) {
        return SUB_MENSUALES.contains(frecuencia);
    }

    // ========================================================================

    /** La ocurrencia numero n contada DESDE EL ANCLA. */
    private static LocalDate canonicaDeLaOcurrencia(ScheduledMovement programado, int n) {
        LocalDate ancla = programado.getStartDate();
        YearMonth mesDelAncla = YearMonth.from(ancla);
        return switch (programado.getFrequency()) {
            case DAILY -> ancla.plusDays(n);
            case WEEKLY -> ancla.plusWeeks(n);
            case BIWEEKLY -> ancla.plusWeeks(2L * n);
            case MONTHLY -> diaDelMes(programado, mesDelAncla.plusMonths(n));
            case BIMONTHLY -> diaDelMes(programado, mesDelAncla.plusMonths(2L * n));
            case QUARTERLY -> diaDelMes(programado, mesDelAncla.plusMonths(3L * n));
            case SEMIANNUAL -> diaDelMes(programado, mesDelAncla.plusMonths(6L * n));
            case ANNUAL -> diaDelMes(programado, mesDelAncla.plusMonths(12L * n));
        };
    }

    /**
     * Que dia de ese mes le toca a un programado mensual o mas espaciado.
     *
     * Manda el patron "el tercer viernes" si esta puesto; si no, el dia del mes
     * elegido; y si tampoco, el dia del ancla. Siempre recortado al ultimo dia
     * del mes: el 31 en febrero es el 28, y en marzo vuelve a ser el 31 porque
     * se cuenta desde el ancla y no desde la ocurrencia anterior.
     */
    private static LocalDate diaDelMes(ScheduledMovement programado, YearMonth mes) {
        if (programado.getWeekOfMonth() != null && programado.getDayOfWeek() != null) {
            return elEnesimoDiaDeLaSemana(mes, programado.getWeekOfMonth(),
                    DayOfWeek.of(programado.getDayOfWeek()));
        }
        int dia = programado.getDayOfMonth() != null
                ? programado.getDayOfMonth()
                : programado.getStartDate().getDayOfMonth();
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    /**
     * "El tercer viernes de agosto". Asi se paga la nomina en Colombia y es la
     * unica forma de decirlo: no es el dia 15 ni el 21, es el tercer viernes.
     *
     * El ordinal 5 significa "el ultimo": pedir el quinto viernes de un mes que
     * solo tiene cuatro devuelve el cuarto, no el primero de septiembre —que es
     * lo que hace TemporalAdjusters.dayOfWeekInMonth por su cuenta y seria un
     * movimiento en el mes equivocado.
     */
    private static LocalDate elEnesimoDiaDeLaSemana(YearMonth mes, int ordinal, DayOfWeek dia) {
        LocalDate primero = mes.atDay(1).with(TemporalAdjusters.nextOrSame(dia));
        LocalDate candidata = primero.plusWeeks(ordinal - 1L);
        return YearMonth.from(candidata).equals(mes)
                ? candidata
                : mes.atEndOfMonth().with(TemporalAdjusters.previousOrSame(dia));
    }
}
