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
}
