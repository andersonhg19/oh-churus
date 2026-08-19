package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovementFilterDTO {

    private Long userId;
    private Long categoryId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean confirmed;
    @Min(0)
    private int page = 0;
    /**
     * El tope de la pagina.
     *
     * ESTABA EN 100 Y HABIA PANTALLAS PIDIENDO 200. El resultado no era un
     * error visible: el validador rechazaba la peticion, el
     * @RestControllerAdvice la convertia en un `correct: false`, la pantalla
     * comprobaba `if (res.correct)` y no entraba. Las barras de "presupuesto
     * vs real" del resumen NO FUNCIONARON NUNCA, y no habia forma de notarlo
     * porque la dona y los totales de al lado si funcionan.
     *
     * El mismo fallo aparecio dos veces el mismo dia —tambien en la pantalla
     * de importacion, con las categorias— asi que el numero se sube en los
     * TRES filtros a la vez: tres topes distintos son tres cosas que recordar,
     * y ya se demostro que no se recuerdan.
     *
     * 500 y no "sin limite": una pagina sin tope se trae la tabla entera a
     * memoria el dia que los datos crezcan. Con dos personas, un mes son unas
     * decenas de movimientos; 500 sobra y sigue siendo un techo.
     *
     * Lo ata LasPantallasCabenEnLaPaginaTest, que pide desde el HTTP lo que
     * piden las pantallas de verdad.
     */
    @Min(1) @Max(500)
    private int size = 10;
}
