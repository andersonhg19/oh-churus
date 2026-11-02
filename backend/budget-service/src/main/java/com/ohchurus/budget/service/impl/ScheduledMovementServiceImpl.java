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
import com.ohchurus.budget.enums.ScheduledKind;
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

    /** Quien paga de verdad: el responsable, o quien lo creo si no hay. */
    static Long pagadorDe(ScheduledMovement p) {
        return p.getPayerUserId() != null ? p.getPayerUserId() : p.getUserId();
    }

    /**
     * El responsable tiene que ser alguien de mi hogar. Y si la categoria es
     * PRIVADA, solo puede ser su dueno: el gasto de una categoria que el otro
     * no ve no puede caer en la cuenta del otro, que tampoco lo veria.
     */
    private String pagadorInvalido(Long pagador, Long categoryId, Long yo) {
        if (pagador == null) return null;
        if (!householdService.companerosDeHogar(yo).contains(pagador)) {
            return "Quien paga tiene que ser alguien de tu hogar";
        }
        return categoryRepository.findByIdAndActiveTrue(categoryId)
                .filter(c -> c.getHouseholdId() == null && !pagador.equals(c.getUserId()))
                .map(c -> "Una categoria privada solo la puede pagar su dueno")
                .orElse(null);
    }

    @Override
    public ResultDTO saveAndUpdate(ScheduledMovementSaveDTO dto) {
        /* Un credito de 0 cuotas termina antes de empezar: no genera nada y
           desaparece del plan sin que se sepa por que. */
        if (dto.getDurationMonths() != null && dto.getDurationMonths() <= 0) {
            return new ResultDTO(false, "La duracion tiene que ser de al menos 1 mes (o vacia si no termina)", 400);
        }
        try {
            boolean isUpdate = dto.getId() != null;
            return isUpdate ? updateScheduled(dto) : createScheduled(dto);
        } catch (Exception e) {
            com.ohchurus.budget.util.Transacciones.revertir();
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
        /* Que la categoria exista no basta: tiene que ser una que yo vea. Si
           no, bastaba un id ajeno para meterle un cobro mensual a otra casa. */
        if (categoryRepository.findByIdAndActiveTrue(dto.getCategoryId())
                .filter(acceso::puedeVerCategoria).isEmpty()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        String patronIncompleto = patronSemanalIncompleto(dto);
        if (patronIncompleto != null) {
            return new ResultDTO(false, patronIncompleto, 400);
        }

        String pagadorInvalido = pagadorInvalido(dto.getPayerUserId(), dto.getCategoryId(),
                com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId());
        if (pagadorInvalido != null) {
            return new ResultDTO(false, pagadorInvalido, 400);
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
                .payerUserId(dto.getPayerUserId())
                .kind(ScheduledKind.de(dto.getKind()))
                .notes(dto.getNotes() != null && !dto.getNotes().isBlank() ? dto.getNotes().trim() : null)
                .active(true)
                .build();

        ScheduledMovement saved = scheduledMovementRepository.save(scheduled);
        log.info("Scheduled movement created: id={} name={} for user {}", saved.getId(), saved.getName(), saved.getUserId());
        return new ResultDTO(scheduledMovementMapper.toResultDTO(saved));
    }

    private ResultDTO updateScheduled(ScheduledMovementSaveDTO dto) {
        Optional<ScheduledMovement> existing = scheduledMovementRepository.findByIdAndActiveTrue(dto.getId());
        /* Leer y borrar ya miraban puedoTocar; editar no, y con el id de un
           programado ajeno se le podia cambiar el importe a otro. */
        if (existing.isEmpty() || !puedoTocar(existing.get())) {
            return new ResultDTO(false, "Scheduled movement not found", 404);
        }

        if (categoryRepository.findByIdAndActiveTrue(dto.getCategoryId())
                .filter(acceso::puedeVerCategoria).isEmpty()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        String patronIncompleto = patronSemanalIncompleto(dto);
        if (patronIncompleto != null) {
            return new ResultDTO(false, patronIncompleto, 400);
        }

        ScheduledMovement scheduled = existing.get();
        /* La firma del calendario ANTES de tocarlo: si cambia, las ocurrencias
           ya generadas quedan con claves del calendario viejo (ver
           realinearOcurrencias). */
        String calendarioAntes = firmaDelCalendario(scheduled);
        Antes antes = new Antes(scheduled.getAmount(), scheduled.getCategoryId(), scheduled.getName(), pagadorDe(scheduled));
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
        /* Responsable, clase y notas: nulo es "no lo mandaste", no "borralo".
           Un cliente que aun no conoce estos campos no puede, al guardar,
           devolverle el gimnasio de Samy a quien lo creo. Para vaciar las notas
           se manda un texto vacio. */
        if (dto.getPayerUserId() != null) {
            String pagadorInvalido = pagadorInvalido(dto.getPayerUserId(), dto.getCategoryId(),
                    com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId());
            if (pagadorInvalido != null) {
                return new ResultDTO(false, pagadorInvalido, 400);
            }
            scheduled.setPayerUserId(dto.getPayerUserId());
        }
        if (dto.getKind() != null) {
            scheduled.setKind(dto.getKind());
        }
        if (dto.getNotes() != null) {
            scheduled.setNotes(dto.getNotes().isBlank() ? null : dto.getNotes().trim());
        }

        /* Con la categoria nueva el responsable de siempre puede dejar de ser
           valido (la categoria paso a privada y lo paga la pareja). */
        if (scheduled.getPayerUserId() != null) {
            String pagadorInvalido = pagadorInvalido(scheduled.getPayerUserId(), scheduled.getCategoryId(),
                    com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId());
            if (pagadorInvalido != null) {
                return new ResultDTO(false, pagadorInvalido, 400);
            }
        }

        ScheduledMovement saved = scheduledMovementRepository.save(scheduled);
        realinearOcurrencias(saved, !calendarioAntes.equals(firmaDelCalendario(saved)),
                dto.getBudgetStartDay() != null ? dto.getBudgetStartDay() : 1, antes);
        log.info("Scheduled movement updated: id={}", saved.getId());
        return new ResultDTO(scheduledMovementMapper.toResultDTO(saved));
    }

    private static String cmp(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private static String firmaDelCalendario(ScheduledMovement p) {
        return String.join("|", String.valueOf(p.getFrequency()), String.valueOf(p.getStartDate()),
                String.valueOf(p.getEndDate()), String.valueOf(p.getDayOfMonth()),
                String.valueOf(p.getWeekOfMonth()), String.valueOf(p.getDayOfWeek()),
                String.valueOf(p.getWeekendPolicy()));
    }

    /**
     * Lo ya generado tiene que seguir al programado editado.
     *
     * Paso en produccion: el salario se movio del dia 1 al 28. La ocurrencia
     * confirmada del 1-nov se quedo con la clave de noviembre; el calendario
     * nuevo produjo el 28-oct (clave octubre), que "faltaba", y lo genero otra
     * vez —la misma nomina dos veces—, y el 28-nov ya no salia nunca porque su
     * clave la ocupaba la vieja.
     *
     *  · Si cambio el CALENDARIO: los pendientes sin confirmar de hoy en
     *    adelante se borran (el siguiente refresco los genera con el calendario
     *    nuevo) y todo lo demas —confirmado, atrasado, omitido— se re-clava a la
     *    ocurrencia nueva del MISMO periodo de presupuesto (la mas cercana si hay
     *    varias), sin repetir: con corte el 28 el salario del 1-nov y el del
     *    28-oct son la misma nomina; con corte el 1, el arriendo del 1 y el del
     *    25 de octubre son el mismo. Lo que no tiene pareja se queda sin clave y
     *    se reconoce por su fecha, como las filas de antes de existir la clave.
     *  · Si no: importe, categoria, nombre y responsable pasan a los pendientes
     *    sin confirmar; y si ahora es una provision, esos pendientes sobran.
     */
    /** Como estaba el programado antes de editarlo: solo se propaga lo que cambio. */
    private record Antes(BigDecimal importe, Long categoria, String nombre, Long pagador) {}

    private void realinearOcurrencias(ScheduledMovement p, boolean cambioElCalendario, int diaDeCorte, Antes antes) {
        List<Movement> generadas = movementRepository.findByScheduledMovementId(p.getId());
        if (generadas.isEmpty()) {
            return;
        }
        LocalDate hoy = LocalDate.now();
        List<Movement> sinConfirmarVivos = generadas.stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive()) && !Boolean.TRUE.equals(m.getConfirmed()))
                .collect(Collectors.toList());

        if (ScheduledKind.de(p.getKind()) == ScheduledKind.PROVISION) {
            movementRepository.deleteAll(sinConfirmarVivos);
            log.info("Scheduled {} is now a provision: {} pending removed", p.getId(), sinConfirmarVivos.size());
            return;
        }

        if (!cambioElCalendario) {
            /* Solo lo que CAMBIO en el programado pasa a sus pendientes. Si se
               toca solo la nota, un pendiente corregido a 175.350 no vuelve a
               150.000, y uno que esta vez paga la pareja no vuelve a su dueno. */
            Long pagador = pagadorDe(p);
            boolean cambioImporte = !Objects.equals(cmp(antes.importe()), cmp(p.getAmount()));
            boolean cambioCategoria = !Objects.equals(antes.categoria(), p.getCategoryId());
            boolean cambioNombre = !Objects.equals(antes.nombre(), p.getName());
            boolean cambioPagador = !Objects.equals(antes.pagador(), pagador);
            if (!cambioImporte && !cambioCategoria && !cambioNombre && !cambioPagador) return;
            Long cuenta = cambioPagador ? cuentas.porDefecto(pagador).getId() : null;
            for (Movement m : sinConfirmarVivos) {
                if (cambioImporte) m.setAmount(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
                if (cambioCategoria) m.setCategoryId(p.getCategoryId());
                if (cambioNombre) m.setDescription(p.getName());
                if (cambioPagador && !pagador.equals(m.getUserId())) {
                    m.setUserId(pagador);
                    m.setAccountId(cuenta);
                }
            }
            movementRepository.saveAll(sinConfirmarVivos);
            return;
        }

        List<Movement> futurasSinConfirmar = generadas.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getConfirmed()) && m.getDate() != null && !m.getDate().isBefore(hoy))
                .collect(Collectors.toList());
        movementRepository.deleteAll(futurasSinConfirmar);

        List<Movement> quedan = generadas.stream()
                .filter(m -> !futurasSinConfirmar.contains(m) && m.getDate() != null)
                .sorted(Comparator.comparing(Movement::getDate))
                .collect(Collectors.toList());
        if (quedan.isEmpty()) {
            return;
        }
        LocalDate ultima = quedan.get(quedan.size() - 1).getDate();
        List<Ocurrencia> nuevas = CalendarioDeRecurrencias.ocurrenciasHasta(p, ultima.plusDays(32));
        Set<LocalDate> usadas = new HashSet<>();
        for (Movement m : quedan) {
            LocalDate suPeriodo = PeriodUtils.getStartOfPeriod(diaDeCorte, m.getDate());
            Ocurrencia cercana = nuevas.stream()
                    .filter(o -> !usadas.contains(o.clave()))
                    .filter(o -> PeriodUtils.getStartOfPeriod(diaDeCorte, o.fecha()).equals(suPeriodo))
                    .min(Comparator.comparingLong(o -> Math.abs(java.time.temporal.ChronoUnit.DAYS.between(o.fecha(), m.getDate()))))
                    .orElse(null);
            if (cercana != null) {
                usadas.add(cercana.clave());
                m.setPeriodStart(cercana.clave());
            } else {
                m.setPeriodStart(null);
            }
        }
        movementRepository.saveAll(quedan);
        log.info("Scheduled {} calendar changed: {} future pending removed, {} occurrences re-keyed",
                p.getId(), futurasSinConfirmar.size(), quedan.size());
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

    /**
     * Lo que la lista necesita y la fila no guarda: la categoria, quien paga
     * con su apodo, y para los que tienen fin, las cuotas que faltan.
     *
     * Las cuotas se CUENTAN con el mismo calendario que genera los pendientes,
     * no se restan a mano: un bimestral "a 4 meses" son dos cuotas, no cuatro,
     * y la unica forma de no equivocarse es preguntarle al calendario.
     */
    private void enriquecer(ResultScheduledMovementDTO dto, ScheduledMovement s) {
        categoryRepository.findByIdAndActiveTrue(s.getCategoryId()).ifPresent(cat -> {
            dto.setCategoryName(cat.getName());
            dto.setCategoryType(cat.getType() != null ? cat.getType().name() : null);
            dto.setCategoryIcon(cat.getIcon());
            dto.setCategoryColor(cat.getColor());
            dto.setCategoryHouseholdId(cat.getHouseholdId());
        });
        Long pagador = pagadorDe(s);
        dto.setEffectivePayerUserId(pagador);
        dto.setPayerName(householdService.apodoDe(pagador));
        if (dto.getKind() == null) dto.setKind(ScheduledKind.PAYMENT);

        LocalDate hoy = LocalDate.now();
        LocalDate hasta = s.getEndDate() != null ? s.getEndDate() : hoy.plusDays(400);
        List<Ocurrencia> futuras = CalendarioDeRecurrencias.ocurrenciasHasta(s, hasta).stream()
                .filter(o -> !o.fecha().isBefore(hoy))
                .collect(Collectors.toList());
        if (!futuras.isEmpty()) dto.setNextDate(futuras.get(0).fecha());
        if (s.getEndDate() != null) {
            dto.setRemainingOccurrences(futuras.size());
            dto.setRemainingTotal(s.getAmount() != null
                    ? s.getAmount().multiply(BigDecimal.valueOf(futuras.size()))
                    : null);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getById(Long id) {
        Optional<ScheduledMovement> scheduled = scheduledMovementRepository.findByIdAndActiveTrue(id);
        if (scheduled.isEmpty() || !puedoTocar(scheduled.get())) {
            return new ResultDTO(false, "Scheduled movement not found", 404);
        }
        ResultScheduledMovementDTO dto = scheduledMovementMapper.toResultDTO(scheduled.get());
        enriquecer(dto, scheduled.get());
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
                        enriquecer(dto, s);
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
    /* Transaccion propia: el panel la llama dentro de la suya, y si generar
       falla (dos refrescos a la vez chocan con el indice de V11) el panel tiene
       que seguir mostrandose, sin quedar marcado para revertir. */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
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
                /* Una provision no es un pago: separar plata para el mercado
                   no es comprar el mercado. Si generara pendiente, al anotar
                   la compra real el gasto contaria dos veces. */
                if (ScheduledKind.de(programado.getKind()) == ScheduledKind.PROVISION) {
                    continue;
                }
                /* Un programado que TERMINO antes del periodo actual no tiene
                   nada que decir. No se le proponen sus ocurrencias viejas: un
                   credito que se acabo hace dos anos y nunca se genero es
                   historia, no una tarea pendiente, y llenar la pantalla de
                   revisiones de cosas muertas es la forma mas rapida de que
                   nadie vuelva a mirar la pantalla de revisiones. */
                if (programado.getEndDate() != null && programado.getEndDate().isBefore(inicioDelPeriodo)) {
                    continue;
                }

                /*
                 * HASTA DONDE SE GENERA, Y POR QUE DEPENDE DE QUIEN ES EL
                 * PROGRAMADO.
                 *
                 * El fallo que esto arregla: la ventana se calculaba SIEMPRE
                 * con el dia de corte de QUIEN REFRESCA. Con el arriendo de
                 * Ana (corte el 1) y Bruno mirando el panel (corte el 15), el
                 * 18 de agosto el periodo de Bruno llegaba hasta el 14 de
                 * septiembre, asi que su refresco le creaba a Ana el arriendo
                 * de SEPTIEMBRE. El mismo programado generaba un numero
                 * distinto de ocurrencias segun quien abriera la app primero y
                 * que dia del mes fuera.
                 *
                 * Es la misma familia del fallo que ya costo una ola —"el
                 * arriendo de uno acababa a nombre del otro solo porque abrio
                 * la app primero"— y se descubrio igual: una prueba que llevaba
                 * dias en verde se puso roja sola al cambiar la fecha del
                 * sistema.
                 *
                 * La regla ahora no depende de quien mira:
                 *
                 *   · Lo TUYO se adelanta hasta el final de tu periodo, que es
                 *     lo que le da sentido al panel: ver lo que viene.
                 *   · Lo de OTRO solo se materializa hasta HOY, es decir lo que
                 *     ya vencio. Adelantarle a alguien pendientes futuros
                 *     usando TU calendario es inventarle datos con una regla
                 *     que ni siquiera es la suya.
                 *
                 * No se pide el dia de corte del dueno al servicio de usuarios
                 * a proposito: seria una llamada HTTP por programado ajeno en
                 * cada refresco del panel, y "hasta hoy" ya es deterministico,
                 * que es lo que hacia falta.
                 */
                boolean esMio = programado.getUserId() != null && programado.getUserId().equals(userId);
                LocalDate hastaDonde = esMio ? finDeLaVentana : hoy;

                List<Ocurrencia> faltantes = ocurrenciasQueFaltan(programado, hastaDonde);
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
            com.ohchurus.budget.util.Transacciones.revertir();
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
                if (ScheduledKind.de(programado.getKind()) == ScheduledKind.PROVISION) {
                    return new ResultDTO(false,
                            "Una provision no genera movimientos: anota la compra real cuando ocurra", 400);
                }
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
            com.ohchurus.budget.util.Transacciones.revertir();
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
        /* Se enumera unos dias de mas y se corta por la fecha REAL del pago:
           el arriendo del sabado 28 que se paga el viernes 27 tiene que tener
           su pendiente el 27, no nacer ya vencido en el periodo siguiente. */
        List<Ocurrencia> todas = CalendarioDeRecurrencias.ocurrenciasHasta(programado, hasta.plusDays(3)).stream()
                .filter(o -> !o.fecha().isAfter(hasta))
                .collect(Collectors.toList());
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
                /* Ola 5: y si el programado tiene responsable, es de el. El
                   gimnasio de Samy que dio de alta Anderson tiene que salir de
                   la cuenta de Samy. */
                .userId(pagadorDe(programado))
                /* Y su cuenta es la del dueno, por lo mismo: un pendiente sin
                   cuenta no saldria en ningun saldo pero si contaria en el
                   presupuesto. */
                .accountId(cuentas.porDefecto(pagadorDe(programado)).getId())
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
