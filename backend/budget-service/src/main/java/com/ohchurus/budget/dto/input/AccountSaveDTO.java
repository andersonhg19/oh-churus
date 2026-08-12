package com.ohchurus.budget.dto.input;

import com.ohchurus.budget.enums.AccountKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * No hay userId, y no es un olvido: el dueno de una cuenta es quien la crea, y
 * eso lo dice el token. Anadirlo aqui pondria roja la prueba de arquitectura
 * LaIdentidadNoVuelveAlCuerpo, que existe precisamente para que este agujero
 * no se reabra copiando un DTO viejo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountSaveDTO {

    private Long id;

    @NotBlank(message = "es obligatorio")
    @Size(max = 100, message = "no puede superar los 100 caracteres")
    private String name;

    @NotNull(message = "es obligatorio (OWN o LIABILITY)")
    private AccountKind kind;

    @Size(max = 50, message = "no puede superar los 50 caracteres")
    private String icon;

    @Size(max = 7, message = "no puede superar los 7 caracteres")
    private String color;

    private Long householdId;

    /**
     * Saldo inicial y su fecha. Solo se miran AL CREAR.
     *
     * Al editar se ignoran a proposito: cambiar el saldo inicial de una cuenta
     * que ya tiene tres meses de movimientos reescribiria el pasado en
     * silencio. Si el saldo de partida estaba mal, se corrige editando el
     * movimiento de apertura, que se ve y tiene fecha, o conciliando.
     */
    private BigDecimal openingBalance;

    private LocalDate openingDate;
}
