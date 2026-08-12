package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.input.ScheduledMovementFilterDTO;
import com.ohchurus.budget.dto.input.ScheduledMovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.dto.output.ResultScheduledMovementDTO;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.mapper.ScheduledMovementMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.service.ScheduledMovementService;
import com.ohchurus.budget.util.PeriodUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ScheduledMovementServiceImpl implements ScheduledMovementService {

    private final AccountServiceImpl cuentas;
    private final ScheduledMovementRepository scheduledMovementRepository;
    private final MovementRepository movementRepository;
    private final CategoryRepository categoryRepository;
    private final ScheduledMovementMapper scheduledMovementMapper;
    private final MovementMapper movementMapper;
    private final HouseholdServiceImpl householdService;
    private final com.ohchurus.budget.util.ControlAcceso acceso;

    public ScheduledMovementServiceImpl(ScheduledMovementRepository scheduledMovementRepository,
                                         MovementRepository movementRepository,
                                         CategoryRepository categoryRepository,
                                         ScheduledMovementMapper scheduledMovementMapper,
                                         MovementMapper movementMapper,
                                         HouseholdServiceImpl householdService,
                                         com.ohchurus.budget.util.ControlAcceso acceso,
                                        AccountServiceImpl cuentas) {
        this.scheduledMovementRepository = scheduledMovementRepository;
        this.movementRepository = movementRepository;
        this.categoryRepository = categoryRepository;
        this.scheduledMovementMapper = scheduledMovementMapper;
        this.movementMapper = movementMapper;
        this.householdService = householdService;
        this.acceso = acceso;
        this.cuentas = cuentas;
    }

    /** Un programado es tuyo, o esta en una categoria compartida de tu hogar. */
    private boolean puedoTocar(ScheduledMovement p) {
        return acceso.puedeVer(p.getUserId(), p.getCategoryId());
    }

    @Override
    public ResultDTO saveAndUpdate(ScheduledMovementSaveDTO dto) {
        try {
            boolean isUpdate = dto.getId() != null;
            return isUpdate ? updateScheduled(dto) : createScheduled(dto);
        } catch (Exception e) {
            log.error("Error saving scheduled movement: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving scheduled movement", 500);
        }
    }

    private ResultDTO createScheduled(ScheduledMovementSaveDTO dto) {
        if (!categoryRepository.findByIdAndActiveTrue(dto.getCategoryId()).isPresent()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        LocalDate endDate = calculateEndDate(dto.getStartDate(), dto.getDurationMonths());

        /* El dueno de lo que se crea es QUIEN LO CREA, no lo que diga el
           cuerpo. Se demostro con trafico real: Ana enviaba
           {"userId": <id de Bruno>} con su propio token y la categoria
           aparecia dentro de la cuenta de Bruno. Las lecturas y los borrados
           ya estaban cerrados; la creacion se habia quedado fuera. */
        ScheduledMovement scheduled = ScheduledMovement.builder()
                .userId(com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId())
                .categoryId(dto.getCategoryId())
                .name(dto.getName())
                .amount(dto.getAmount())
                .frequency(dto.getFrequency())
                .durationMonths(dto.getDurationMonths())
                .startDate(dto.getStartDate())
                .endDate(endDate)
                .dayOfMonth(dto.getDayOfMonth())
                .active(true)
                .build();

        ScheduledMovement saved = scheduledMovementRepository.save(scheduled);
        log.info("Scheduled movement created: id={} name={} for user {}", saved.getId(), saved.getName(), saved.getUserId());
        return new ResultDTO(scheduledMovementMapper.toResultDTO(saved));
    }

    private ResultDTO updateScheduled(ScheduledMovementSaveDTO dto) {
        Optional<ScheduledMovement> existing = scheduledMovementRepository.findByIdAndActiveTrue(dto.getId());
        if (existing.isEmpty()) {
            return new ResultDTO(false, "Scheduled movement not found", 404);
        }

        if (!categoryRepository.findByIdAndActiveTrue(dto.getCategoryId()).isPresent()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        ScheduledMovement scheduled = existing.get();
        /* El dueno NUNCA cambia al actualizar. Antes se reasignaba al userId
           del cuerpo cuando la categoria era personal, asi que mandar el id de
           un programado ajeno bastaba para quedarse con el. */
        scheduled.setCategoryId(dto.getCategoryId());
        scheduled.setName(dto.getName());
        scheduled.setAmount(dto.getAmount());
        scheduled.setFrequency(dto.getFrequency());
        scheduled.setDurationMonths(dto.getDurationMonths());
        scheduled.setStartDate(dto.getStartDate());
        scheduled.setEndDate(calculateEndDate(dto.getStartDate(), dto.getDurationMonths()));
        scheduled.setDayOfMonth(dto.getDayOfMonth());

        ScheduledMovement saved = scheduledMovementRepository.save(scheduled);
        log.info("Scheduled movement updated: id={}", saved.getId());
        return new ResultDTO(scheduledMovementMapper.toResultDTO(saved));
    }

    /*
     * Un credito a 6 meses que empieza el 15 de enero termina el 14 de julio,
     * no el 15: el 15 de julio ya es la cuota numero 7. Antes se devolvia
     * startDate.plusMonths(n) y el programado generaba una ocurrencia de mas.
     */
    private LocalDate calculateEndDate(LocalDate startDate, Integer durationMonths) {
        if (durationMonths == null || startDate == null) {
            return null;
        }
        return startDate.plusMonths(durationMonths).minusDays(1);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getById(Long id) {
        Optional<ScheduledMovement> scheduled = scheduledMovementRepository.findByIdAndActiveTrue(id);
        if (scheduled.isEmpty() || !puedoTocar(scheduled.get())) {
            return new ResultDTO(false, "Scheduled movement not found", 404);
        }
        ResultScheduledMovementDTO dto = scheduledMovementMapper.toResultDTO(scheduled.get());
        categoryRepository.findByIdAndActiveTrue(scheduled.get().getCategoryId()).ifPresent(cat -> {
            dto.setCategoryName(cat.getName());
            dto.setCategoryType(cat.getType() != null ? cat.getType().name() : null);
        });
        return new ResultDTO(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getAll(ScheduledMovementFilterDTO filter) {
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by("name").ascending());

        List<Long> hIds = filter.getUserId() != null
                ? householdService.getHouseholdIds(filter.getUserId())
                : Collections.emptyList();

        Page<ScheduledMovement> page = !hIds.isEmpty()
                ? scheduledMovementRepository.findAllWithFiltersAndHousehold(
                        filter.getUserId(), hIds, filter.getCategoryId(), filter.getFrequency(), pageable)
                : scheduledMovementRepository.findAllWithFilters(
                        filter.getUserId(), filter.getCategoryId(), filter.getFrequency(), pageable);

        List<ResultScheduledMovementDTO> list = page.getContent().stream()
                .map(s -> {
                    ResultScheduledMovementDTO dto = scheduledMovementMapper.toResultDTO(s);
                    if (dto != null) {
                        categoryRepository.findByIdAndActiveTrue(s.getCategoryId()).ifPresent(cat -> {
                            dto.setCategoryName(cat.getName());
                            dto.setCategoryType(cat.getType() != null ? cat.getType().name() : null);
                        });
                    }
                    return dto;
                })
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
    public ResultDTO delete(Long id) {
        Optional<ScheduledMovement> scheduled = scheduledMovementRepository.findByIdAndActiveTrue(id);
        if (scheduled.isEmpty() || !puedoTocar(scheduled.get())) {
            return new ResultDTO(false, "Scheduled movement not found", 404);
        }

        ScheduledMovement entity = scheduled.get();
        entity.setActive(false);
        scheduledMovementRepository.save(entity);

        // Desactivar tambien los pendientes no confirmados generados por este programado
        List<Movement> pendingFromScheduled = movementRepository
                .findByScheduledMovementIdAndConfirmedFalseAndActiveTrue(id);
        for (Movement pending : pendingFromScheduled) {
            pending.setActive(false);
            movementRepository.save(pending);
        }

        log.info("Scheduled movement deleted (soft): id={}, pending cancelled: {}", entity.getId(), pendingFromScheduled.size());
        return new ResultDTO(true, "Scheduled movement deleted successfully", 0);
    }

    /**
     * Genera movimientos pendientes para TODOS los periodos desde startDate del
     * programado hasta el periodo actual. Si pasan meses sin confirmar, se acumulan.
     */
    @Override
    public ResultDTO generatePending(Long userId, int budgetStartDay) {
        try {
            // Include household scheduled movements to avoid duplicates
            List<Long> hIds = householdService.getHouseholdIds(userId);
            List<ScheduledMovement> scheduledList = !hIds.isEmpty()
                    ? scheduledMovementRepository.findHouseholdActive(userId, hIds)
                    : scheduledMovementRepository.findByUserIdAndActiveTrue(userId);
            List<ResultMovementDTO> generated = new ArrayList<>();

            LocalDate today = LocalDate.now();
            LocalDate currentPeriodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, today);
            LocalDate currentPeriodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, currentPeriodStart);

            for (ScheduledMovement scheduled : scheduledList) {
                // Saltar si el programado ya expiro completamente
                if (scheduled.getEndDate() != null && scheduled.getEndDate().isBefore(currentPeriodStart)) {
                    continue;
                }

                // Saltar si aun no ha iniciado
                if (scheduled.getStartDate().isAfter(currentPeriodEnd)) {
                    continue;
                }

                // Determinar desde que periodo empezar a generar
                LocalDate genStart = scheduled.getStartDate();
                LocalDate iterPeriodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, genStart);

                // Iterar periodo por periodo hasta el actual
                while (!iterPeriodStart.isAfter(currentPeriodStart)) {
                    LocalDate iterPeriodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, iterPeriodStart);

                    // Saltar si el programado ya expiro
                    if (scheduled.getEndDate() != null && scheduled.getEndDate().isBefore(iterPeriodStart)) {
                        break;
                    }

                    // Saltar si el startDate del programado es posterior a este periodo
                    if (scheduled.getStartDate().isAfter(iterPeriodEnd)) {
                        iterPeriodStart = advancePeriod(iterPeriodStart, budgetStartDay);
                        continue;
                    }

                    // Verificar si la frecuencia aplica para este periodo
                    if (shouldGenerateForPeriod(scheduled, iterPeriodStart, iterPeriodEnd)) {
                        LocalDate periodoDeLaOcurrencia = periodoCanonico(iterPeriodStart);
                        boolean alreadyExists = movementRepository.existeOcurrenciaDelPeriodo(
                                scheduled.getId(), periodoDeLaOcurrencia, iterPeriodStart, iterPeriodEnd);

                        if (!alreadyExists) {
                            int day = scheduled.getDayOfMonth() != null
                                    ? Math.min(scheduled.getDayOfMonth(), YearMonth.from(iterPeriodStart).lengthOfMonth())
                                    : Math.min(budgetStartDay, YearMonth.from(iterPeriodStart).lengthOfMonth());

                            LocalDate movementDate = LocalDate.of(
                                    iterPeriodStart.getYear(), iterPeriodStart.getMonth(), day);
                            // Fix #2: clamp date within period
                            if (movementDate.isBefore(iterPeriodStart)) movementDate = iterPeriodStart;
                            if (movementDate.isAfter(iterPeriodEnd)) movementDate = iterPeriodEnd;

                            BigDecimal amount = scheduled.getAmount() != null
                                    ? scheduled.getAmount() : BigDecimal.ZERO;

                            Movement movement = Movement.builder()
                                    /* El pendiente es del DUENO del programado,
                                       no de quien refresca el panel. En un hogar
                                       el arriendo de uno acababa a nombre del
                                       otro solo porque abrio la app primero. */
                                    .userId(scheduled.getUserId())
                                    /* Y su cuenta es la del dueno, por lo mismo:
                                       un pendiente sin cuenta no saldria en
                                       ningun saldo pero si en el presupuesto. */
                                    .accountId(cuentas.porDefecto(scheduled.getUserId()).getId())
                                    .categoryId(scheduled.getCategoryId())
                                    .date(movementDate)
                                    .amount(amount)
                                    .description(scheduled.getName())
                                    .scheduledMovementId(scheduled.getId())
                                    .periodStart(periodoDeLaOcurrencia)
                                    .confirmed(false)
                                    .active(true)
                                    .build();

                            Movement saved = movementRepository.save(movement);
                            generated.add(movementMapper.toResultDTO(saved));
                            log.info("Pending movement generated: id={} from scheduled={} for period {}",
                                    saved.getId(), scheduled.getId(), iterPeriodStart);
                        }
                    }

                    iterPeriodStart = advancePeriod(iterPeriodStart, budgetStartDay);
                }
            }

            return new ResultDTO(generated);
        } catch (Exception e) {
            log.error("Error generating pending movements: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error generating pending movements", 500);
        }
    }

    /**
     * El periodo con el que se identifica una ocurrencia, en forma canonica.
     *
     * Cada miembro del hogar elige su propio dia de corte, asi que el dia NO
     * puede formar parte de la clave: Ana con corte 1 y Bruno con corte 15
     * abrian dos periodos distintos para el mismo mes y el arriendo se generaba
     * dos veces. El mes si es el mismo para los dos, porque la fecha de la
     * ocurrencia siempre cae dentro del periodo que empieza ese mes.
     */
    private LocalDate periodoCanonico(LocalDate inicioDelPeriodo) {
        return YearMonth.from(inicioDelPeriodo).atDay(1);
    }

    private LocalDate advancePeriod(LocalDate periodStart, int budgetStartDay) {
        return PeriodUtils.getStartOfPeriod(budgetStartDay,
                PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart).plusDays(1));
    }

    private boolean shouldGenerateForPeriod(ScheduledMovement scheduled, LocalDate periodStart, LocalDate periodEnd) {
        Frequency freq = scheduled.getFrequency();
        LocalDate start = scheduled.getStartDate();

        long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(
                YearMonth.from(start), YearMonth.from(periodStart));

        if (monthsBetween < 0) {
            return false;
        }

        return switch (freq) {
            case DAILY, WEEKLY, BIWEEKLY -> true;
            case MONTHLY -> true;
            case BIMONTHLY -> monthsBetween % 2 == 0;
            case QUARTERLY -> monthsBetween % 3 == 0;
            case SEMIANNUAL -> monthsBetween % 6 == 0;
            case ANNUAL -> monthsBetween % 12 == 0;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO frequencyList() {
        List<Map<String, String>> frequencies = new ArrayList<>();
        for (Frequency freq : Frequency.values()) {
            Map<String, String> map = new HashMap<>();
            map.put("key", freq.name());
            map.put("name", freq.name());
            frequencies.add(map);
        }
        return new ResultDTO(frequencies);
    }
}
