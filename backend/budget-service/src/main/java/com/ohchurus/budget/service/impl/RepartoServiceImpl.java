package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.input.SettleDTO;
import com.ohchurus.budget.dto.input.SplitInputDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.MovementSplit;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.SplitMode;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.MovementSplitRepository;
import com.ohchurus.budget.service.RepartoService;
import com.ohchurus.budget.util.CalculadoraDeReparto;
import com.ohchurus.budget.util.ControlAcceso;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El reparto de gastos entre personas: la funcionalidad por la que alguien
 * elegiria Oh Churus antes que Actual Budget, que es gratis y mejor en casi
 * todo lo demas.
 *
 * NADIE MAS LO RESUELVE ENTERO. Firefly III documenta compartir usuario y
 * contrasena. YNAB tampoco, y existe un producto de terceros solo para tapar
 * ese hueco. Monarch asume bolsa comun y no calcula deudas. Splitwise calcula
 * deudas de maravilla y no presupuesta. Aqui conviven las dos cosas, que es
 * justo el problema domestico del que nacio la app.
 */
@Slf4j
@Service
@Transactional
public class RepartoServiceImpl implements RepartoService {

    private final MovementRepository movimientos;
    private final MovementSplitRepository partes;
    private final CategoryRepository categorias;
    private final HouseholdMemberRepository miembros;
    private final HouseholdServiceImpl hogares;
    private final RepartoDeGastos reparto;
    private final AccountServiceImpl cuentas;
    private final ControlAcceso acceso;

    public RepartoServiceImpl(MovementRepository movimientos,
                              MovementSplitRepository partes,
                              CategoryRepository categorias,
                              HouseholdMemberRepository miembros,
                              HouseholdServiceImpl hogares,
                              RepartoDeGastos reparto,
                              AccountServiceImpl cuentas,
                              ControlAcceso acceso) {
        this.movimientos = movimientos;
        this.partes = partes;
        this.categorias = categorias;
        this.miembros = miembros;
        this.hogares = hogares;
        this.reparto = reparto;
        this.cuentas = cuentas;
        this.acceso = acceso;
    }

    // ========================================================================
    // Guardar el reparto de un movimiento

    /**
     * Calcula y guarda las partes de un movimiento.
     *
     * Se llama desde MovementServiceImpl al guardar, no desde un endpoint
     * aparte: repartir es parte de anotar el gasto, no un segundo paso. Un
     * flujo de dos pasos garantiza que a la mitad de los gastos se les olvide
     * el segundo.
     *
     * @return null si todo fue bien; un ResultDTO de error si no.
     */
    public ResultDTO aplicar(Movement movimiento, SplitMode modo, List<SplitInputDTO> entradas) {
        ResultDTO problema = validar(movimiento.getAmount(), modo, entradas);
        if (problema != null) return problema;
        guardar(movimiento, modo, entradas);
        return null;
    }

    /**
     * Comprueba que el reparto tiene sentido, SIN tocar nada.
     *
     * Va separado de guardar por un fallo que cazo su propia prueba: al
     * principio se validaba despues de persistir el movimiento y se lanzaba
     * una excepcion para deshacerlo. Capturarla dentro del mismo metodo
     * @Transactional NO revierte —Spring solo marca la transaccion cuando la
     * excepcion cruza el proxy—, asi que un reparto rechazado dejaba el
     * movimiento guardado con el importe total y sin partes: exactamente la
     * mentira que el reparto viene a arreglar.
     *
     * Se podia haber forzado con setRollbackOnly, pero eso convierte la
     * respuesta en un 500 al confirmar. Validar antes de escribir hace que el
     * problema no exista.
     */
    public ResultDTO validar(BigDecimal importe, SplitMode modo, List<SplitInputDTO> entradas) {
        if (modo == null) return null;

        if (entradas == null || entradas.isEmpty()) {
            return new ResultDTO(false, "Un gasto repartido necesita al menos una persona", 400);
        }

        List<Long> participantes = entradas.stream()
                .map(SplitInputDTO::getParticipantId).distinct().collect(Collectors.toList());
        if (participantes.size() != entradas.size()) {
            return new ResultDTO(false, "Hay una persona repetida en el reparto", 400);
        }

        ResultDTO problema = comprobarQueSonDeMiHogar(participantes);
        if (problema != null) return problema;

        if (modo == SplitMode.PERCENT) {
            BigDecimal suma = entradas.stream()
                    .map(e -> e.getValue() == null ? BigDecimal.ZERO : e.getValue())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (suma.compareTo(new BigDecimal("100")) != 0) {
                /* Se rechaza en vez de normalizar. Si alguien escribe 30 y 30,
                   no se sabe si quiso 50/50 o si le falta una persona, y
                   adivinar en un reparto de plata es peor que preguntar. */
                return new ResultDTO(false,
                        "Los porcentajes suman " + suma + " y tienen que sumar 100", 400);
            }
        }

        if (modo == SplitMode.AMOUNT) {
            BigDecimal suma = entradas.stream()
                    .map(e -> e.getValue() == null ? BigDecimal.ZERO : e.getValue())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (importe != null && suma.compareTo(importe) > 0) {
                return new ResultDTO(false,
                        "Las partes suman " + suma + ", mas que el gasto (" + importe + ")", 400);
            }
        }

        return null;
    }

