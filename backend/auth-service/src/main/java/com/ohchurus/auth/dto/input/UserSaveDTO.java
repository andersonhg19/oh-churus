package com.ohchurus.auth.dto.input;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSaveDTO {

    private Long id;

    @NotBlank(message = "es obligatorio")
    @Size(max = 100, message = "no puede superar los 100 caracteres")
    private String name;

    @NotBlank(message = "es obligatorio")
    @Email(message = "no tiene formato de correo valido")
    @Size(max = 150, message = "no puede superar los 150 caracteres")
    private String email;

    @Size(min = 6, max = 100, message = "debe tener entre 6 y 100 caracteres")
    private String password;

    @Min(value = 1, message = "debe estar entre 1 y 31")
    @Max(value = 31, message = "debe estar entre 1 y 31")
    private Integer budgetStartDay;

    /**
     * Moneda y decimales propios. Nulo significa "sigue con lo global", no
     * "borralo": para volver a lo global hay un endpoint aparte, porque un
     * nulo en una peticion de guardado casi siempre es un campo que el cliente
     * no envio, no una eleccion.
     */
    @Size(max = 10, message = "no puede superar los 10 caracteres")
    private String currency;

    @Min(value = 0, message = "debe estar entre 0 y 4")
    @Max(value = 4, message = "debe estar entre 0 y 4")
    private Integer decimalPlaces;
}
