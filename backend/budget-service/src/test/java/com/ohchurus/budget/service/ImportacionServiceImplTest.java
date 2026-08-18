package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.FilaAImportarDTO;
import com.ohchurus.budget.dto.input.ImportarDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Account;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.ImportProfile;
import com.ohchurus.budget.entity.ImportRule;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.AccountKind;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.ImportProfileRepository;
import com.ohchurus.budget.repository.ImportRuleRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.impl.AccountServiceImpl;
import com.ohchurus.budget.service.impl.ImportacionServiceImpl;
import com.ohchurus.budget.util.ControlAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los bordes del importador: formatos raros de banco, perfiles y negativas.
 *
 * El camino feliz lo cubre ImportarNoDuplicaTest levantando la app entera.
 * Aqui estan las cosas que solo se pueden provocar a mano — un extracto con
 * los importes entre parentesis, un perfil ajeno, una fecha que ningun formato
 * conocido entiende — y que son exactamente las que aparecen con el primer
 * banco de verdad.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Importacion: formatos de banco, perfiles y negativas")
class ImportacionServiceImplTest {

    private static final Long YO = 1L;
    private static final Long OTRO = 2L;
    private static final Long CATEGORIA = 10L;

    @Mock private MovementRepository movimientos;
    @Mock private CategoryRepository categorias;
    @Mock private ImportProfileRepository perfiles;
    @Mock private ImportRuleRepository reglas;
    @Mock private AccountServiceImpl cuentas;
    @Mock private ControlAcceso acceso;

    @InjectMocks private ImportacionServiceImpl servicio;

    @BeforeEach
    void soyYo() {
        lenient().when(acceso.usuarioActual()).thenReturn(YO);
        lenient().when(acceso.puedeVerCategoria(any())).thenReturn(true);
        lenient().when(movimientos.findByUserIdAndActiveTrue(any())).thenReturn(List.of());
        lenient().when(reglas.findByUserIdAndActiveTrue(any())).thenReturn(List.of());
        lenient().when(cuentas.porDefecto(any())).thenReturn(Account.builder()
                .id(4L).userId(YO).name("Sin asignar").kind(AccountKind.OWN)
                .isDefault(true).active(true).build());
    }

    private ImportarDTO conCsv(String csv) {
        ImportarDTO dto = new ImportarDTO();
        dto.setCsv(csv);
        dto.setDateColumn(0);
        dto.setDescriptionColumn(1);
        dto.setAmountColumn(2);
        dto.setHasHeader(false);
        return dto;
    }

    private Category categoria() {
        return Category.builder().id(CATEGORIA).userId(YO).name("Mercado")
                .type(CategoryType.EXPENSE).active(true).build();
    }

    // ========================================================================

    @Nested
    @DisplayName("Formatos con los que exporta un banco de verdad")
    class FormatosDeBanco {

        private BigDecimal importeLeidoDe(String csv) {
            ResultDTO r = servicio.preview(conCsv(csv));
            assertThat(r.isCorrect()).as(String.valueOf(r.getMessage())).isTrue();
            Map<?, ?> objeto = (Map<?, ?>) r.getObject();
            List<?> nuevos = (List<?>) objeto.get("newRows");
            assertThat(nuevos).isNotEmpty();
            return (BigDecimal) ((Map<?, ?>) nuevos.get(0)).get("amount");
        }

        @Test
        @DisplayName("miles con punto y decimales con coma, que es como escribe medio pais")
        void formatoColombiano() {
            assertThat(importeLeidoDe("2026-08-01,Compra,\"$ 1.234.567,89\"\n"))
                    .isEqualByComparingTo("1234567.89");
        }

        @Test
        @DisplayName("miles con coma y decimales con punto, que es como escribe el otro medio")
        void formatoIngles() {
            assertThat(importeLeidoDe("2026-08-01,Compra,\"1,234,567.89\"\n"))
                    .isEqualByComparingTo("1234567.89");
        }

        @Test
        @DisplayName("los parentesis son un negativo, no un adorno")
        void parentesisSonNegativo() {
            /* Varios extractos escriben los cargos como (45.000). Leerlo como
               positivo convertiria un gasto en un ingreso. */
            assertThat(importeLeidoDe("2026-08-01,Compra,(45.000)\n"))
                    .isEqualByComparingTo("-45000");
        }