    /** Escribe las partes. Solo se llama con un reparto ya validado. */
    private void guardar(Movement movimiento, SplitMode modo, List<SplitInputDTO> entradas) {
        /* Sin modo no hay reparto: se limpia lo que hubiera. Asi se "deshace"
           un reparto, editando el gasto y quitandolo. */
        if (modo == null) {
            if (partes.existsByMovementIdAndActiveTrue(movimiento.getId())) {
                partes.desactivarDe(movimiento.getId());
            }
            movimiento.setSplitMode(null);
            return;
        }

        List<CalculadoraDeReparto.ParteCalculada> calculadas = CalculadoraDeReparto.repartir(
                movimiento.getAmount(), modo,
                entradas.stream()
                        .map(e -> new CalculadoraDeReparto.ParteDeclarada(
                                e.getParticipantId(), e.getValue()))
                        .collect(Collectors.toList()));

        partes.desactivarDe(movimiento.getId());
        calculadas.forEach(c -> partes.save(MovementSplit.builder()
                .movementId(movimiento.getId())
                .userId(c.userId())
                .shareValue(c.valor())
                .computedAmount(c.importe())
                .active(true)
                .build()));

        movimiento.setSplitMode(modo);
        log.info("Movement {} split {} ways in mode {}", movimiento.getId(), calculadas.size(), modo);
    }

    /**
     * Repartir con alguien exige compartir hogar con esa persona.
     *
     * Sin esto, cualquiera podria meterle a un desconocido una deuda de un
     * millon usando su id: le apareceria en su pantalla de balances sin haber
     * hecho nada. El hogar es la relacion que la app ya usa para decidir quien
     * puede ver que, y es la que se aplica aqui.
     */
    private ResultDTO comprobarQueSonDeMiHogar(List<Long> participantes) {
        Long yo = acceso.usuarioActual();
        if (yo == null) return new ResultDTO(false, "Movement not found", 404);

        Set<Long> conocidos = hogares.getHouseholdIds(yo).stream()
                .flatMap(h -> miembros.findByHouseholdIdAndActiveTrue(h).stream())
                .map(HouseholdMember::getUserId)
                .collect(Collectors.toSet());
        conocidos.add(yo);

        List<Long> extranos = participantes.stream()
                .filter(p -> !conocidos.contains(p))
                .collect(Collectors.toList());

        if (!extranos.isEmpty()) {
            /* No se dice quien sobra: confirmar que un id existe permitiria
               ir probando. Se dice que hacer. */
            return new ResultDTO(false,
                    "Solo puedes repartir un gasto con miembros de tu nucleo familiar. "
                            + "Invitalos primero desde Nucleo Familiar.", 400);
        }
        return null;
    }

    // ========================================================================
    // Balances

    @Override
    @Transactional(readOnly = true)
    public ResultDTO balances() {
        Long yo = acceso.usuarioActual();
        if (yo == null) return new ResultDTO(new ArrayList<>());

        Map<Long, BigDecimal> neto = reparto.balances(yo, movimientosQueMeAfectan(yo));

        List<Map<String, Object>> lista = new ArrayList<>();
        BigDecimal meDeben = BigDecimal.ZERO;
        BigDecimal debo = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal> e : neto.entrySet()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("userId", e.getKey());
            fila.put("net", e.getValue());
            /* El texto va junto al numero y no lo deduce la pantalla: es la
               regla de la casa de que un estado nunca dependa solo del signo
               o del color. */
            fila.put("label", e.getValue().signum() > 0 ? "Te debe" : "Le debes");
            fila.put("amount", e.getValue().abs());
            lista.add(fila);

            if (e.getValue().signum() > 0) meDeben = meDeben.add(e.getValue());
            else debo = debo.add(e.getValue().abs());
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("list", lista);
        respuesta.put("totalOwedToMe", meDeben);
        respuesta.put("totalIOwe", debo);
        respuesta.put("net", meDeben.subtract(debo));
        return new ResultDTO(respuesta);
    }

