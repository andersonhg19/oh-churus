package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de /v1/household/invite.
 *
 * Se invita por CORREO, no por id: la pantalla pedia el id de fila de la base
 * de datos ("Ej: 4") y nadie conoce el suyo, asi que el nucleo familiar —una
 * de las tres patas del producto— no se podia usar.
 *
 * El id del invitado no viene del cliente: lo resuelve el servidor contra
 * auth-service. Quien invita sale del token y tiene que ser el OWNER.
 */
@Getter
@Setter
@NoArgsConstructor
public class InvitacionPorCorreoDTO {

    @jakarta.validation.constraints.NotNull(message = "es obligatorio")
    private Long householdId;

    @NotBlank(message = "es obligatorio")
    @Email(message = "no es un correo valido")
    @Size(max = 150, message = "no puede superar los 150 caracteres")
    private String email;
}
