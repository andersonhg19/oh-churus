package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.input.MaterializeOccurrencesDTO;
import com.ohchurus.budget.dto.input.OccurrenceRefDTO;
import com.ohchurus.budget.dto.input.ScheduledMovementFilterDTO;
import com.ohchurus.budget.dto.input.ScheduledMovementSaveDTO;
import com.ohchurus.budget.dto.output.ProposedOccurrenceDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.dto.output.ResultScheduledMovementDTO;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.enums.WeekendPolicy;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.mapper.ScheduledMovementMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.service.ScheduledMovementService;
import com.ohchurus.budget.util.CalendarioDeRecurrencias;
import com.ohchurus.budget.util.CalendarioDeRecurrencias.Ocurrencia;
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
import java.util.*;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ScheduledMovementServiceImpl implements ScheduledMovementService {

    private final AccountServiceImpl cuentas;

    /**
     * Cuantas ocurrencias atrasadas puede materializar el generador solo.
     *
     * Por encima de esto no crea NINGUNA de ese programado: las propone. Cinco
     * es "se me paso un mes de un semanal", que se arregla mirandolo un
     * momento; cincuenta es la app escribiendo por su cuenta un historial que
     * nadie ha revisado. Es la misma regla que la TOLERANCIA del motor del
     * proyecto hermano: por debajo se encadena solo, por encima se pregunta.
     */
    static final int TOPE_DE_MATERIALIZACION = 5;

    /**
     * Cuantas propuestas se devuelven por programado. Las que no quepan salen
     * en el siguiente refresco, cuando las de delante ya esten resueltas: un
     * diario abandonado dos anos son setecientas, y una lista de setecientas no
     * la revisa nadie.
     */
    static final int TOPE_DE_PROPUESTAS_POR_PROGRAMADO = 50;

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

    /**
     * "El tercer viernes" son DOS datos y ninguno vale solo: "el tercero" no
     * dice de que, y "un viernes" no dice cual. Se rechaza en vez de adivinar,
     * porque adivinar aqui es poner la nomina en un dia que nadie eligio.
     */
    private String patronSemanalIncompleto(ScheduledMovementSaveDTO dto) {
        boolean semana = dto.getWeekOfMonth() != null;
        boolean dia = dto.getDayOfWeek() != null;
        if (semana == dia) {
            return null;
        }
        return "El patron 'el tercer viernes' necesita weekOfMonth y dayOfWeek juntos: "
                + (semana ? "falta dayOfWeek" : "falta weekOfMonth");
    }

    private ResultDTO createScheduled(ScheduledMovementSaveDTO dto) {
        if (!categoryRepository.findByIdAndActiveTrue(dto.getCategoryId()).isPresent()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        String patronIncompleto = patronSemanalIncompleto(dto);
        if (patronIncompleto != null) {
            return new ResultDTO(false, patronIncompleto, 400);
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
                .weekOfMonth(dto.getWeekOfMonth())
                .dayOfWeek(dto.getDayOfWeek())
                .weekendPolicy(dto.getWeekendPolicy() != null ? dto.getWeekendPolicy() : WeekendPolicy.KEEP)
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

        String patronIncompleto = patronSemanalIncompleto(dto);
        if (patronIncompleto != null) {
            return new ResultDTO(false, patronIncompleto, 400);
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
        scheduled.setWeekOfMonth(dto.getWeekOfMonth());
        scheduled.setDayOfWeek(dto.getDayOfWeek());
        scheduled.setWeekendPolicy(dto.getWeekendPolicy() != null ? dto.getWeekendPolicy() : WeekendPolicy.KEEP);

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
     * ========================================================================
     * GENERAR LOS PENDIENTES
     * ========================================================================
     *
     * Recorre los programados del usuario y de su hogar y, por cada uno, mira
     * QUE OCURRENCIAS LE TOCABAN desde su ancla hasta el final del periodo
     * actual. Las que aun no existen se crean... salvo que sean demasiadas.
     *
     * LA VERSION ANTERIOR NO HACIA ESTO. Recorria periodos de presupuesto —o
     * sea, meses— y por cada uno preguntaba "¿aplica esta frecuencia?", que
     * solo admite si o no. Con eso DAILY, WEEKLY y BIWEEKLY generaban un
     * movimiento al mes, exactamente igual que MONTHLY: tres de las ocho
     * frecuencias del catalogo eran una etiqueta que no significaba nada.
     * Ahora el calendario lo calcula CalendarioDeRecurrencias desde el ancla.
     *
     * EL TOPE: LA APP NO INVENTA DATOS
     * --------------------------------
     * Si un programado acumula mas de {@link #TOPE_DE_MATERIALIZACION}
     * ocurrencias atrasadas sin generar, NINGUNA de las suyas se crea: se
     * devuelven como propuestas para que la persona las mire. Un diario
     * olvidado tres meses son noventa movimientos, y crearlos en silencio es
     * escribirle a alguien noventa gastos que quiza nunca hizo. Se aceptan
     * despues, todas o unas pocas, por /v1/scheduled/materialize.
     */
    @Override
    public ResultDTO generatePending(Long userId, int budgetStartDay) {
        try {
            // Include household scheduled movements to avoid duplicates
            List<Long> hIds = householdService.getHouseholdIds(userId);
            List<ScheduledMovement> scheduledList = !hIds.isEmpty()
                    ? scheduledMovementRepository.findHouseholdActive(userId, hIds)
                    : scheduledMovementRepository.findByUserIdAndActiveTrue(userId);

            LocalDate hoy = LocalDate.now();
            LocalDate inicioDelPeriodo = PeriodUtils.getStartOfPeriod(budgetStartDay, hoy);
            LocalDate finDeLaVentana = PeriodUtils.getEndOfPeriod(budgetStartDay, inicioDelPeriodo);

            List<ResultMovementDTO> creados = new ArrayList<>();
            List<ProposedOccurrenceDTO> propuestas = new ArrayList<>();
            int totalPropuesto = 0;

            for (ScheduledMovement programado : scheduledList) {
                /* Un programado que TERMINO antes del periodo actual no tiene
                   nada que decir. No se le proponen sus ocurrencias viejas: un
                   credito que se acabo hace dos anos y nunca se genero es
                   historia, no una tarea pendiente, y llenar la pantalla de
                   revisiones de cosas muertas es la forma mas rapida de que
                   nadie vuelva a mirar la pantalla de revisiones. */
                if (programado.getEndDate() != null && programado.getEndDate().isBefore(inicioDelPeriodo)) {
                    continue;
                }

                List<Ocurrencia> faltantes = ocurrenciasQueFaltan(programado, finDeLaVentana);
                if (faltantes.isEmpty()) {
                    continue;
                }

                long atrasadas = faltantes.stream().filter(o -> o.fecha().isBefore(hoy)).count();
                if (atrasadas > TOPE_DE_MATERIALIZACION) {
                    totalPropuesto += faltantes.size();
                    faltantes.stream()
                            .limit(TOPE_DE_PROPUESTAS_POR_PROGRAMADO)
                            .forEach(o -> propuestas.add(comoPropuesta(programado, o, hoy)));
                    log.info("Scheduled movement {} has {} overdue occurrences: proposed, not created",
                            programado.getId(), atrasadas);
                } else {
                    faltantes.forEach(o -> creados.add(movementMapper.toResultDTO(crearPendiente(programado, o))));
                }
            }

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("created", creados);
            respuesta.put("proposals", propuestas);
            respuesta.put("proposalsTotal", totalPropuesto);
            respuesta.put("needsReview", !propuestas.isEmpty());
            return new ResultDTO(respuesta);
        } catch (Exception e) {
            log.error("Error generating pending movements: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error generating pending movements", 500);
        }
    }

    /**
     * Crea las ocurrencias que la persona reviso y acepto.
     *
     * La pareja (scheduledMovementId, periodStart) se vuelve a validar contra
     * el calendario del programado: el cliente senala cual quiere, no dicta que
     * movimiento se escribe. Y se salta en silencio la que ya exista, porque un
     * segundo toque en "Registrar" no puede crear la misma ocurrencia dos veces.
     *
     * Se valida TODO antes de escribir NADA. Si se validara y creara sobre la
     * marcha, una lista con una referencia mala al final dejaria creadas las
     * buenas y devolveria un error: la persona veria "no se pudo" con la mitad
     * del trabajo hecho, que es la peor de las dos respuestas posibles.
     */
    @Override
    public ResultDTO materialize(MaterializeOccurrencesDTO dto) {
        try {
            /* Indexado por id y no por la entidad: ScheduledMovement no define
               equals, asi que como clave de un mapa seria identidad. */
            Map<Long, ScheduledMovement> programados = new LinkedHashMap<>();
            Map<Long, List<Ocurrencia>> aceptadas = new LinkedHashMap<>();

            for (OccurrenceRefDTO referencia : dto.getOccurrences()) {
                Optional<ScheduledMovement> encontrado =
                        scheduledMovementRepository.findByIdAndActiveTrue(referencia.getScheduledMovementId());
                if (encontrado.isEmpty() || !puedoTocar(encontrado.get())) {
                    return new ResultDTO(false, "Scheduled movement not found", 404);
                }

                ScheduledMovement programado = encontrado.get();
                Ocurrencia ocurrencia = CalendarioDeRecurrencias.buscarPorClave(
                        programado, referencia.getPeriodStart());
                if (ocurrencia == null) {
                    return new ResultDTO(false,
                            "La ocurrencia no pertenece a este programado: " + referencia.getPeriodStart(), 400);
                }
                programados.putIfAbsent(programado.getId(), programado);
                aceptadas.computeIfAbsent(programado.getId(), id -> new ArrayList<>()).add(ocurrencia);
            }

            List<ResultMovementDTO> creados = new ArrayList<>();
            for (Map.Entry<Long, List<Ocurrencia>> entrada : aceptadas.entrySet()) {
                ScheduledMovement programado = programados.get(entrada.getKey());
                /* Las que ya existen se saltan en silencio: pulsar dos veces
                   "Registrar" no puede crear la misma ocurrencia dos veces, y
                   la clave repetida dentro de la propia peticion tampoco. */
                Set<LocalDate> yaEstan = yaGeneradas(programado);
                for (Ocurrencia ocurrencia : entrada.getValue()) {
                    if (!yaEstan.add(ocurrencia.clave())) {
                        continue;
                    }
                    creados.add(movementMapper.toResultDTO(crearPendiente(programado, ocurrencia)));
                }
            }

            return new ResultDTO(creados);
        } catch (Exception e) {
            log.error("Error materializing occurrences: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error materializing occurrences", 500);
        }
    }

    /**
     * Las ocurrencias del programado que todavia no tienen movimiento.
     *
     * Se traen de una vez las ya generadas —vivas Y muertas— y se comparan en
     * memoria: un programado diario tiene cientos de ocurrencias y el panel
     * llama a esto cada vez que se abre.
     *
     * Las muertas cuentan a proposito: borrar un pendiente significa "omite
     * esta ocurrencia", no "vuelve a crearmela en el siguiente refresco".
     */
    private List<Ocurrencia> ocurrenciasQueFaltan(ScheduledMovement programado, LocalDate hasta) {
        List<Ocurrencia> todas = CalendarioDeRecurrencias.ocurrenciasHasta(programado, hasta);
        if (todas.isEmpty()) {
            return todas;
        }
        List<Movement> generadas = movementRepository.findByScheduledMovementId(programado.getId());
        Set<LocalDate> claves = clavesDe(generadas);
        Set<LocalDate> sinClave = fechasSinClaveDe(generadas);
        return todas.stream()
                .filter(o -> !claves.contains(o.clave()) && !cubiertaPorUnaFilaVieja(programado, o, sinClave))
                .collect(Collectors.toList());
    }

    private Set<LocalDate> yaGeneradas(ScheduledMovement programado) {
        return clavesDe(movementRepository.findByScheduledMovementId(programado.getId()));
    }

    private Set<LocalDate> clavesDe(List<Movement> generadas) {
        return generadas.stream()
                .map(Movement::getPeriodStart)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<LocalDate> fechasSinClaveDe(List<Movement> generadas) {
        return generadas.stream()
                .filter(m -> m.getPeriodStart() == null && m.getDate() != null)
                .map(Movement::getDate)
                .collect(Collectors.toSet());
    }

    /**
     * Las ocurrencias creadas ANTES de que existiera periodStart lo tienen
     * nulo y solo se pueden reconocer por su fecha. Sin esto, el primer
     * refresco tras el despliegue duplicaria todos los pendientes vivos.
     */
    private boolean cubiertaPorUnaFilaVieja(ScheduledMovement programado, Ocurrencia ocurrencia,
                                            Set<LocalDate> fechasSinClave) {
        if (fechasSinClave.isEmpty()) {
            return false;
        }
        LocalDate[] ventana = CalendarioDeRecurrencias.ventanaDeLaClave(
                programado.getFrequency(), ocurrencia.clave());
        return fechasSinClave.stream()
                .anyMatch(f -> !f.isBefore(ventana[0]) && !f.isAfter(ventana[1]));
    }

    private Movement crearPendiente(ScheduledMovement programado, Ocurrencia ocurrencia) {
        Movement movimiento = Movement.builder()
                /* El pendiente es del DUENO del programado, no de quien
                   refresca el panel. En un hogar el arriendo de uno acababa a
                   nombre del otro solo porque abrio la app primero. */
                .userId(programado.getUserId())
                /* Y su cuenta es la del dueno, por lo mismo: un pendiente sin
                   cuenta no saldria en ningun saldo pero si contaria en el
                   presupuesto. */
                .accountId(cuentas.porDefecto(programado.getUserId()).getId())
                .categoryId(programado.getCategoryId())
                .date(ocurrencia.fecha())
                .amount(programado.getAmount() != null ? programado.getAmount() : BigDecimal.ZERO)
                .description(programado.getName())
                .scheduledMovementId(programado.getId())
                .periodStart(ocurrencia.clave())
                .confirmed(false)
                .active(true)
                .build();

        Movement guardado = movementRepository.save(movimiento);
        log.info("Pending movement generated: id={} from scheduled={} for occurrence {}",
                guardado.getId(), programado.getId(), ocurrencia.clave());
        return guardado;
    }

    private ProposedOccurrenceDTO comoPropuesta(ScheduledMovement programado, Ocurrencia ocurrencia,
                                                LocalDate hoy) {
        ProposedOccurrenceDTO propuesta = new ProposedOccurrenceDTO();
        propuesta.setScheduledMovementId(programado.getId());
        propuesta.setName(programado.getName());
        propuesta.setCategoryId(programado.getCategoryId());
        propuesta.setAmount(programado.getAmount() != null ? programado.getAmount() : BigDecimal.ZERO);
        propuesta.setDate(ocurrencia.fecha());
        propuesta.setPeriodStart(ocurrencia.clave());
        propuesta.setOverdue(ocurrencia.fecha().isBefore(hoy));
        categoryRepository.findByIdAndActiveTrue(programado.getCategoryId()).ifPresent(cat -> {
            propuesta.setCategoryName(cat.getName());
            propuesta.setCategoryType(cat.getType() != null ? cat.getType().name() : null);
        });
        return propuesta;
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