        @Test
        @DisplayName("45.000 son cuarenta y cinco MIL, no cuarenta y cinco")
        void tresDigitosSonMiles() {
            /*
             * La regla ingenua —"el ultimo separador es el decimal"— leia
             * "45.000" como cuarenta y cinco pesos, y un extracto entero
             * entraba con los importes divididos por mil.
             *
             * Se prefiere leerlo como miles porque el error al reves es
             * catastrofico y silencioso: 45.000 convertidos en 45 no llaman la
             * atencion de nadie en una lista de sesenta filas, y el saldo deja
             * de cuadrar sin que se sepa por que.
             */
            assertThat(importeLeidoDe("2026-08-01,Compra,-45.000\n"))
                    .isEqualByComparingTo("-45000");
            assertThat(importeLeidoDe("2026-08-01,Compra,1.500\n"))
                    .isEqualByComparingTo("1500");
        }

        @Test
        @DisplayName("pero dos decimales detras si son decimales")
        void dosDigitosSonDecimales() {
            assertThat(importeLeidoDe("2026-08-01,Compra,\"45,50\"\n"))
                    .isEqualByComparingTo("45.50");
        }

        @Test
        @DisplayName("acepta las fechas escritas a la colombiana")
        void fechaDiaMesAno() {
            ResultDTO r = servicio.preview(conCsv("15/08/2026,Compra,-45000\n"));
            Map<?, ?> objeto = (Map<?, ?>) r.getObject();
            assertThat(((Map<?, ?>) ((List<?>) objeto.get("newRows")).get(0)).get("date"))
                    .isEqualTo("2026-08-15");
        }

        @Test
        @DisplayName("invertir el signo para los bancos que exportan los gastos en positivo")
        void invertirElSigno() {
            /* Sin esto habria que editar sesenta filas a mano, que es
               exactamente lo que el importador viene a evitar. */
            ImportarDTO dto = conCsv("2026-08-01,Compra,45000\n");
            dto.setInvertSign(true);

            Map<?, ?> objeto = (Map<?, ?>) servicio.preview(dto).getObject();
            Map<?, ?> fila = (Map<?, ?>) ((List<?>) objeto.get("newRows")).get(0);
            assertThat((BigDecimal) fila.get("amount")).isEqualByComparingTo("-45000");
            assertThat(fila.get("suggestedType")).isEqualTo("EXPENSE");
        }

        @Test
        @DisplayName("una fecha que ningun formato entiende salta la fila, no tumba el archivo")
        void fechaIlegible() {
            ResultDTO r = servicio.preview(conCsv("ayer,Compra,-45000\n2026-08-01,Otra,-1000\n"));
            Map<?, ?> objeto = (Map<?, ?>) r.getObject();
            assertThat(objeto.get("total")).isEqualTo(1);
        }

        @Test
        @DisplayName("si no se puede leer NADA se dice, en vez de devolver una lista vacia")
        void nadaLegible() {
            /* Una lista vacia se lee como "tu extracto no tiene movimientos",
               cuando lo que pasa es que las columnas estan mal puestas. */
            ResultDTO r = servicio.preview(conCsv("basura,total,sin sentido\n"));
            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("columnas");
        }
    }

    @Nested
    @DisplayName("Perfiles")
    class Perfiles {

        @Test
        @DisplayName("sin decir donde esta la fecha ni el importe, no se adivina")
        void sinMapeo() {
            ImportarDTO dto = new ImportarDTO();
            dto.setCsv("2026-08-01,Compra,-45000\n");

            ResultDTO r = servicio.preview(dto);
            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("columna");
        }

        @Test
        @DisplayName("un perfil ajeno se ignora: la forma de leer TU extracto no la elige otro")
        void perfilAjeno() {
            when(perfiles.findByIdAndActiveTrue(9L)).thenReturn(Optional.of(
                    ImportProfile.builder().id(9L).userId(OTRO).bankName("Suyo")
                            .dateColumn(2).amountColumn(0).hasHeader(false)
                            .invertSign(false).active(true).build()));

            ImportarDTO dto = conCsv("2026-08-01,Compra,-45000\n");
            dto.setProfileId(9L);

            /* Cae al mapeo del cuerpo, que es el correcto: la fecha en la 0. */
            Map<?, ?> objeto = (Map<?, ?>) servicio.preview(dto).getObject();
            assertThat(((Map<?, ?>) ((List<?>) objeto.get("newRows")).get(0)).get("date"))
                    .isEqualTo("2026-08-01");
        }

