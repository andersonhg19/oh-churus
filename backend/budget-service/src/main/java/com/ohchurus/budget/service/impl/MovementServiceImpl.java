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
    private final AccountServiceImpl cuentas;
    private final com.ohchurus.budget.repository.AccountRepository cuentaRepo;
    private final RepartoServiceImpl repartos;
    private final RepartoDeGastos reparto;

    public MovementServiceImpl(MovementRepository movementRepository,
                               CategoryRepository categoryRepository,
                               MovementMapper movementMapper,
                               HouseholdServiceImpl householdService,
                               ControlAcceso acceso,
                               AccountServiceImpl cuentas,
                               com.ohchurus.budget.repository.AccountRepository cuentaRepo,
                               RepartoServiceImpl repartos,
                               RepartoDeGastos reparto) {
        this.movementRepository = movementRepository;
        this.categoryRepository = categoryRepository;
        this.movementMapper = movementMapper;
        this.householdService = householdService;
        this.acceso = acceso;
        this.cuentas = cuentas;
        this.cuentaRepo = cuentaRepo;
        this.repartos = repartos;
        this.reparto = reparto;
    }

    /**
     * En que cuenta cae este movimiento.
     *
     * Tres reglas, en orden:
     *   1. La que diga el DTO, SI es suya. Aceptar un accountId ajeno metería
     *      el movimiento en la cuenta de otra persona y le descuadraría el
     *      saldo — el mismo agujero de identidad de siempre, con otra columna.
     *   2. Si no dice nada, la cuenta por defecto ("Sin asignar").
     *   3. Nunca null. Un movimiento sin cuenta no aparece en ningún saldo
     *      pero sí en el presupuesto: descuadre invisible.
     */
    private Long cuentaPara(Long cuentaPedida, Long dueno) {
        if (cuentaPedida != null) {
            java.util.Optional<com.ohchurus.budget.entity.Account> c =
                    cuentaRepo.findByIdAndActiveTrue(cuentaPedida);
            if (c.isPresent()
                    && (acceso.esMio(c.get().getUserId()) || acceso.esDeMiHogar(c.get().getHouseholdId()))) {
                return c.get().getId();
            }
        }
        return cuentas.porDefecto(dueno).getId();
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
        cuentaCacheTL.remove();
        try {
            boolean isUpdate = dto.getId() != null;
            return isUpdate ? updateMovement(dto) : createMovement(dto);
        } catch (Exception e) {
            log.error("Error saving movement: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving movement", 500);
        }
    }

    private ResultDTO createMovement(MovementSaveDTO dto) {
        if (!categoryRepository.findByIdAndActiveTrue(dto.getCategoryId()).isPresent()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        // Validate parent and depth
        if (dto.getParentMovementId() != null) {
            Optional<Movement> parentOpt = movementRepository.findByIdAndActiveTrue(dto.getParentMovementId());
            if (parentOpt.isEmpty()) {
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
        /* El reparto se valida ANTES de escribir el movimiento. Si se validara
           despues habria que deshacer lo escrito, y capturar la excepcion
           dentro del mismo metodo transaccional no revierte: quedaria un gasto
           con el importe total y sin partes, que es justo la mentira que el
           reparto viene a arreglar. Lo cazo su propia prueba. */
        ResultDTO repartoInvalido = repartos.validar(dto.getAmount(), dto.getSplitMode(), dto.getSplits());
        if (repartoInvalido != null) return repartoInvalido;

        Long dueno = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();
        Movement movement = Movement.builder()
                .userId(dueno)
                .accountId(cuentaPara(dto.getAccountId(), dueno))
                .categoryId(dto.getCategoryId())
                .date(dto.getDate())
                .amount(dto.getAmount())
                .description(dto.getDescription())
                .scheduledMovementId(dto.getScheduledMovementId())
                .parentMovementId(dto.getParentMovementId())
                .confirmed(dto.getConfirmed() != null ? dto.getConfirmed() : true)
                .active(true)
                .build();

        Movement saved = movementRepository.save(movement);

        /* Las FILAS del reparto si se escriben despues, porque necesitan el id
           del movimiento. Pero ya esta validado arriba, asi que aqui no puede
           fallar. */
        /* Solo se vuelve a guardar SI hay reparto: aplicar() le pone el modo a
           la entidad y hay que persistirlo. Sin reparto no hay nada que
           reescribir, y un segundo save por cada gasto anotado es un viaje a
           la base que no hace nada. */
        if (dto.getSplitMode() != null) {
            repartos.aplicar(saved, dto.getSplitMode(), dto.getSplits());
            saved = movementRepository.save(saved);
        }

        log.info("Movement created: id={} for user {}", saved.getId(), saved.getUserId());
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(saved)));
    }

    private ResultDTO updateMovement(MovementSaveDTO dto) {
        Optional<Movement> existing = movementRepository.findByIdAndActiveTrue(dto.getId());
        if (existing.isEmpty() || !puedoTocar(existing.get())) {
            return new ResultDTO(false, "Movement not found", 404);
        }

        if (!categoryRepository.findByIdAndActiveTrue(dto.getCategoryId()).isPresent()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        Movement movement = existing.get();
        /* El dueno NUNCA cambia en una actualizacion. Antes se reasignaba al
           userId que viniera en el cuerpo, asi que mandar el id de un
           movimiento ajeno bastaba para quedarse con el. */

        /* Una pata de transferencia no puede cambiar de categoria: la salida
           vive en el bote comun y la entrada en el bolsillo personal, y mover
           una de las dos al otro lado convierte el par en dos salidas (o dos
           entradas) y descuadra el consolidado. */
        if (!esTransferencia(movement)) {
            movement.setCategoryId(dto.getCategoryId());
        }
        movement.setDate(dto.getDate());
        movement.setAmount(dto.getAmount());
        movement.setDescription(dto.getDescription());
        movement.setScheduledMovementId(dto.getScheduledMovementId());
        if (dto.getConfirmed() != null) {
            movement.setConfirmed(dto.getConfirmed());
        }
        /* Cambiar de cuenta SI se permite: apuntaste el almuerzo en efectivo y
           en realidad lo pagaste con la tarjeta. Pasa por el mismo filtro que
           al crear, asi que no se puede mover a la cuenta de otra persona. Si
           el DTO no trae cuenta, se queda en la que estaba en vez de caer a la
           de por defecto: un cliente que no conoce las cuentas no deberia
           poder desclasificar un movimiento sin querer. */
        if (dto.getAccountId() != null) {
            movement.setAccountId(cuentaPara(dto.getAccountId(), movement.getUserId()));
        } else if (movement.getAccountId() == null) {
            movement.setAccountId(cuentas.porDefecto(movement.getUserId()).getId());
        }

        ResultDTO repartoInvalido = repartos.aplicar(movement, dto.getSplitMode(), dto.getSplits());
        if (repartoInvalido != null) return repartoInvalido;

        Movement saved = movementRepository.save(movement);
        propagarALaOtraPata(saved);
        log.info("Movement updated: id={}", saved.getId());
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(saved)));
    }

    private boolean esTransferencia(Movement m) {
        return Boolean.TRUE.equals(m.getIsTransfer());
    }

    /**
     * Una transferencia son DOS movimientos, no uno.
     *
     * Borrar ya desactivaba los dos, pero editar cambiaba una sola pata:
     * corregir una transferencia de 500.000 a 300.000 dejaba el consolidado
     * descuadrado en 200.000 que no existen, y no habia forma de arreglarlo
     * desde la app. Se propaga en la MISMA transaccion, no despues: si falla
     * el guardado de la segunda pata, tampoco se guarda la primera.
     *
     * Se propagan importe y fecha, que es lo que define cuanta plata se movio
     * y cuando. La categoria no, porque cada pata vive en un lado distinto.
     */
    private void propagarALaOtraPata(Movement pata) {
        if (!esTransferencia(pata) || pata.getTransferPairId() == null) {
            return;
        }
        movementRepository.findByIdAndActiveTrue(pata.getTransferPairId()).ifPresent(otra -> {
            otra.setAmount(pata.getAmount());
            otra.setDate(pata.getDate());
            movementRepository.save(otra);
            log.info("Transfer pair kept in sync: id={} follows id={}", otra.getId(), pata.getId());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getById(Long id) {
        categoryCacheTL.remove();
        cuentaCacheTL.remove();
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
        cuentaCacheTL.remove();
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

    @Override
    public ResultDTO confirmWithAmount(Long id, java.math.BigDecimal newAmount) {
        categoryCacheTL.remove();
        cuentaCacheTL.remove();
        Optional<Movement> movement = movementRepository.findByIdAndActiveTrue(id);
        if (movement.isEmpty() || !puedoTocar(movement.get())) {
            return new ResultDTO(false, "Movement not found", 404);
        }

        Movement entity = movement.get();
        if (newAmount != null && newAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
            entity.setAmount(newAmount);
        }
        entity.setConfirmed(true);
        Movement saved = movementRepository.save(entity);
        /* Confirmar con otro importe es la otra puerta por la que se editaba
           una sola pata de la transferencia. */
        propagarALaOtraPata(saved);
        log.info("Movement confirmed: id={}, amount={}", saved.getId(), saved.getAmount());
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(saved)));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getByPeriod(Long userId, LocalDate startDate, LocalDate endDate) {
        categoryCacheTL.remove();
        cuentaCacheTL.remove();
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
        cuentaCacheTL.remove();
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

        // If it's a transfer, delete both pair movements
        if (Boolean.TRUE.equals(entity.getIsTransfer()) && entity.getTransferPairId() != null) {
            movementRepository.findByIdAndActiveTrue(entity.getTransferPairId()).ifPresent(pair -> {
                pair.setActive(false);
                movementRepository.save(pair);
            });
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
        /* Mi parte viaja con el movimiento para que la lista pueda ensenar
           "120.000 (te tocan 40.000)". Sin reparto es igual al importe. */
        dto.setMyShare(dto.getId() == null ? dto.getAmount()
                : reparto.miParte(movementRepository.findById(dto.getId()).orElse(null),
                                  acceso.usuarioActual()));

        /* El nombre de la cuenta viaja con el movimiento por lo mismo que el de
           la categoria: para que la lista se pueda pintar sin una segunda
           llamada por fila. Mismo cache por peticion. */
        if (dto.getAccountId() != null) {
            java.util.Map<Long, com.ohchurus.budget.entity.Account> cache = cuentaCacheTL.get();
            com.ohchurus.budget.entity.Account cuenta = cache.computeIfAbsent(dto.getAccountId(),
                    id -> cuentaRepo.findByIdAndActiveTrue(id).orElse(null));
            if (cuenta != null) {
                dto.setAccountName(cuenta.getName());
            }
        }
        return dto;
    }

    private static final ThreadLocal<java.util.Map<Long, com.ohchurus.budget.entity.Account>> cuentaCacheTL =
            ThreadLocal.withInitial(java.util.HashMap::new);
}
