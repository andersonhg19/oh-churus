package com.ohchurus.auth.dto.input;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de un registro publico. Existe por seguridad, no por estilo.
 *
 * /v1/auth/register es la unica ruta abierta sin token. Antes recibia un
 * UserSaveDTO, que lleva campo "id", y el servicio interpretaba "trae id" como
 * "esto es una actualizacion". Consecuencia: cualquiera, SIN autenticarse,
 * podia enviar {"id":3,"email":"suyo@…","password":"…"} y quedarse con la
 * cuenta de otro cambiandole el correo y la contrasena.
 *
 * Este DTO no tiene id, asi que esa ruta ya no puede actualizar nada.
 * Actualizar un usuario vive solo en /v1/users/save, que exige token.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDTO {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;

    @Min(value = 1, message = "Budget start day must be between 1 and 31")
    @Max(value = 31, message = "Budget start day must be between 1 and 31")
    private Integer budgetStartDay;
}