        @Test
        @DisplayName("guardar el perfil sobreescribe el del mismo banco en vez de acumular")
        void noSeAcumulanPerfiles() {
            /* Al segundo perfil llamado "Bancolombia", el selector deja de
               servir para nada. */
            when(categorias.findByIdAndActiveTrue(CATEGORIA)).thenReturn(Optional.of(categoria()));
            when(perfiles.findByUserIdAndBankNameIgnoreCaseAndActiveTrue(YO, "Bancolombia"))
                    .thenReturn(Optional.of(ImportProfile.builder()
                            .id(3L).userId(YO).bankName("Bancolombia")
                            .dateColumn(0).amountColumn(2).hasHeader(false)
                            .invertSign(false).active(true).build()));

            ImportarDTO dto = conCsv("2026-08-01,Compra,-45000\n");
            dto.setBankName("Bancolombia");
            dto.setRememberProfile(true);
            dto.setRows(List.of(new FilaAImportarDTO(0, CATEGORIA, null)));

            servicio.confirm(dto);

            ArgumentCaptor<ImportProfile> guardado = ArgumentCaptor.forClass(ImportProfile.class);
            verify(perfiles).save(guardado.capture());
            assertThat(guardado.getValue().getId())
                    .as("se actualiza el que ya existia, no se crea otro igual")
                    .isEqualTo(3L);
        }

        @Test
        @DisplayName("sin sesion no se lee ni se importa nada")
        void sinSesion() {
            when(acceso.usuarioActual()).thenReturn(null);
            assertThat(servicio.preview(conCsv("2026-08-01,C,-1\n")).isCorrect()).isFalse();
            assertThat(servicio.confirm(conCsv("2026-08-01,C,-1\n")).isCorrect()).isFalse();
            assertThat(servicio.profiles().isCorrect()).isTrue();
        }
    }

    @Nested
    @DisplayName("Columnas que no vienen o no cuadran")
    class ColumnasRaras {

        @Test
        @DisplayName("un indice de columna fuera del archivo no rompe: la fila se salta")
        void columnaFueraDeRango() {
            /* Le pasa a cualquiera que cuente las columnas desde 1 en vez de
               desde 0. Reventar con un IndexOutOfBounds seria un 500 sin
               explicacion; saltar la fila deja que la vista previa muestre
               "no se pudo leer nada" y se corrija el mapeo. */
            ImportarDTO dto = conCsv("2026-08-01,Compra,-45000\n");
            dto.setAmountColumn(9);

            ResultDTO r = servicio.preview(dto);
            assertThat(r.isCorrect()).isFalse();
            assertThat(r.getMessage()).contains("columnas");
        }

        @Test
        @DisplayName("sin columna de descripcion se lee igual")
        void sinDescripcion() {
            ImportarDTO dto = new ImportarDTO();
            dto.setCsv("2026-08-01,-45000\n");
            dto.setDateColumn(0);
            dto.setAmountColumn(1);
            dto.setHasHeader(false);

            Map<?, ?> objeto = (Map<?, ?>) servicio.preview(dto).getObject();
            assertThat(objeto.get("total")).isEqualTo(1);
        }

        @Test
        @DisplayName("la cabecera se salta cuando se dice que la hay")
        void saltaLaCabecera() {
            ImportarDTO dto = conCsv("fecha,concepto,valor\n2026-08-01,Compra,-45000\n");
            dto.setHasHeader(true);

            Map<?, ?> objeto = (Map<?, ?>) servicio.preview(dto).getObject();
            assertThat(objeto.get("total"))
                    .as("sin saltarla, la cabecera intenta interpretarse como un movimiento")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("un separador decimal dicho a mano manda sobre la deteccion")
        void separadorDicho() {
            ImportarDTO dto = conCsv("2026-08-01,Compra,\"45.50\"\n");
            dto.setDecimalSeparator(".");

            Map<?, ?> objeto = (Map<?, ?>) servicio.preview(dto).getObject();
            Map<?, ?> fila = (Map<?, ?>) ((List<?>) objeto.get("newRows")).get(0);
            assertThat((BigDecimal) fila.get("amount")).isEqualByComparingTo("45.50");
        }

        @Test
        @DisplayName("un formato de fecha dicho a mano se prueba primero")
        void formatoDeFechaDicho() {
            /* 03/04/2026 es ambiguo: 3 de abril o 4 de marzo. Si el banco
               exporta a la americana hay que poder decirlo, o medio extracto
               entra con el mes y el dia cambiados. */
            ImportarDTO dto = conCsv("03/04/2026,Compra,-45000\n");
            dto.setDatePattern("MM/dd/yyyy");

            Map<?, ?> objeto = (Map<?, ?>) servicio.preview(dto).getObject();
            assertThat(((Map<?, ?>) ((List<?>) objeto.get("newRows")).get(0)).get("date"))
                    .isEqualTo("2026-03-04");
        }
    }

