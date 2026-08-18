package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.input.FilaAImportarDTO;
import com.ohchurus.budget.dto.input.ImportarDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.ImportProfile;
import com.ohchurus.budget.entity.ImportRule;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.ImportProfileRepository;
import com.ohchurus.budget.repository.ImportRuleRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.util.CotejadorDeImportacion;
import com.ohchurus.budget.util.ControlAcceso;
import com.ohchurus.budget.util.LectorCsv;
import com.ohchurus.budget.util.Parecido;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ============================================================================
 * IMPORTAR EL EXTRACTO DEL BANCO
 * ============================================================================
 *
 * ES LO QUE DECIDE SI LA APP SE USA O SE ABANDONA. Nadie deja una app de
 * finanzas porque los informes sean feos; todo el mundo la deja por teclear
 * sesenta movimientos al mes.
 *
 * DOS PASOS, Y EL PRIMERO NO ESCRIBE NADA
 * ---------------------------------------
 * `preview` lee, coteja y clasifica; `confirm` escribe lo que el usuario haya
 * aceptado. Un importador que escribe primero y te deja arreglar el desastre
 * despues es peor que no tener importador: con sesenta filas mal metidas, la
 * unica salida real es borrar el mes entero.
 *
 * TRES LISTAS, NO DOS
 * -------------------
 * Nuevos, duplicados, y los que CONFIRMAN un pendiente que genero una
 * recurrencia. La tercera es la que evita el peor resultado posible: importar
 * el arriendo como nuevo dejando el pendiente colgando, y el gasto contado dos
 * veces.
 */
@Slf4j
@Service
@Transactional
public class ImportacionServiceImpl {

    /** Los formatos con los que exportan los bancos de por aqui. */
    private static final List<String> FORMATOS_DE_FECHA = List.of(
            "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd", "dd/MM/yy", "MM/dd/yyyy");

    private final MovementRepository movimientos;
    private final CategoryRepository categorias;
    private final ImportProfileRepository perfiles;
    private final ImportRuleRepository reglas;
    private final AccountServiceImpl cuentas;
    private final ControlAcceso acceso;

    public ImportacionServiceImpl(MovementRepository movimientos,
                                  CategoryRepository categorias,
                                  ImportProfileRepository perfiles,
                                  ImportRuleRepository reglas,
                                  AccountServiceImpl cuentas,
                                  ControlAcceso acceso) {
        this.movimientos = movimientos;
        this.categorias = categorias;
        this.perfiles = perfiles;
        this.reglas = reglas;
        this.cuentas = cuentas;
        this.acceso = acceso;
    }

    // ========================================================================
    // Vista previa

    @Transactional(readOnly = true)
    public ResultDTO preview(ImportarDTO dto) {
        try {
            Long yo = acceso.usuarioActual();
            if (yo == null) return new ResultDTO(false, "No autenticado", 401);

            ImportProfile perfil = resolverPerfil(dto, yo);
            if (perfil == null) {
                return new ResultDTO(false,
                        "Falta decir en que columna viene la fecha y en cual el importe.", 400);
            }

            List<CotejadorDeImportacion.Fila> filas = interpretar(dto.getCsv(), perfil);
            if (filas.isEmpty()) {
                return new ResultDTO(false,
                        "No se pudo leer ninguna fila. Revisa las columnas y el formato de fecha.", 400);
            }

            List<Movement> existentes = movimientos.findByUserIdAndActiveTrue(yo);
            List<CotejadorDeImportacion.Cotejo> cotejos =
                    CotejadorDeImportacion.cotejar(filas, existentes);

            Map<String, Long> diccionario = diccionarioDe(yo);

            List<Map<String, Object>> nuevos = new ArrayList<>();
            List<Map<String, Object>> duplicados = new ArrayList<>();
            List<Map<String, Object>> confirman = new ArrayList<>();

            for (CotejadorDeImportacion.Cotejo cotejo : cotejos) {
                Map<String, Object> fila = comoMapa(cotejo, diccionario);
                switch (cotejo.veredicto()) {
                    case NUEVO -> nuevos.add(fila);
                    case DUPLICADO -> duplicados.add(fila);
                    case CONFIRMA_PENDIENTE -> confirman.add(fila);
                }
            }

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("newRows", nuevos);
            respuesta.put("duplicates", duplicados);
            respuesta.put("confirmPending", confirman);
            respuesta.put("total", cotejos.size());
            return new ResultDTO(respuesta);
        } catch (Exception e) {
            log.error("Error previewing import: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error leyendo el archivo", 500);
        }
    }