    /**
     * Los movimientos que pueden generar deuda conmigo: los mios y los de las
     * categorias compartidas de mis hogares.
     *
     * No se acota por periodo a proposito. Una deuda no caduca a fin de mes:
     * si el mercado de marzo sigue sin saldarse, tiene que seguir apareciendo
     * en octubre. Es la diferencia entre esta pantalla y el panel.
     */
    private List<Movement> movimientosQueMeAfectan(Long yo) {
        List<Long> misHogares = hogares.getHouseholdIds(yo);
        if (misHogares == null || misHogares.isEmpty()) {
            return movimientos.findByUserIdAndActiveTrue(yo);
        }
        return movimientos.findParaBalances(yo, misHogares);
    }

    // ========================================================================
    // Liquidar

    @Override
    public ResultDTO settle(SettleDTO dto) {
        Long yo = acceso.usuarioActual();
        if (yo == null) return new ResultDTO(false, "Movement not found", 404);

        if (yo.equals(dto.getWithUserId())) {
            return new ResultDTO(false, "No puedes liquidar contigo mismo", 400);
        }

        ResultDTO problema = comprobarQueSonDeMiHogar(List.of(dto.getWithUserId()));
        if (problema != null) return problema;

        Map<Long, BigDecimal> neto = reparto.balances(yo, movimientosQueMeAfectan(yo));
        BigDecimal saldo = neto.getOrDefault(dto.getWithUserId(), BigDecimal.ZERO);

        /* Saldar el neto entero es lo que se quiere casi siempre; el importe
           es opcional porque a veces se paga a plazos, y obligar a todo o nada
           haria que no se anotara. */
        BigDecimal importe = dto.getAmount() != null ? dto.getAmount().abs() : saldo.abs();
        if (importe.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResultDTO(false, "No hay nada que liquidar con esa persona", 400);
        }

        /*
         * El signo del NETO decide quien paga, no quien pulsa el boton.
         *
         * Si el neto es negativo yo debo, asi que la plata sale de mi cuenta y
         * el movimiento es mio. Si es positivo me deben, y lo que se anota es
         * que ME PAGARON: entra plata. Dejar que el que pulsa decida el sentido
         * permitiria "cobrarse" una deuda que en realidad se tiene.
         */
        boolean yoPago = saldo.signum() < 0;

        Category categoria = categoriaDeLiquidacion(yo, yoPago);
        Long cuenta = cuentaElegida(dto.getAccountId(), yo);

        Movement liquidacion = movimientos.save(Movement.builder()
                .userId(yo)
                .accountId(cuenta)
                .categoryId(categoria.getId())
                .date(dto.getDate() != null ? dto.getDate() : LocalDate.now())
                .amount(importe)
                .description(yoPago ? "Liquidacion pagada" : "Liquidacion recibida")
                .isSettlement(true)
                .settledWithUserId(dto.getWithUserId())
                .confirmed(true)
                .active(true)
                .build());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("settlementId", liquidacion.getId());
        respuesta.put("amount", importe);
        respuesta.put("iPaid", yoPago);
        respuesta.put("message", yoPago
                ? "Anotado: le pagaste " + importe
                : "Anotado: te pagaron " + importe);
        log.info("Settlement {} between {} and {} for {}", liquidacion.getId(), yo,
                dto.getWithUserId(), importe);
        return new ResultDTO(respuesta);
    }

    /**
     * La liquidacion necesita categoria porque todo movimiento la necesita,
     * pero NO es un gasto: Computables.suma() la excluye. La categoria esta
     * solo para que la fila sea valida y para que se pueda encontrar despues.
     */
    private Category categoriaDeLiquidacion(Long yo, boolean yoPago) {
        String nombre = yoPago ? "Liquidacion pagada" : "Liquidacion recibida";
        CategoryType tipo = yoPago ? CategoryType.EXPENSE : CategoryType.INCOME;
        return categorias.findByUserIdAndActiveTrue(yo).stream()
                .filter(c -> nombre.equals(c.getName()) && tipo == c.getType())
                .findFirst()
                .orElseGet(() -> categorias.save(Category.builder()
                        .userId(yo).name(nombre).type(tipo)
                        .icon("swap-horizontal").color("#7E57C2").active(true).build()));
    }

    private Long cuentaElegida(Long pedida, Long yo) {
        if (pedida != null) {
            Optional<com.ohchurus.budget.entity.Account> c = cuentas.porId(pedida);
            if (c.isPresent() && (acceso.esMio(c.get().getUserId())
                    || acceso.esDeMiHogar(c.get().getHouseholdId()))) {
                return c.get().getId();
            }
        }
        return cuentas.porDefecto(yo).getId();
    }
}
