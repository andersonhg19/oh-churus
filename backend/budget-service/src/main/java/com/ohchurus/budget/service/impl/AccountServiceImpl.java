package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.input.AccountSaveDTO;
import com.ohchurus.budget.dto.input.ReconcileDTO;
import com.ohchurus.budget.dto.output.ResultAccountDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Account;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.AccountKind;
import com.ohchurus.budget.repository.AccountRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.AccountService;
import com.ohchurus.budget.util.CategoriasDelSistema;
import com.ohchurus.budget.util.ControlAcceso;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    /** El nombre con el que la migracion V4 recogio lo que no estaba clasificado. */
    public static final String SIN_ASIGNAR = "Sin asignar";

    private final AccountRepository cuentas;
    private final MovementRepository movimientos;
    private final HouseholdServiceImpl hogares;
    private final SaldoDeCuenta saldos;
    private final CategoriasDelSistema categoriasDelSistema;
    private final ControlAcceso acceso;

    public AccountServiceImpl(AccountRepository cuentas,
                              MovementRepository movimientos,
                              HouseholdServiceImpl hogares,
                              SaldoDeCuenta saldos,
                              CategoriasDelSistema categoriasDelSistema,
                              ControlAcceso acceso) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.hogares = hogares;
        this.saldos = saldos;
        this.categoriasDelSistema = categoriasDelSistema;
        this.acceso = acceso;
    }

    // ========================================================================
    // Propiedad

    /**
     * Se responde "no existe" y no "no puedes", igual que en el resto del
     * servicio: contestar "no puedes" confirma que ese id existe, y con ids
     * consecutivos eso deja contar cuantas cuentas tiene otra persona.
     */
    private boolean puedoTocar(Account cuenta) {
        return cuenta != null
                && (acceso.esMio(cuenta.getUserId()) || acceso.esDeMiHogar(cuenta.getHouseholdId()));
    }

    // ========================================================================

    @Override
    public ResultDTO saveAndUpdate(AccountSaveDTO dto) {
        try {
            return dto.getId() != null ? actualizar(dto) : crear(dto);
        } catch (Exception e) {
            log.error("Error saving account: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving account", 500);
        }
    }

    private ResultDTO crear(AccountSaveDTO dto) {
        Long yo = acceso.usuarioActual();
        if (yo == null) return new ResultDTO(false, "Account not found", 404);

        /* Compartir una cuenta con un hogar al que no perteneces seria meterle
           a otra pareja una cuenta dentro de sus cifras. */
        if (dto.getHouseholdId() != null && !acceso.esDeMiHogar(dto.getHouseholdId())) {
            return new ResultDTO(false, "Household not found", 404);
        }

        Account cuenta = cuentas.save(Account.builder()
                .userId(yo)
                .name(dto.getName())
                .kind(dto.getKind())
                .icon(dto.getIcon())
                .color(dto.getColor())
                .householdId(dto.getHouseholdId())
                .isDefault(false)
                .active(true)
                .build());

        crearAperturaSiHace(cuenta, dto);

        log.info("Account created: id={} for user {}", cuenta.getId(), cuenta.getUserId());
        return new ResultDTO(aDTO(cuenta));
    }

    /**
     * La apertura es un movimiento, no un campo. Ver Account y Movement.
     *
     * Un saldo inicial de cero no genera movimiento: una cuenta que empieza
     * vacia ya vale cero sin necesidad de decirlo, y anotarlo solo ensuciaria
     * la lista con una linea de 0 que no explica nada.
     */
    private void crearAperturaSiHace(Account cuenta, AccountSaveDTO dto) {
        BigDecimal inicial = dto.getOpeningBalance();
        if (inicial == null || inicial.compareTo(BigDecimal.ZERO) == 0) return;

        boolean aFavor = inicial.compareTo(BigDecimal.ZERO) > 0;
        Category categoria = categoriasDelSistema.apertura(cuenta.getUserId(), aFavor);

        movimientos.save(Movement.builder()
                .userId(cuenta.getUserId())
                .accountId(cuenta.getId())
                .categoryId(categoria.getId())
                .date(dto.getOpeningDate() != null ? dto.getOpeningDate() : LocalDate.now())
                .amount(inicial.abs())
                .description("Saldo inicial de " + cuenta.getName())
                .isOpening(true)
                .confirmed(true)
                .active(true)
                .build());
    }

    private ResultDTO actualizar(AccountSaveDTO dto) {
        Optional<Account> existente = cuentas.findByIdAndActiveTrue(dto.getId());
        if (existente.isEmpty() || !puedoTocar(existente.get())) {
            return new ResultDTO(false, "Account not found", 404);
        }
        if (dto.getHouseholdId() != null && !acceso.esDeMiHogar(dto.getHouseholdId())) {
            return new ResultDTO(false, "Household not found", 404);
        }

        Account cuenta = existente.get();
        /* El dueno NUNCA cambia al actualizar. Es la misma regla que en
           movimientos y categorias, y viene del mismo agujero: mandar el id de
           algo ajeno bastaba para quedarse con ello. */
        cuenta.setName(dto.getName());
        cuenta.setKind(dto.getKind());
        cuenta.setIcon(dto.getIcon());
        cuenta.setColor(dto.getColor());
        cuenta.setHouseholdId(dto.getHouseholdId());
        /* openingBalance y openingDate se ignoran aqui a proposito: ver el
           comentario del DTO. Reescribir el pasado en silencio, no. */

        Account guardada = cuentas.save(cuenta);
        log.info("Account updated: id={}", guardada.getId());
        return new ResultDTO(aDTO(guardada));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getById(Long id) {
        Optional<Account> cuenta = cuentas.findByIdAndActiveTrue(id);
        if (cuenta.isEmpty() || !puedoTocar(cuenta.get())) {
            return new ResultDTO(false, "Account not found", 404);
        }
        return new ResultDTO(aDTO(cuenta.get()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getAll() {
        Long yo = acceso.usuarioActual();
        if (yo == null) return new ResultDTO(new ArrayList<>());

        List<Long> misHogares = hogares.getHouseholdIds(yo);
        /* Una lista vacia en un IN de JPQL revienta en algunos dialectos, y
           esa es exactamente la situacion de quien no tiene hogar: la mayoria
           al empezar. */
        List<Account> visibles = misHogares == null || misHogares.isEmpty()
                ? cuentas.findByUserIdAndActiveTrueOrderByNameAsc(yo)
                : cuentas.findVisibles(yo, misHogares);

        List<ResultAccountDTO> lista = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Account cuenta : visibles) {
            ResultAccountDTO dto = aDTO(cuenta);
            lista.add(dto);
            /*
             * El patrimonio se suma A SECAS, sin mirar la clase de cuenta, y
             * esto merece un parrafo porque el primer intento lo hizo mal.
             *
             * La tentacion es "sumo las propias y RESTO los pasivos". Es
             * exactamente el error: el saldo de un pasivo YA es negativo,
             * porque cargar la tarjeta es un gasto y los gastos restan. Al
             * restarlo otra vez se cambia de signo y deber 400.000 te hacia
             * 400.000 mas rico. Lo cazo la prueba del patrimonio.
             *
             * La clase de cuenta no es aritmetica: es como se PRESENTA. En un
             * pasivo la pantalla dice "debes 400.000" en lugar de "tienes
             * -400.000". La suma es la misma para todas.
             */
            total = total.add(dto.getBalance());
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("list", lista);
        respuesta.put("netWorth", total);
        return new ResultDTO(respuesta);
    }

    @Override
    public ResultDTO delete(Long id) {
        Optional<Account> existente = cuentas.findByIdAndActiveTrue(id);
        if (existente.isEmpty() || !puedoTocar(existente.get())) {
            return new ResultDTO(false, "Account not found", 404);
        }

        Account cuenta = existente.get();

        /* Dos negativas, y las dos evitan dejar movimientos huerfanos.
           Un movimiento cuya cuenta ya no existe no aparece en ningun saldo
           pero sigue contando en el presupuesto: descuadre invisible, que es
           la peor clase. */
        if (Boolean.TRUE.equals(cuenta.getIsDefault())) {
            return new ResultDTO(false,
                    "No se puede borrar la cuenta por defecto: es donde caen los movimientos "
                            + "que no indican cuenta. Renombrala si quieres, pero tiene que existir.", 400);
        }
        if (movimientos.existsByAccountIdAndActiveTrue(cuenta.getId())) {
            return new ResultDTO(false,
                    "Esta cuenta todavia tiene movimientos. Muevelos a otra cuenta o borralos "
                            + "antes, para que no queden sin sitio.", 400);
        }

        cuenta.setActive(false);
        cuentas.save(cuenta);
        log.info("Account deleted (soft): id={}", cuenta.getId());
        return new ResultDTO(true, "Account deleted successfully", 0);
    }

    // ========================================================================
    // Conciliacion

    @Override
    public ResultDTO reconcile(ReconcileDTO dto) {
        Optional<Account> existente = cuentas.findByIdAndActiveTrue(dto.getAccountId());
        if (existente.isEmpty() || !puedoTocar(existente.get())) {
            return new ResultDTO(false, "Account not found", 404);
        }

        Account cuenta = existente.get();
        LocalDate fecha = dto.getDate() != null ? dto.getDate() : LocalDate.now();
        BigDecimal segunLaApp = saldos.confirmadoHasta(cuenta.getId(), fecha);
        BigDecimal segunElBanco = dto.getRealBalance();
        BigDecimal diferencia = segunElBanco.subtract(segunLaApp);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("accountId", cuenta.getId());
        respuesta.put("date", fecha);
        respuesta.put("appBalance", segunLaApp);
        respuesta.put("realBalance", segunElBanco);
        respuesta.put("difference", diferencia);

        if (diferencia.compareTo(BigDecimal.ZERO) == 0) {
            respuesta.put("adjusted", false);
            respuesta.put("message", "La cuenta cuadra.");
            return new ResultDTO(respuesta);
        }

        /* Sin apply se limita a informar. Que la app cree sola un ajuste solo
           porque preguntaste seria inventar un movimiento que nadie pidio, y
           casi siempre la respuesta correcta no es ajustar: es acordarse del
           gasto que falta por anotar. */
        if (!Boolean.TRUE.equals(dto.getApply())) {
            respuesta.put("adjusted", false);
            respuesta.put("message", diferencia.signum() > 0
                    ? "El banco tiene mas de lo que dice la app: te falta anotar algun ingreso."
                    : "El banco tiene menos de lo que dice la app: te falta anotar algun gasto.");
            return new ResultDTO(respuesta);
        }

        boolean sobraba = diferencia.signum() > 0;
        Category categoria = categoriasDelSistema.ajuste(cuenta.getUserId(), sobraba);
        Movement ajuste = movimientos.save(Movement.builder()
                .userId(cuenta.getUserId())
                .accountId(cuenta.getId())
                .categoryId(categoria.getId())
                .date(fecha)
                .amount(diferencia.abs())
                .description("Ajuste de conciliacion de " + cuenta.getName())
                .confirmed(true)
                .active(true)
                .build());

        respuesta.put("adjusted", true);
        respuesta.put("adjustmentId", ajuste.getId());
        respuesta.put("message", "Se anoto un ajuste de " + diferencia.abs() + ".");
        log.info("Account reconciled: id={}, difference={}", cuenta.getId(), diferencia);
        return new ResultDTO(respuesta);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO kindList() {
        List<Map<String, String>> lista = new ArrayList<>();
        Arrays.asList(AccountKind.values()).forEach(k -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", k.name());
            item.put("label", k == AccountKind.OWN ? "Propia" : "Pasivo (deuda)");
            lista.add(item);
        });
        return new ResultDTO(lista);
    }

    // ========================================================================

    private ResultAccountDTO aDTO(Account cuenta) {
        return ResultAccountDTO.builder()
                .id(cuenta.getId())
                .userId(cuenta.getUserId())
                .name(cuenta.getName())
                .kind(cuenta.getKind() != null ? cuenta.getKind().name() : null)
                .icon(cuenta.getIcon())
                .color(cuenta.getColor())
                .householdId(cuenta.getHouseholdId())
                .isDefault(cuenta.getIsDefault())
                .balance(saldos.confirmado(cuenta.getId()))
                .projectedBalance(saldos.proyectado(cuenta.getId()))
                .build();
    }

    /**
     * La cuenta donde caen los movimientos que no dicen en cual ocurrieron.
     *
     * La crea si no existe. Eso pasa con un usuario nuevo, que no vivio la
     * migracion V4 y por tanto no tiene su "Sin asignar": sin esto, su primer
     * movimiento se quedaria sin cuenta y fuera de todos los saldos.
     */
    /** Acceso puntual por id, para los servicios que necesitan validar una cuenta. */
    public java.util.Optional<Account> porId(Long id) {
        return cuentas.findByIdAndActiveTrue(id);
    }

    public Account porDefecto(Long userId) {
        return cuentas.findByUserIdAndIsDefaultTrueAndActiveTrue(userId)
                .orElseGet(() -> cuentas.save(Account.builder()
                        .userId(userId)
                        .name(SIN_ASIGNAR)
                        .kind(AccountKind.OWN)
                        .icon("help-circle-outline")
                        .color("#90A4AE")
                        .isDefault(true)
                        .active(true)
                        .build()));
    }
}
