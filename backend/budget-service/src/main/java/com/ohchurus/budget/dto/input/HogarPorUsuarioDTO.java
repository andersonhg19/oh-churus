package com.ohchurus.budget.dto.input;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de /v1/household/by-user.
 *
 * No pide nada: la identidad sale del token. Existe para que el endpoint deje
 * de recibir un Map generico y para que un cuerpo mal formado responda dentro
 * del contrato en vez de con el 400 de Spring.
 */
@Getter
@Setter
@NoArgsConstructor
public class HogarPorUsuarioDTO {

    /* El frontend lo sigue enviando. Se acepta y se ignora. */
    private Long userId;
}
