package com.ohchurus.budget.dto.input;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una fila que el usuario decidio importar, ya con su categoria.
 *
 * Se manda el NUMERO de fila y no la fila entera: el CSV vuelve a viajar en la
 * confirmacion, asi que reenviar los datos interpretados permitiria que lo que
 * se guarda no sea lo que se vio en la vista previa. Con el numero, el
 * servidor vuelve a leer exactamente lo mismo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FilaAImportarDTO {

    private Integer row;

    private Long categoryId;

    /**
     * Cuando la fila casa con un pendiente que genero una recurrencia, aqui
     * viene su id: en vez de crear un movimiento nuevo se CONFIRMA ese. Sin
     * esto el pendiente quedaria colgando para siempre y el gasto contado dos
     * veces.
     */
    private Long confirmsMovementId;
}