    @Nested
    @DisplayName("Sugerir categoria")
    class SugerirCategoria {

        @Test
        @DisplayName("si el diccionario tiene algo parecido, lo propone")
        void proponeLoAprendido() {
            when(reglas.findByUserIdAndActiveTrue(YO)).thenReturn(List.of(
                    ImportRule.builder().id(1L).userId(YO).pattern("compra exito")
                            .categoryId(CATEGORIA).hits(5).active(true).build()));

            Map<?, ?> objeto = (Map<?, ?>) servicio.preview(
                    conCsv("2026-08-01,COMPRA EXITO CALLE 80,-45000\n")).getObject();
            Map<?, ?> fila = (Map<?, ?>) ((List<?>) objeto.get("newRows")).get(0);

            assertThat(fila.get("suggestedCategoryId")).isEqualTo(CATEGORIA);
        }

        @Test
        @DisplayName("si no se parece a nada, no propone nada en vez de inventar")
        void noInventa() {
            when(reglas.findByUserIdAndActiveTrue(YO)).thenReturn(List.of(
                    ImportRule.builder().id(1L).userId(YO).pattern("arriendo")
                            .categoryId(CATEGORIA).hits(5).active(true).build()));

            Map<?, ?> objeto = (Map<?, ?>) servicio.preview(
                    conCsv("2026-08-01,NETFLIX,-45000\n")).getObject();
            Map<?, ?> fila = (Map<?, ?>) ((List<?>) objeto.get("newRows")).get(0);

            assertThat(fila.get("suggestedCategoryId"))
                    .as("proponer cualquier cosa entrena a aceptar sin mirar, que es peor que "
                            + "no proponer")
                    .isNull();
        }

        @Test
        @DisplayName("cuando dos reglas compiten gana la mas usada")
        void ganaLaMasUsada() {
            when(reglas.findByUserIdAndActiveTrue(YO)).thenReturn(List.of(
                    ImportRule.builder().id(1L).userId(YO).pattern("pago nequi")
                            .categoryId(99L).hits(1).active(true).build(),
                    ImportRule.builder().id(2L).userId(YO).pattern("pago nequi")
                            .categoryId(CATEGORIA).hits(20).active(true).build()));

            Map<?, ?> objeto = (Map<?, ?>) servicio.preview(
                    conCsv("2026-08-01,PAGO NEQUI,-45000\n")).getObject();
            Map<?, ?> fila = (Map<?, ?>) ((List<?>) objeto.get("newRows")).get(0);

            assertThat(fila.get("suggestedCategoryId")).isEqualTo(CATEGORIA);
        }

        @Test
        @DisplayName("una fila sin descripcion no revienta el diccionario")
        void sinDescripcionNoRevienta() {
            when(reglas.findByUserIdAndActiveTrue(YO)).thenReturn(List.of(
                    ImportRule.builder().id(1L).userId(YO).pattern("arriendo")
                            .categoryId(CATEGORIA).hits(1).active(true).build()));

            ImportarDTO dto = new ImportarDTO();
            dto.setCsv("2026-08-01,-45000\n");
            dto.setDateColumn(0);
            dto.setAmountColumn(1);
            dto.setHasHeader(false);

            assertThat(servicio.preview(dto).isCorrect()).isTrue();
        }
    }

    @Nested
    @DisplayName("Confirmar")
    class Confirmar {