    // ========================================================================
    // Confirmar

    public ResultDTO confirm(ImportarDTO dto) {
        try {
            Long yo = acceso.usuarioActual();
            if (yo == null) return new ResultDTO(false, "No autenticado", 401);
            if (dto.getRows() == null || dto.getRows().isEmpty()) {
                return new ResultDTO(false, "No hay ninguna fila seleccionada", 400);
            }

            ImportProfile perfil = resolverPerfil(dto, yo);
            if (perfil == null) return new ResultDTO(false, "Falta el perfil de columnas", 400);

            /* Se vuelve a leer el CSV en vez de fiarse de lo que mande el
               cliente: asi lo que se guarda es exactamente lo que se vio en la
               vista previa, y no una version reinterpretada por el camino. */
            List<CotejadorDeImportacion.Fila> filas = interpretar(dto.getCsv(), perfil);
            Map<Integer, CotejadorDeImportacion.Fila> porNumero = new LinkedHashMap<>();
            filas.forEach(f -> porNumero.put(f.numero(), f));

            Long cuenta = cuentaElegida(dto.getAccountId(), yo);

            int creados = 0;
            int confirmados = 0;
            List<String> omitidas = new ArrayList<>();

            for (FilaAImportarDTO peticion : dto.getRows()) {
                CotejadorDeImportacion.Fila fila = porNumero.get(peticion.getRow());
                if (fila == null) {
                    omitidas.add("fila " + peticion.getRow() + ": ya no esta en el archivo");
                    continue;
                }

                if (peticion.getConfirmsMovementId() != null) {
                    if (confirmarPendiente(peticion.getConfirmsMovementId(), fila, yo)) {
                        confirmados++;
                    } else {
                        omitidas.add("fila " + peticion.getRow() + ": el pendiente ya no existe");
                    }
                    continue;
                }

                Category categoria = categoriaDe(peticion.getCategoryId(), yo);
                if (categoria == null) {
                    /* NO se inventa una categoria. Meter sesenta gastos en
                       "Otros" es lo que hace que despues nadie se fie del
                       presupuesto, y arreglarlo cuesta mas que haberlos
                       tecleado. */
                    omitidas.add("fila " + peticion.getRow() + ": sin categoria valida");
                    continue;
                }

                movimientos.save(Movement.builder()
                        .userId(yo)
                        .accountId(cuenta)
                        .categoryId(categoria.getId())
                        .date(fila.fecha())
                        .amount(fila.importe().abs())
                        .description(fila.descripcion())
                        .externalId(fila.identificadorDelBanco())
                        .confirmed(true)
                        .active(true)
                        .build());
                creados++;

                aprender(yo, fila.descripcion(), categoria.getId());
            }

            if (Boolean.TRUE.equals(dto.getRememberProfile())) {
                guardarPerfil(dto, perfil, yo);
            }

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("created", creados);
            respuesta.put("confirmed", confirmados);
            respuesta.put("skipped", omitidas);
            respuesta.put("message", "Se importaron " + creados + " movimientos"
                    + (confirmados > 0 ? " y se confirmaron " + confirmados + " pendientes" : "") + ".");
            log.info("Import confirmed by user {}: {} created, {} confirmed, {} skipped",
                    yo, creados, confirmados, omitidas.size());
            return new ResultDTO(respuesta);
        } catch (Exception e) {
            log.error("Error confirming import: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error importando", 500);
        }
    }

