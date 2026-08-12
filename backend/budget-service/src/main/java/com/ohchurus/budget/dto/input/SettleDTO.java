package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** "Te paso lo que te debo." */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettleDTO {

    /** A quien le pagas. */
    @NotNull(message = "es obligatorio")
    private Long withUserId;

    /**
     * Cuanto. Opcional: si no viene, se salda el neto entero, que es lo que
     * quiere el 90 % de las veces. Se admite parcial porque a veces se paga a
     * plazos, y obligar a saldar todo o nada haria que la gente no lo anotara.
     */
    private BigDecimal amount;

    private LocalDate date;

    /** De que cuenta sale la plata. Si no viene, la de por defecto. */
    private Long accountId;
}
