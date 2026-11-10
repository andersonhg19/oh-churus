package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.input.MovementFilterDTO;
import com.ohchurus.budget.dto.input.MovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.util.ControlAcceso;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.MovementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class MovementServiceImpl implements MovementService {

    private final MovementRepository movementRepository;
    private final CategoryRepository categoryRepository;
    private final MovementMapper movementMapper;
    private final HouseholdServiceImpl householdService;
    private final ControlAcceso acceso;
    private final com.ohchurus.budget.repository.CardRepository tarjetas;
    private final com.ohchurus.budget.repository.ScheduledMovementRepository programados;

    public MovementServiceImpl(MovementRepository movementRepository,
                               CategoryRepository categoryRepository,
                               MovementMapper movementMapper,
                               HouseholdServiceImpl householdService,
                               ControlAcceso acceso,
                               com.ohchurus.budget.repository.CardRepository tarjetas,
                               com.ohchurus.budget.repository.ScheduledMovementRepository programados) {
        this.movementRepository = movementRepository;
        this.categoryRepository = categoryRepository;
        this.movementMapper = movementMapper;
        this.householdService = householdService;
        this.acceso = acceso;
        this.tarjetas = tarjetas;
        this.programados = programados;
    }

    /*
     * Se responde "no existe" y no "no puedes" a proposito: contestar "no
     * puedes" confirma que ese id existe, y con ids consecutivos eso permite
     * averiguar cuantos movimientos tiene otra persona.
     */
    private boolean puedoTocar(Movement m) {
        return acceso.puedeVer(m.getUserId(), m.getCategoryId());
    }

    @Override
    public ResultDTO saveAndUpdate(MovementSaveDTO dto) {
        categoryCacheTL.remove();
        try {
            boolean isUpdate = dto.getId() != null;
            return isUpdate ? updateMovement(dto) : createMovement(dto);
        } catch (Exception e) {
            /* Atrapar la excepcion dentro del metodo transaccional NO revierte:
               lo ya escrito se confirmaba igual. */
            com.ohchurus.budget.util.Transacciones.revertir();
            log.error("Error saving movement: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving movement", 500);
        }
    }

    private ResultDTO createMovement(MovementSaveDTO dto) {
        /* Solo en categorias que puedo ver: con el id de una categoria privada
           de la pareja se le podia anotar un gasto en su presupuesto. */
        if (categoryRepository.findByIdAndActiveTrue(dto.getCategoryId())
                .filter(acceso::puedeVerCategoria).isEmpty()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        // Validate parent and depth
        if (dto.getParentMovementId() != null) {
            Optional<Movement> parentOpt = movementRepository.findByIdAndActiveTrue(dto.getParentMovementId());
            if (parentOpt.isEmpty() || !puedoTocar(parentOpt.get())) {
                return new ResultDTO(false, "Parent movement not found", 404);
            }
            Movement parent = parentOpt.get();
            // Check depth: if parent has a parent, we're at level 2 (max)
            if (parent.getParentMovementId() != null) {
                // Check if grandparent also has a parent (would be level 3 = too deep)
                Optional<Movement> grandparent = movementRepository.findByIdAndActiveTrue(parent.getParentMovementId());
                if (grandparent.isPresent() && grandparent.get().getParentMovementId() != null) {
                    return new ResultDTO(false, "Maximum nesting depth exceeded (max 3 levels)", 400);
                }
            }
            // Child inherits category from parent
            dto.setCategoryId(parent.getCategoryId());
        }

        /* El dueno de lo que se crea es QUIEN LO CREA, no lo que diga el
           cuerpo. Se demostro con trafico real: Ana enviaba
           {"userId": <id de Bruno>} con su propio token y la categoria
           aparecia dentro de la cuenta de Bruno. Las lecturas y los borrados
           ya estaban cerrados; la creacion se habia quedado fuera. */
        ResultDTO fueraDeRango = validarFechaYMonto(dto);
        if (fueraDeRango != null) return fueraDeRango;
        ResultDTO programadoAjeno = validarProgramado(dto.getScheduledMovementId());
        if (programadoAjeno != null) return programadoAjeno;
        ResultDTO origenInvalido = validarOrigen(dto.getFundedFromCategoryId(), dto.getCategoryId());
        if (origenInvalido != null) return origenInvalido;
        ResultDTO noAlcanza = validarSaldoDelAhorro(origen(dto.getFundedFromCategoryId()), dto.getAmount(), null,
                com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId());
        if (noAlcanza != null) return noAlcanza;
        ResultDTO tarjetaInvalida = validarTarjeta(dto.getCardId(), dto.getFundedFromCategoryId(), dto.getCategoryId());
        if (tarjetaInvalida != null) return tarjetaInvalida;

        Long dueno = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();
        Long tarjeta = idONulo(dto.getCardId());
        int cuotas = tarjeta != null && dto.getInstallments() != null ? dto.getInstallments() : 1;
        if (cuotas > 1) {
            if (dto.getParentMovementId() != null) {
                return new ResultDTO(false, "Un sub-gasto no se divide en cuotas", 400);
            }
            return crearCompraACuotas(dto, dueno, tarjeta, cuotas);
        }
        Movement movement = Movement.builder()
                .userId(dueno)
                .categoryId(dto.getCategoryId())
                .date(dto.getDate())
                .amount(dto.getAmount())
                .description(dto.getDescription())
                .scheduledMovementId(dto.getScheduledMovementId())
                .parentMovementId(dto.getParentMovementId())
                .confirmed(dto.getConfirmed() != null ? dto.getConfirmed() : true)
                .fundedFromCategoryId(origen(dto.getFundedFromCategoryId()))
                .cardId(tarjeta)
                .installmentNumber(tarjeta != null ? 1 : null)
                .installmentTotal(tarjeta != null ? 1 : null)
                .active(true)
                .build();

        Movement saved = movementRepository.save(movement);

        log.info("Movement created: id={} for user {}", saved.getId(), saved.getUserId());
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(saved)));
    }

    private ResultDTO updateMovement(MovementSaveDTO dto) {
        Optional<Movement> existing = movementRepository.findByIdAndActiveTrue(dto.getId());
        if (existing.isEmpty() || !puedoTocar(existing.get())) {
            return new ResultDTO(false, "Movement not found", 404);
        }

        /* Solo en categorias que puedo ver: con el id de una categoria privada
           de la pareja se le podia anotar un gasto en su presupuesto. */
        if (categoryRepository.findByIdAndActiveTrue(dto.getCategoryId())
                .filter(acceso::puedeVerCategoria).isEmpty()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        Movement movement = existing.get();
        Long categoriaFinal = dto.getCategoryId();
        ResultDTO fueraDeRango = validarFechaYMonto(dto);
        if (fueraDeRango != null) return fueraDeRango;
        if (dto.getScheduledMovementId() != null) {
            ResultDTO programadoAjeno = validarProgramado(dto.getScheduledMovementId());
            if (programadoAjeno != null) return programadoAjeno;
        }
        /* Un gasto de la pareja no se puede mover a MI categoria privada: ella
           dejaria de verlo y yo veria su plata en mi presupuesto (QA, ola 7.1). */
        Category destino = categoryRepository.findByIdAndActiveTrue(categoriaFinal).orElse(null);
        if (destino != null && !destino.getId().equals(movement.getCategoryId())
                && destino.getHouseholdId() == null && !destino.getUserId().equals(movement.getUserId())) {
            return new ResultDTO(false, "Category not found", 404);
        }
        Long origenPedido = dto.getFundedFromCategoryId() != null ? dto.getFundedFromCategoryId() : movement.getFundedFromCategoryId();
        ResultDTO origenInvalido = validarOrigen(origenPedido, categoriaFinal);
        if (origenInvalido != null) return origenInvalido;
        BigDecimal yaSalia = origen(origenPedido) != null && origen(origenPedido).equals(movement.getFundedFromCategoryId())
                && Boolean.TRUE.equals(movement.getConfirmed()) ? movement.getAmount() : null;
        ResultDTO noAlcanza = validarSaldoDelAhorro(origen(origenPedido), dto.getAmount(), yaSalia, movement.getUserId());
        if (noAlcanza != null) return noAlcanza;
        Long tarjetaPedida = dto.getCardId() != null ? dto.getCardId() : movement.getCardId();
        ResultDTO tarjetaInvalida = validarTarjeta(tarjetaPedida, origenPedido, categoriaFinal);
        if (tarjetaInvalida != null) return tarjetaInvalida;
        int cuotasActuales = movement.getInstallmentTotal() != null ? movement.getInstallmentTotal() : 1;
        if (dto.getInstallments() != null && dto.getInstallments() != cuotasActuales) {
            return new ResultDTO(false, "Para cambiar las cuotas, borra la compra y anotala de nuevo", 400);
        }
        if (dto.getFundedFromCategoryId() != null) movement.setFundedFromCategoryId(origen(dto.getFundedFromCategoryId()));
        if (dto.getCardId() != null) cambiarTarjeta(movement, idONulo(dto.getCardId()));
        /* El dueno NUNCA cambia en una actualizacion. Antes se reasignaba al
           userId que viniera en el cuerpo, asi que mandar el id de un
           movimiento ajeno bastaba para quedarse con el. */

        movement.setCategoryId(dto.getCategoryId());
        movement.setDate(dto.getDate());
        movement.setAmount(dto.getAmount());
        movement.setDescription(dto.getDescription());
        /* El formulario no reenvia de que programado salio el pendiente, y
           pisarlo con null lo desenganchaba: el siguiente "generar" ya no lo
           encontraba y volvia a crear la misma ocurrencia. Solo se cambia si
           llega un valor. */
        if (dto.getScheduledMovementId() != null) {
            movement.setScheduledMovementId(dto.getScheduledMovementId());
        }
        if (dto.getConfirmed() != null) {
            movement.setConfirmed(dto.getConfirmed());
        }
        Movement saved = movementRepository.save(movement);
        log.info("Movement updated: id={}", saved.getId());
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(saved)));
    }

    /** Fechas y montos que no pueden ser de nadie: el ano 1 o el 9999, o mil billones. */
    private static final java.math.BigDecimal MONTO_MAXIMO = new java.math.BigDecimal("100000000000");

    private ResultDTO validarFechaYMonto(MovementSaveDTO dto) {
        LocalDate f = dto.getDate();
        if (f != null && (f.isBefore(LocalDate.of(2000, 1, 1)) || f.isAfter(LocalDate.now().plusYears(5)))) {
            return new ResultDTO(false, "La fecha tiene que estar entre el 2000 y dentro de cinco anos", 400);
        }
        if (dto.getAmount() != null && dto.getAmount().compareTo(MONTO_MAXIMO) > 0) {
            return new ResultDTO(false, "Ese valor es demasiado grande", 400);
        }
        return null;
    }

    /** Solo se engancha a un programado que yo vea (no al privado de la pareja). */
    private ResultDTO validarProgramado(Long programadoId) {
        if (programadoId == null) return null;
        Optional<com.ohchurus.budget.entity.ScheduledMovement> p = programados.findById(programadoId)
                .filter(x -> acceso.puedeVer(x.getUserId(), x.getCategoryId()));
        return p.isPresent() ? null : new ResultDTO(false, "Scheduled movement not found", 404);
    }

    /**
     * Con el ahorro se paga lo que HAY en el ahorro: antes solo se advertia y
     * se podia dejar el ahorro en negativo (QA, ola 7.1). El saldo es el de
     * quien paga en ese ahorro (lo que metio menos lo que ha pagado con el, de
     * todos los meses): cada uno tiene su cuenta de ahorro, y es la misma cifra
     * que ve en su vista.
     */
    private ResultDTO validarSaldoDelAhorro(Long ahorroId, java.math.BigDecimal monto, java.math.BigDecimal yaSalia, Long dueno) {
        if (ahorroId == null || monto == null) return null;
        LocalDate siempre = LocalDate.of(9999, 12, 31);
        java.math.BigDecimal saldo = java.math.BigDecimal.ZERO;
        for (Movement m : movementRepository.findByCategoryIdAndActiveTrueAndConfirmedTrueAndDateLessThanEqual(ahorroId, siempre)) {
            if (com.ohchurus.budget.util.Computables.suma(m) && java.util.Objects.equals(m.getUserId(), dueno)) saldo = saldo.add(com.ohchurus.budget.util.Computables.importe(m));
        }
        for (Movement m : movementRepository.findByFundedFromCategoryIdAndActiveTrueAndConfirmedTrueAndDateLessThanEqual(ahorroId, siempre)) {
            if (com.ohchurus.budget.util.Computables.suma(m) && java.util.Objects.equals(m.getUserId(), dueno)) saldo = saldo.subtract(com.ohchurus.budget.util.Computables.importe(m));
        }
        if (yaSalia != null) saldo = saldo.add(yaSalia);
        if (monto.compareTo(saldo) > 0) {
            String nombre = categoryRepository.findById(ahorroId).map(Category::getName).orElse("el ahorro");
            return new ResultDTO(false, "En " + nombre + " hay " + saldo.setScale(0, java.math.RoundingMode.DOWN).toPlainString()
                    + ": no alcanza para pagar " + monto.setScale(0, java.math.RoundingMode.DOWN).toPlainString(), 400);
        }
        return null;
    }

    /** 0 en el cuerpo es "ninguno". */
    private static Long idONulo(Long pedido) {
        return pedido == null || pedido == 0L ? null : pedido;
    }

    /**
     * Pagar con tarjeta: la tarjeta tiene que ser MIA (no se le carga una
     * compra a la tarjeta de la pareja), solo para gastos, y no a la vez que
     * "pagado con el ahorro": la plata sale de un sitio o del otro.
     */
    private ResultDTO validarTarjeta(Long pedida, Long ahorro, Long categoriaDelGasto) {
        Long t = idONulo(pedida);
        if (t == null) return null;
        if (origen(ahorro) != null) {
            return new ResultDTO(false, "Se paga con el ahorro o con la tarjeta, no con los dos", 400);
        }
        Optional<com.ohchurus.budget.entity.Card> tarjeta = tarjetas.findByIdAndActiveTrue(t)
                .filter(c -> acceso.esMio(c.getUserId()));
        if (tarjeta.isEmpty()) {
            return new ResultDTO(false, "Card not found", 404);
        }
        Optional<Category> delGasto = categoryRepository.findByIdAndActiveTrue(categoriaDelGasto);
        if (delGasto.isPresent() && delGasto.get().getType() != com.ohchurus.budget.enums.CategoryType.EXPENSE) {
            return new ResultDTO(false, "Solo un gasto se paga con tarjeta", 400);
        }
        return null;
    }

    /** Cambia la tarjeta de la compra entera (todas sus cuotas). */
    private void cambiarTarjeta(Movement m, Long tarjeta) {
        List<Movement> compra = m.getPurchaseId() != null
                ? movementRepository.findByPurchaseIdAndActiveTrue(m.getPurchaseId())
                : List.of(m);
        for (Movement c : compra) {
            c.setCardId(tarjeta);
            if (tarjeta == null) {
                c.setCardPaid(false);
            } else if (c.getInstallmentTotal() == null) {
                c.setInstallmentNumber(1);
                c.setInstallmentTotal(1);
            }
            if (!c.getId().equals(m.getId())) movementRepository.save(c);
        }
    }

    /**
     * Una compra a N cuotas son N gastos de su categoria, uno por mes desde el
     * de la compra: cada mes baja lo libre solo su cuota. El valor se reparte
     * en pesos enteros y lo que sobra de la division va en la primera cuota,
     * para que la suma sea exactamente lo que costo.
     */
    private ResultDTO crearCompraACuotas(MovementSaveDTO dto, Long dueno, Long tarjeta, int n) {
        java.math.BigDecimal total = dto.getAmount();
        java.math.BigDecimal base = total.divide(java.math.BigDecimal.valueOf(n), 0, java.math.RoundingMode.DOWN);
        java.math.BigDecimal resto = total.subtract(base.multiply(java.math.BigDecimal.valueOf(n)));
        Movement primera = null;
        for (int k = 1; k <= n; k++) {
            Movement cuota = Movement.builder()
                    .userId(dueno)
                    .categoryId(dto.getCategoryId())
                    .date(dto.getDate().plusMonths(k - 1L))
                    .amount(k == 1 ? base.add(resto) : base)
                    .description(dto.getDescription())
                    .confirmed(true)
                    .cardId(tarjeta)
                    .installmentNumber(k)
                    .installmentTotal(n)
                    .active(true)
                    .build();
            cuota = movementRepository.save(cuota);
            if (primera == null) primera = cuota;
            cuota.setPurchaseId(primera.getId());
            movementRepository.save(cuota);
        }
        log.info("Card purchase in {} installments: purchase={} user={}", n, primera.getId(), dueno);
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(primera)));
    }

    /** 0 en el cuerpo es "ya no sale del ahorro". */
    private static Long origen(Long pedido) {
        return pedido == null || pedido == 0L ? null : pedido;
    }

    /**
     * Un gasto puede decir que se pago con plata ahorrada, y de que ahorro:
     * debe ser una categoria de gasto que yo vea, distinta de la del gasto (el
     * ahorro no se paga con el ahorro), y el movimiento debe ser un gasto.
     */
    private ResultDTO validarOrigen(Long pedido, Long categoriaDelGasto) {
        Long o = origen(pedido);
        if (o == null) return null;
        if (o.equals(categoriaDelGasto)) {
            return new ResultDTO(false, "El ahorro no se paga con el mismo ahorro", 400);
        }
        Optional<com.ohchurus.budget.entity.Category> ahorro = categoryRepository.findByIdAndActiveTrue(o)
                .filter(acceso::puedeVerCategoria);
        if (ahorro.isEmpty() || ahorro.get().getType() != com.ohchurus.budget.enums.CategoryType.EXPENSE) {
            return new ResultDTO(false, "Savings category not found", 404);
        }
        Optional<com.ohchurus.budget.entity.Category> delGasto = categoryRepository.findByIdAndActiveTrue(categoriaDelGasto);
        if (delGasto.isPresent() && delGasto.get().getType() != com.ohchurus.budget.enums.CategoryType.EXPENSE) {
            return new ResultDTO(false, "Solo un gasto se paga con el ahorro", 400);
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getById(Long id) {
        categoryCacheTL.remove();
        Optional<Movement> movement = movementRepository.findByIdAndActiveTrue(id);
        if (movement.isEmpty() || !puedoTocar(movement.get())) {
            return new ResultDTO(false, "Movement not found", 404);
        }
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(movement.get())));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getAll(MovementFilterDTO filter) {
        categoryCacheTL.remove();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by("date").descending());

        List<Long> householdIds = filter.getUserId() != null
                ? householdService.getHouseholdIds(filter.getUserId())
                : Collections.emptyList();

        Page<Movement> page;
        if (!householdIds.isEmpty()) {
            page = movementRepository.findAllWithFiltersAndHousehold(
                    filter.getUserId(), householdIds, filter.getCategoryId(),
                    filter.getStartDate(), filter.getEndDate(),
                    filter.getConfirmed(), pageable);
        } else {
            page = movementRepository.findAllWithFilters(
                    filter.getUserId(), filter.getCategoryId(),
                    filter.getStartDate(), filter.getEndDate(),
                    filter.getConfirmed(), pageable);
        }

        List<ResultMovementDTO> list = page.getContent().stream()
                .map(movementMapper::toResultDTO)
                .map(this::enrichWithCategory)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        response.put("totalPage", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("list", list);

        return new ResultDTO(response);
    }

    @Override
    public ResultDTO confirm(Long id) {
        return confirmWithAmount(id, null);
    }

    /**
     * "Este mes el gimnasio lo paga Andy." El pendiente pasa a nombre del
     * nuevo responsable, que es lo que lo pone en SU lista de cosas
     * por pagar y lo saca de la del otro.
     *
     * Solo pendientes (lo confirmado ya lo pago alguien) y solo de categorias COMPARTIDAS: un
     * gasto privado no puede caer en la cuenta de alguien que no lo ve.
     */
    @Override
    @Transactional
    public ResultDTO reasignarPagador(Long id, Long nuevoPagador) {
        Optional<Movement> encontrado = movementRepository.findByIdAndActiveTrue(id);
        if (encontrado.isEmpty() || !puedoTocar(encontrado.get())) {
            return new ResultDTO(false, "Movement not found", 404);
        }
        Movement m = encontrado.get();
        if (Boolean.TRUE.equals(m.getConfirmed())) {
            return new ResultDTO(false, "Ya esta confirmado: solo se cambia quien paga lo pendiente", 400);
        }
        Category cat = categoryRepository.findByIdAndActiveTrue(m.getCategoryId()).orElse(null);
        if (cat == null || cat.getHouseholdId() == null) {
            return new ResultDTO(false, "Solo se puede cambiar quien paga en categorias compartidas", 400);
        }
        Long yo = acceso.usuarioActual();
        if (!householdService.companerosDeHogar(yo).contains(nuevoPagador)) {
            return new ResultDTO(false, "Quien paga tiene que ser alguien de tu hogar", 400);
        }
        m.setUserId(nuevoPagador);
        Movement guardado = movementRepository.save(m);
        log.info("Movement {} reassigned to payer {} by {}", id, nuevoPagador, yo);
        return new ResultDTO(movementMapper.toResultDTO(guardado));
    }

    /**
     * LIBERAR plata de un compromiso del plan: "este mes no ahorro, necesito
     * esa plata para unas deudas que no estaban en el plan".
     *
     * El presupuesto NO cambia: la asignacion y el programado siguen diciendo
     * cuanto se queria ahorrar, y el cierre del mes lo compara con lo que de
     * verdad se ahorro. Lo que cambia es que esa plata deja de estar "por
     * pagar" y pasa a lo libre.
     *   · Liberar todo: el pendiente se cierra sin pagarse (queda inactivo y
     *     con lo liberado anotado). No se vuelve a generar: la fecha existe.
     *   · Liberar una parte: el pendiente sigue por el resto, que se confirma
     *     como siempre.
     * Solo lo pendiente: lo confirmado ya salio de una cuenta.
     */
    @Override
    @Transactional
    public ResultDTO liberar(Long id, java.math.BigDecimal monto) {
        Optional<Movement> encontrado = movementRepository.findByIdAndActiveTrue(id);
        if (encontrado.isEmpty() || !puedoTocar(encontrado.get())) {
            return new ResultDTO(false, "Movement not found", 404);
        }
        Movement m = encontrado.get();
        if (Boolean.TRUE.equals(m.getConfirmed())) {
            return new ResultDTO(false, "Ya esta pagado: solo se libera lo que sigue pendiente", 400);
        }
        java.math.BigDecimal pendiente = m.getAmount() != null ? m.getAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal suelta = monto != null ? monto : pendiente;
        if (suelta.signum() <= 0 || suelta.compareTo(pendiente) > 0) {
            return new ResultDTO(false, "Se puede liberar entre 1 y " + pendiente.toPlainString(), 400);
        }
        java.math.BigDecimal antes = m.getReleasedAmount() != null ? m.getReleasedAmount() : java.math.BigDecimal.ZERO;
        m.setReleasedAmount(antes.add(suelta));
        if (suelta.compareTo(pendiente) == 0) {
            m.setActive(false);
        } else {
            m.setAmount(pendiente.subtract(suelta));
        }
        Movement guardado = movementRepository.save(m);
        log.info("Movement {} released {} (now pending {})", id, suelta, guardado.getActive() ? guardado.getAmount() : 0);
        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("released", suelta);
        res.put("totalReleased", guardado.getReleasedAmount());
        res.put("stillPending", Boolean.TRUE.equals(guardado.getActive()) ? guardado.getAmount() : java.math.BigDecimal.ZERO);
        return new ResultDTO(res);
    }

    /**
     * Deshace una liberacion: lo liberado vuelve a quedar por pagar, por lo que
     * estaba planeado. Sirve cuando se libero por error (o al final si se pudo
     * pagar). Solo sobre un pendiente que no se ha pagado.
     */
    @Override
    @Transactional
    public ResultDTO deshacerLiberacion(Long id) {
        Optional<Movement> encontrado = movementRepository.findById(id);
        if (encontrado.isEmpty() || !puedoTocar(encontrado.get())) {
            return new ResultDTO(false, "Movement not found", 404);
        }
        Movement m = encontrado.get();
        java.math.BigDecimal liberado = m.getReleasedAmount();
        if (liberado == null || liberado.signum() <= 0) {
            return new ResultDTO(false, "No tiene plata liberada", 400);
        }
        if (Boolean.TRUE.equals(m.getConfirmed())) {
            return new ResultDTO(false, "Ya esta pagado: no hay liberacion que deshacer", 400);
        }
        /* Liberado del todo: quedo inactivo con el monto de antes y lo liberado
           ya es todo lo planeado. Liberado en parte: sigue activo por el resto. */
        boolean cerrado = !Boolean.TRUE.equals(m.getActive());
        java.math.BigDecimal resto = m.getAmount() != null ? m.getAmount() : java.math.BigDecimal.ZERO;
        m.setAmount(cerrado ? liberado : resto.add(liberado));
        m.setActive(true);
        m.setReleasedAmount(null);
        Movement guardado = movementRepository.save(m);
        log.info("Movement {} release undone: pending again {}", id, guardado.getAmount());
        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("pending", guardado.getAmount());
        return new ResultDTO(res);
    }

    @Override
    public ResultDTO confirmarVarios(java.util.List<Long> ids, java.util.List<java.math.BigDecimal> montos) {
        /* Se valida TODO antes de tocar nada: una lista con un pago ajeno o ya
           borrado al final no puede dejar confirmados los de antes. */
        if (new java.util.HashSet<>(ids).size() != ids.size()) {
            return new ResultDTO(false, "Un pago aparece dos veces", 400);
        }
        for (int i = 0; i < ids.size(); i++) {
            Optional<Movement> m = movementRepository.findByIdAndActiveTrue(ids.get(i));
            if (m.isEmpty() || !puedoTocar(m.get())) {
                return new ResultDTO(false, "Pago no encontrado: " + ids.get(i), 404);
            }
            java.math.BigDecimal monto = montos.get(i);
            if (monto != null && monto.signum() <= 0) {
                return new ResultDTO(false, "El valor de " + m.get().getDescription() + " tiene que ser mayor que cero", 400);
            }
        }
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        int confirmados = 0;
        for (int i = 0; i < ids.size(); i++) {
            /* Lo que ya estaba pagado no se vuelve a contar como "confirmado
               ahora": el total del lote tiene que ser lo que se pago hoy. */
            if (movementRepository.findByIdAndActiveTrue(ids.get(i)).map(Movement::getConfirmed).orElse(false)) continue;
            ResultDTO r = confirmWithAmount(ids.get(i), montos.get(i));
            if (!r.isCorrect()) {
                com.ohchurus.budget.util.Transacciones.revertir();
                return r;
            }
            confirmados++;
            total = total.add(movementRepository.findById(ids.get(i)).map(Movement::getAmount)
                    .orElse(java.math.BigDecimal.ZERO));
        }
        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("confirmed", confirmados);
        res.put("total", total);
        log.info("Batch confirmed {} movements for {}", confirmados, total);
        return new ResultDTO(res);
    }

    @Override
    public ResultDTO confirmWithAmount(Long id, java.math.BigDecimal newAmount) {
        categoryCacheTL.remove();
        Optional<Movement> movement = movementRepository.findByIdAndActiveTrue(id);
        if (movement.isEmpty() || !puedoTocar(movement.get())) {
            return new ResultDTO(false, "Movement not found", 404);
        }

        Movement entity = movement.get();
        /* Un importe cero o negativo se ignoraba y se confirmaba el original
           sin decir nada: quien escribio 0 creia haber registrado otra cosa. */
        if (newAmount != null && newAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return new ResultDTO(false, "El valor tiene que ser mayor que cero", 400);
        }
        if (newAmount != null && newAmount.compareTo(entity.getAmount()) != 0) {
            entity.setAmount(newAmount);
        }
        entity.setConfirmed(true);
        Movement saved = movementRepository.save(entity);
        log.info("Movement confirmed: id={}, amount={}", saved.getId(), saved.getAmount());
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(saved)));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getByPeriod(Long userId, LocalDate startDate, LocalDate endDate) {
        categoryCacheTL.remove();
        List<Long> hIds = householdService.getHouseholdIds(userId);
        List<Movement> movements = !hIds.isEmpty()
                ? movementRepository.findHouseholdByPeriod(userId, hIds, startDate, endDate)
                : movementRepository.findByUserIdAndDateBetweenAndActiveTrue(userId, startDate, endDate);

        List<ResultMovementDTO> list = movements.stream()
                .map(movementMapper::toResultDTO)
                .map(this::enrichWithCategory)
                .collect(Collectors.toList());

        return new ResultDTO(list);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getChildren(Long parentId) {
        categoryCacheTL.remove();
        Optional<Movement> parentOpt = movementRepository.findByIdAndActiveTrue(parentId);
        if (parentOpt.isEmpty() || !puedoTocar(parentOpt.get())) {
            return new ResultDTO(false, "Parent movement not found", 404);
        }

        Movement parent = parentOpt.get();
        List<Movement> children = movementRepository.findByParentMovementIdAndActiveTrue(parentId);

        java.math.BigDecimal childrenTotal = children.stream()
                .map(m -> m.getAmount() != null ? m.getAmount() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        List<ResultMovementDTO> list = children.stream()
                .map(movementMapper::toResultDTO)
                .map(this::enrichWithCategory)
                .collect(Collectors.toList());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("parentId", parent.getId());
        result.put("parentAmount", parent.getAmount());
        result.put("parentDescription", parent.getDescription());
        result.put("childrenTotal", childrenTotal);
        result.put("childrenCount", children.size());
        java.math.BigDecimal parentAmt = parent.getAmount() != null ? parent.getAmount() : java.math.BigDecimal.ZERO;
        result.put("remaining", parentAmt.subtract(childrenTotal));
        result.put("executionPct", parentAmt.compareTo(java.math.BigDecimal.ZERO) > 0
                ? childrenTotal.multiply(new java.math.BigDecimal("100"))
                .divide(parentAmt, 1, java.math.RoundingMode.HALF_UP).doubleValue()
                : 0);
        result.put("children", list);

        return new ResultDTO(result);
    }

    @Override
    public ResultDTO delete(Long id) {
        Optional<Movement> movement = movementRepository.findByIdAndActiveTrue(id);
        if (movement.isEmpty() || !puedoTocar(movement.get())) {
            return new ResultDTO(false, "Movement not found", 404);
        }

        Movement entity = movement.get();

        /* Una compra a cuotas es UNA compra: borrar una cuota borra todas,
           o quedarian cuotas sueltas de algo que ya no existe. */
        if (entity.getPurchaseId() != null) {
            for (Movement cuota : movementRepository.findByPurchaseIdAndActiveTrue(entity.getPurchaseId())) {
                if (!cuota.getId().equals(entity.getId())) {
                    cuota.setActive(false);
                    movementRepository.save(cuota);
                }
            }
        }
        entity.setActive(false);
        movementRepository.save(entity);
        log.info("Movement deleted (soft): id={}", entity.getId());
        return new ResultDTO(true, "Movement deleted successfully", 0);
    }

    // Request-scoped category cache (ThreadLocal to avoid concurrency issues)
    private static final ThreadLocal<java.util.Map<Long, com.ohchurus.budget.entity.Category>> categoryCacheTL = ThreadLocal.withInitial(java.util.HashMap::new);

    private ResultMovementDTO enrichWithCategory(ResultMovementDTO dto) {
        if (dto.getCategoryId() != null) {
            java.util.Map<Long, com.ohchurus.budget.entity.Category> cache = categoryCacheTL.get();
            com.ohchurus.budget.entity.Category cat = cache.computeIfAbsent(dto.getCategoryId(),
                    id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));
            if (cat != null) {
                dto.setCategoryName(cat.getName());
                dto.setCategoryType(cat.getType() != null ? cat.getType().name() : null);
                dto.setCategoryIcon(cat.getIcon());
                dto.setCategoryColor(cat.getColor());
            }
        }
        return dto;
    }

}
