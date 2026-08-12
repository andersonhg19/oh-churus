package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Conciliar: "¿cuanto dice tu banco?".
 *
 * La conciliacion de este proyecto es pobre a proposito. No hay marcado
 * movimiento a movimiento ni estados intermedios como en Firefly III: hay UNA
 * pregunta y, si la respuesta no coincide con lo que la app calcula, un
 * movimiento de ajuste con la diferencia. Cubre el 90 % del valor —enterarte
 * de que te falta algo por anotar— con el 5 % de la complejidad.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileDTO {

    @NotNull(message = "es obligatorio")
    private Long accountId;

    /** Lo que dice el banco. */
    @NotNull(message = "es obligatorio")
    private BigDecimal realBalance;

    /** A que fecha lo dice. Si no viene, hoy. */
    private LocalDate date;

    /**
     * En falso solo calcula la diferencia y la devuelve, sin tocar nada. Sirve
     * para que la pantalla pueda ensenar "te faltan 45.000 por anotar" y que
     * sea la persona quien decida si eso se arregla con un ajuste o buscando
     * el gasto que se le paso.
     */
    private Boolean apply;
}