    @Transactional(readOnly = true)
    public ResultDTO profiles() {
        Long yo = acceso.usuarioActual();
        if (yo == null) return new ResultDTO(new ArrayList<>());

        List<Map<String, Object>> lista = new ArrayList<>();
        perfiles.findByUserIdAndActiveTrueOrderByBankNameAsc(yo).forEach(p -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", p.getId());
            fila.put("bankName", p.getBankName());
            fila.put("dateColumn", p.getDateColumn());
            fila.put("amountColumn", p.getAmountColumn());
            fila.put("descriptionColumn", p.getDescriptionColumn());
            fila.put("externalIdColumn", p.getExternalIdColumn());
            fila.put("datePattern", p.getDatePattern());
            fila.put("decimalSeparator", p.getDecimalSeparator());
            fila.put("hasHeader", p.getHasHeader());
            fila.put("invertSign", p.getInvertSign());
            lista.add(fila);
        });
        return new ResultDTO(lista);
    }

    // ========================================================================
    // Piezas

    /**
     * El perfil sale del guardado o del mapeo que venga en el cuerpo.
     *
     * Un perfil ajeno se ignora en silencio y se cae al mapeo del cuerpo: no
     * hay nada que filtrar —solo dice en que columna va la fecha— pero
     * aceptarlo dejaria que la forma de leer TU extracto la decidiera otro.
     */
    private ImportProfile resolverPerfil(ImportarDTO dto, Long yo) {
        if (dto.getProfileId() != null) {
            Optional<ImportProfile> guardado = perfiles.findByIdAndActiveTrue(dto.getProfileId());
            if (guardado.isPresent() && yo.equals(guardado.get().getUserId())) {
                return guardado.get();
            }
        }
        if (dto.getDateColumn() == null || dto.getAmountColumn() == null) return null;

        return ImportProfile.builder()
                .userId(yo)
                .bankName(dto.getBankName() != null ? dto.getBankName() : "Mi banco")
                .dateColumn(dto.getDateColumn())
                .amountColumn(dto.getAmountColumn())
                .descriptionColumn(dto.getDescriptionColumn())
                .externalIdColumn(dto.getExternalIdColumn())
                .datePattern(dto.getDatePattern())
                .decimalSeparator(dto.getDecimalSeparator())
                .hasHeader(dto.getHasHeader() == null || dto.getHasHeader())
                .invertSign(Boolean.TRUE.equals(dto.getInvertSign()))
                .active(true)
                .build();
    }

    private void guardarPerfil(ImportarDTO dto, ImportProfile perfil, Long yo) {
        if (perfil.getId() != null) return;
        String banco = perfil.getBankName();
        Optional<ImportProfile> existente =
                perfiles.findByUserIdAndBankNameIgnoreCaseAndActiveTrue(yo, banco);
        /* Se sobreescribe el del mismo banco en vez de acumular perfiles
           llamados igual: al segundo, el selector deja de servir para nada. */
        ImportProfile aGuardar = existente.orElse(perfil);
        aGuardar.setUserId(yo);
        aGuardar.setBankName(banco);
        aGuardar.setDateColumn(perfil.getDateColumn());
        aGuardar.setAmountColumn(perfil.getAmountColumn());
        aGuardar.setDescriptionColumn(perfil.getDescriptionColumn());
        aGuardar.setExternalIdColumn(perfil.getExternalIdColumn());
        aGuardar.setDatePattern(perfil.getDatePattern());
        aGuardar.setDecimalSeparator(perfil.getDecimalSeparator());
        aGuardar.setHasHeader(perfil.getHasHeader());
        aGuardar.setInvertSign(perfil.getInvertSign());
        aGuardar.setActive(true);
        perfiles.save(aGuardar);
    }

    /** Convierte el CSV en filas interpretadas, saltando lo que no se pueda leer. */
    private List<CotejadorDeImportacion.Fila> interpretar(String csv, ImportProfile perfil) {
        List<List<String>> crudas = LectorCsv.leer(csv);
        List<CotejadorDeImportacion.Fila> filas = new ArrayList<>();

        int desde = Boolean.TRUE.equals(perfil.getHasHeader()) ? 1 : 0;
        for (int i = desde; i < crudas.size(); i++) {
            List<String> cruda = crudas.get(i);

            LocalDate fecha = leerFecha(columna(cruda, perfil.getDateColumn()), perfil.getDatePattern());
            BigDecimal importe = leerImporte(columna(cruda, perfil.getAmountColumn()),
                    perfil.getDecimalSeparator());
            if (fecha == null || importe == null) {
                /* Una fila ilegible se salta en vez de tumbar la importacion
                   entera: los extractos traen totales y pies de pagina, y
                   rechazar el fichero por eso obligaria a editarlo a mano. */
                continue;
            }
            if (Boolean.TRUE.equals(perfil.getInvertSign())) importe = importe.negate();

            filas.add(new CotejadorDeImportacion.Fila(
                    i,
                    fecha,
                    importe,
                    columna(cruda, perfil.getDescriptionColumn()),
                    columna(cruda, perfil.getExternalIdColumn())));
        }
        return filas;
    }

    private String columna(List<String> fila, Integer indice) {
        if (indice == null || indice < 0 || indice >= fila.size()) return null;
        String valor = fila.get(indice);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private LocalDate leerFecha(String texto, String patron) {
        if (texto == null) return null;
        List<String> aProbar = new ArrayList<>();
        if (patron != null && !patron.isBlank()) aProbar.add(patron);
        aProbar.addAll(FORMATOS_DE_FECHA);

        for (String formato : aProbar) {
            try {
                return LocalDate.parse(texto, DateTimeFormatter.ofPattern(formato));
            } catch (Exception ignorada) {
                // se prueba el siguiente
            }
        }
        return null;
    }

    /**
     * Lee un importe escrito como lo escribe un banco colombiano.
     *
     * "$ 1.234.567,89", "1,234,567.89", "(45.000)" para negativos y "-45000"
     * son todos la misma clase de dato. El separador decimal se detecta por el
     * ULTIMO signo que aparece, porque es el unico que puede serlo.
     */
    private BigDecimal leerImporte(String texto, String separadorDecimal) {
        if (texto == null) return null;

        boolean negativoEntreParentesis = texto.contains("(") && texto.contains(")");
        String limpio = texto.replaceAll("[^0-9,.-]", "");
        if (limpio.isEmpty()) return null;

        String decimal = separadorDecimal;
        if (decimal == null || decimal.isBlank()) {
            int ultimaComa = limpio.lastIndexOf(',');
            int ultimoPunto = limpio.lastIndexOf('.');
            int ultimo = Math.max(ultimaComa, ultimoPunto);
            decimal = ultimaComa > ultimoPunto ? "," : ".";

            /*
             * TRES DIGITOS DETRAS SON MILES, NO DECIMALES.
             *
             * "45.000" en Colombia son cuarenta y cinco mil. Quedarse con la
             * regla ingenua de "el ultimo separador es el decimal" lo leia como
             * cuarenta y cinco pesos, y un extracto entero entraba con los
             * importes divididos por mil. Lo cazo la prueba de los parentesis.
             *
             * No hay ambiguedad cuando aparecen los DOS separadores
             * ("1.234.567,89"): ahi el ultimo es el decimal y punto. La regla
             * de los tres digitos solo se aplica cuando hay uno solo, que es
             * justo el caso en el que hay que decidir.
             *
             * Se prefiere leerlo como miles porque el error al reves es
             * catastrofico y silencioso: 45.000 convertidos en 45 no llaman la
             * atencion de nadie en una lista de sesenta filas.
             */
            boolean hayLosDos = ultimaComa >= 0 && ultimoPunto >= 0;
            if (!hayLosDos && ultimo >= 0 && limpio.length() - ultimo - 1 == 3) {
                limpio = limpio.replace(",", "").replace(".", "");
                try {
                    BigDecimal entero = new BigDecimal(limpio);
                    return negativoEntreParentesis ? entero.abs().negate() : entero;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        String miles = ",".equals(decimal) ? "." : ",";
        limpio = limpio.replace(miles, "").replace(decimal, ".");

        try {
            BigDecimal valor = new BigDecimal(limpio);
            return negativoEntreParentesis ? valor.abs().negate() : valor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean confirmarPendiente(Long movimientoId, CotejadorDeImportacion.Fila fila, Long yo) {
        Optional<Movement> pendiente = movimientos.findByIdAndActiveTrue(movimientoId);
        if (pendiente.isEmpty() || !acceso.puedeVer(pendiente.get().getUserId(),
                pendiente.get().getCategoryId())) {
            return false;
        }
        Movement m = pendiente.get();
        m.setConfirmed(true);
        /* Se le pega el identificador del banco para que la SIGUIENTE
           importacion lo reconozca sin tener que adivinar por importe y fecha. */
        if (fila.identificadorDelBanco() != null) m.setExternalId(fila.identificadorDelBanco());
        movimientos.save(m);
        return true;
    }

    private Category categoriaDe(Long categoriaId, Long yo) {
        if (categoriaId == null) return null;
        Optional<Category> categoria = categorias.findByIdAndActiveTrue(categoriaId);
        return categoria.isPresent() && acceso.puedeVerCategoria(categoria.get())
                ? categoria.get() : null;
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

    // ========================================================================
    // El diccionario

    private Map<String, Long> diccionarioDe(Long yo) {
        Map<String, Long> mapa = new LinkedHashMap<>();
        reglas.findByUserIdAndActiveTrue(yo).stream()
                .sorted((a, b) -> Integer.compare(b.getHits(), a.getHits()))
                .forEach(r -> mapa.putIfAbsent(r.getPattern(), r.getCategoryId()));
        return mapa;
    }

    /**
     * Aprende que esta descripcion va en esta categoria.
     *
     * Se guarda NORMALIZADA porque el banco escribe en mayusculas y sin
     * tildes: comparar en crudo convertiria cada variante en una entrada
     * distinta que no sirve para nada.
     */
    private void aprender(Long yo, String descripcion, Long categoriaId) {
        String patron = Parecido.normalizar(descripcion);
        if (patron.isEmpty()) return;
        if (patron.length() > 200) patron = patron.substring(0, 200);

        String clave = patron;
        Optional<ImportRule> existente = reglas.findByUserIdAndPatternAndActiveTrue(yo, clave);
        if (existente.isPresent()) {
            ImportRule regla = existente.get();
            regla.setCategoryId(categoriaId);
            regla.setHits(regla.getHits() + 1);
            reglas.save(regla);
        } else {
            reglas.save(ImportRule.builder()
                    .userId(yo).pattern(clave).categoryId(categoriaId)
                    .hits(1).active(true).build());
        }
    }

    /** La categoria que sugiere el diccionario, si alguna se parece bastante. */
    private Long sugerirCategoria(String descripcion, Map<String, Long> diccionario) {
        String patron = Parecido.normalizar(descripcion);
        if (patron.isEmpty() || diccionario.isEmpty()) return null;

        /*
         * Se pregunta con Parecido.bastante y no con la distancia a secas, por
         * el mismo motivo que el cotejo de duplicados: el banco ANADE
         * PALABRAS. Aprendiste que "COMPRA EXITO" va en Mercado y al mes
         * siguiente llega "COMPRA EXITO CALLE 80"; la distancia da 0,57 y lo
         * declararia desconocido, obligandote a clasificar otra vez lo que ya
         * habias clasificado — que es justo lo que el diccionario viene a
         * evitar.
         *
         * Entre varios que valen gana el mas parecido, para lo cual si hace
         * falta la cifra.
         */
        Long mejor = null;
        double mejorParecido = -1;
        for (Map.Entry<String, Long> entrada : diccionario.entrySet()) {
            if (!Parecido.bastante(patron, entrada.getKey())) continue;
            double cuanto = Parecido.cuanto(patron, entrada.getKey());
            if (cuanto > mejorParecido) {
                mejorParecido = cuanto;
                mejor = entrada.getValue();
            }
        }
        return mejor;
    }

    private Map<String, Object> comoMapa(CotejadorDeImportacion.Cotejo cotejo,
                                         Map<String, Long> diccionario) {
        CotejadorDeImportacion.Fila fila = cotejo.fila();
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("row", fila.numero());
        mapa.put("date", fila.fecha().toString());
        mapa.put("amount", fila.importe());
        mapa.put("description", fila.descripcion());
        mapa.put("externalId", fila.identificadorDelBanco());
        /* El signo del extracto dice si es gasto o ingreso, y se ensena para
           que el usuario lo pueda corregir antes de importar en vez de
           despues. */
        mapa.put("suggestedType", fila.importe().signum() < 0
                ? CategoryType.EXPENSE.name() : CategoryType.INCOME.name());
        mapa.put("suggestedCategoryId", sugerirCategoria(fila.descripcion(), diccionario));
        mapa.put("matchedMovementId", cotejo.movimientoId());
        mapa.put("reason", cotejo.motivo());
        return mapa;
    }
}
