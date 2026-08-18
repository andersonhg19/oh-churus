package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Lo que se manda para VER una importacion antes de hacerla.
 *
 * La vista previa NO ESCRIBE NADA. Es la mitad del valor de un importador:
 * poder mirar que va a pasar con sesenta filas antes de que pase. Un
 * importador que escribe y despues te deja arreglar el desastre es peor que no
 * tener importador.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportarDTO {

    /** El contenido del CSV, tal cual. */
    @NotBlank(message = "es obligatorio")
    private String csv;

    /**
     * El perfil del banco, si ya lo guardaste. Con el no hace falta volver a
     * decir en que columna viene cada cosa.
     */
    private Long profileId;

    /** Si no hay perfil, el mapeo va aqui. */
    private String bankName;
    private Integer dateColumn;
    private Integer amountColumn;
    private Integer descriptionColumn;
    private Integer externalIdColumn;
    private String datePattern;
    private String decimalSeparator;
    private Boolean hasHeader;
    private Boolean invertSign;

    /** Si se guarda el mapeo para la proxima vez. */
    private Boolean rememberProfile;

    /** En que cuenta entran los movimientos. Si no viene, la de por defecto. */
    private Long accountId;

    /** Solo para confirmar: que filas entran y con que categoria. */
    private List<FilaAImportarDTO> rows;
}
