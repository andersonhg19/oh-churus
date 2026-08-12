package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Una linea del reparto: quien participa y con que valor.
 *
 * Que significa `value` depende del modo del gasto:
 *   EQUAL   se ignora (todos ponen lo mismo)
 *   SHARES  participaciones: 2 = paga por dos
 *   PERCENT porcentaje: 30 = el 30 %
 *   AMOUNT  el importe exacto en pesos
 *
 * Aqui SI viaja un userId, y no contradice la regla de que la identidad sale
 * del token: no dice quien HACE la peticion, dice a quien se le reparte. El
 * servicio comprueba que todos sean de un hogar tuyo antes de aceptarlo, que
 * es la comprobacion que de verdad importa.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SplitInputDTO {

    /*
     * Se llama participantId y no userId por una razon que no es cosmetica.
     *
     * La prueba de arquitectura LaIdentidadNoVuelveAlCuerpo prohibe un
     * @NotNull sobre userId en un DTO de entrada, y salto sobre este fichero.
     * Se podria haber anadido una excepcion; se prefirio cambiar el nombre,
     * porque la prueba estaba senalando algo cierto: un campo llamado userId
     * en un cuerpo de peticion se lee como "de quien es esto", y la respuesta
     * a eso solo la da el token. Aqui la pregunta es otra —"a quien se le
     * reparte"— y el nombre ahora lo dice.
     *
     * Que el participante sea de un hogar tuyo se comprueba en el servicio.
     */
    @NotNull(message = "es obligatorio")
    private Long participantId;

    private BigDecimal value;
}
