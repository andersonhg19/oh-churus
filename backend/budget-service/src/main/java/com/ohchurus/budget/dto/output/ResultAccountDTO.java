package com.ohchurus.budget.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultAccountDTO {

    private Long id;
    private Long userId;
    private String name;
    private String kind;
    private String icon;
    private String color;
    private Long householdId;
    private Boolean isDefault;

    /**
     * Los dos saldos van SIEMPRE juntos y con estos nombres.
     *
     * `balance` es lo confirmado: lo que el banco deberia decir, y con lo que
     * se concilia. `projectedBalance` incluye lo pendiente: en que quedaria la
     * cuenta si todo lo anotado llega a ocurrir.
     *
     * Se mandan los dos porque ensenar uno solo obliga a la pantalla a elegir,
     * y la eleccion se acaba haciendo distinta en cada pantalla. Ninguno de los
     * dos se guarda: los calcula SaldoDeCuenta en cada peticion.
     */
    private BigDecimal balance;
    private BigDecimal projectedBalance;
}