        @Test
        @DisplayName("sin filas seleccionadas no se importa nada")
        void sinFilas() {
            ResultDTO r = servicio.confirm(conCsv("2026-08-01,Compra,-45000\n"));
            assertThat(r.isCorrect()).isFalse();
            verify(movimientos, never()).save(any());
        }

        @Test
        @DisplayName("una fila que ya no esta en el archivo se omite y se dice")
        void filaQueYaNoEsta() {
            ImportarDTO dto = conCsv("2026-08-01,Compra,-45000\n");
            dto.setRows(List.of(new FilaAImportarDTO(99, CATEGORIA, null)));

            ResultDTO r = servicio.confirm(dto);
            assertThat(((Map<?, ?>) r.getObject()).get("created")).isEqualTo(0);
            assertThat(((Map<?, ?>) r.getObject()).get("skipped").toString())
                    .contains("ya no esta");
        }

        @Test
        @DisplayName("con una categoria ajena la fila se omite, no se cuela")
        void categoriaAjena() {
            when(categorias.findByIdAndActiveTrue(CATEGORIA)).thenReturn(Optional.of(categoria()));
            when(acceso.puedeVerCategoria(any())).thenReturn(false);

            ImportarDTO dto = conCsv("2026-08-01,Compra,-45000\n");
            dto.setRows(List.of(new FilaAImportarDTO(0, CATEGORIA, null)));

            servicio.confirm(dto);
            verify(movimientos, never()).save(any());
        }

        @Test
        @DisplayName("confirmar un pendiente ajeno no lo toca")
        void pendienteAjeno() {
            when(movimientos.findByIdAndActiveTrue(77L)).thenReturn(Optional.of(
                    Movement.builder().id(77L).userId(OTRO).categoryId(99L)
                            .date(LocalDate.now()).amount(BigDecimal.TEN)
                            .confirmed(false).active(true).build()));
            when(acceso.puedeVer(any(), any())).thenReturn(false);

            ImportarDTO dto = conCsv("2026-08-01,Compra,-45000\n");
            dto.setRows(List.of(new FilaAImportarDTO(0, null, 77L)));

            ResultDTO r = servicio.confirm(dto);
            assertThat(((Map<?, ?>) r.getObject()).get("confirmed")).isEqualTo(0);
            verify(movimientos, never()).save(any());
        }

        @Test
        @DisplayName("al importar aprende donde va esa descripcion")
        void aprende() {
            when(categorias.findByIdAndActiveTrue(CATEGORIA)).thenReturn(Optional.of(categoria()));
            when(reglas.findByUserIdAndPatternAndActiveTrue(any(), any())).thenReturn(Optional.empty());

            ImportarDTO dto = conCsv("2026-08-01,COMPRA EXITO,-45000\n");
            dto.setRows(List.of(new FilaAImportarDTO(0, CATEGORIA, null)));

            servicio.confirm(dto);

            ArgumentCaptor<ImportRule> regla = ArgumentCaptor.forClass(ImportRule.class);
            verify(reglas).save(regla.capture());
            assertThat(regla.getValue().getPattern())
                    .as("se guarda normalizado: el banco escribe en mayusculas y sin tildes, y "
                            + "comparar en crudo haria una entrada distinta por cada variante")
                    .isEqualTo("compra exito");
        }

        @Test
        @DisplayName("si ya sabia donde va, suma un uso en vez de duplicar la regla")
        void reglaExistente() {
            when(categorias.findByIdAndActiveTrue(CATEGORIA)).thenReturn(Optional.of(categoria()));
            when(reglas.findByUserIdAndPatternAndActiveTrue(any(), any()))
                    .thenReturn(Optional.of(ImportRule.builder()
                            .id(5L).userId(YO).pattern("compra exito").categoryId(CATEGORIA)
                            .hits(3).active(true).build()));

            ImportarDTO dto = conCsv("2026-08-01,COMPRA EXITO,-45000\n");
            dto.setRows(List.of(new FilaAImportarDTO(0, CATEGORIA, null)));

            servicio.confirm(dto);

            ArgumentCaptor<ImportRule> regla = ArgumentCaptor.forClass(ImportRule.class);
            verify(reglas).save(regla.capture());
            assertThat(regla.getValue().getHits())
                    .as("cuando dos reglas compiten por la misma descripcion gana la mas usada")
                    .isEqualTo(4);
        }
    }
}
