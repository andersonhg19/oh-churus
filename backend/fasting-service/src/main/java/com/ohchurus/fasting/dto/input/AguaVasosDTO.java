package com.ohchurus.fasting.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de /v1/fasting/water/add. Sin glasses se anota un vaso, que es como
 * funcionaba con el Map. El nombre JSON no cambia.
 */
@Getter
@Setter
@NoArgsConstructor
public class AguaVasosDTO {

    @NotNull(message = "es obligatorio")
    private Long userId;

    private Integer glasses = 1;

    /* Un glasses:null explicito reventaba al desempaquetar el int; el Map lo
       trataba como ausente y anotaba uno. Se conserva. */
    public Integer getGlasses() {
        return glasses != null ? glasses : 1;
    }
}
