package com.ohchurus.budget.dto.input;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * "De las ocurrencias atrasadas que me mostraste, crea estas."
 *
 * Es la otra mitad del tope de materializacion: sin una forma de aceptarlas, un
 * programado con muchas atrasadas se quedaria proponiendo lo mismo para
 * siempre. Se pueden aceptar todas o unas pocas; las que no se acepten se
 * vuelven a proponer en el siguiente refresco.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaterializeOccurrencesDTO {

    /* El tope de 200 es el mismo espiritu que el de materializacion: aceptar
       de una vez mas de lo que cabe en una pantalla no es revisar. */
    @NotEmpty(message = "es obligatorio")
    @Size(max = 200, message = "no puede superar las 200 ocurrencias por peticion")
    @Valid
    private List<OccurrenceRefDTO> occurrences;
}
