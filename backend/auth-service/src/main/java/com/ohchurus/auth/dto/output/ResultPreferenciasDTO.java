package com.ohchurus.auth.dto.output;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lo que el movil necesita para escribir un importe.
 *
 * Viene RESUELTO: el servidor ya decidio si manda lo que eligio la persona o
 * lo global. Mandar las dos cosas y que el movil las combine seria repartir la
 * misma regla entre dos sitios que se actualizan por separado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultPreferenciasDTO {

    private String currency;
    private Integer decimalPlaces;
    private String locale;

    /** Si esto viene de una eleccion propia o de la configuracion global. */
    private Boolean propia;
}
