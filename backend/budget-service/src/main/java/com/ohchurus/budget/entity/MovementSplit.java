package com.ohchurus.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * La parte de un gasto que le toca a una persona.
 *
 * LA REGLA DE ORO, que es la razon de que esta tabla exista:
 *
 *   Un gasto de 120.000 que pagaste tu y se reparte entre tres personas son
 *   120.000 EN TU CUENTA —porque eso es lo que salio de tu banco y tiene que
 *   cuadrar con el extracto— pero solo 40.000 EN TU CATEGORIA, porque eso es
 *   lo que gastaste tu. Los otros 80.000 no son un gasto tuyo: son un derecho
 *   de cobro.
 *
 * Meter los 120.000 en la categoria es lo que hace la app hoy, y por eso el
 * presupuesto miente cada vez que alguien pone la cuenta del restaurante: se
 * come el mes entero de "Restaurantes" con plata que le van a devolver.
 *
 * POR QUE SE GUARDAN LOS DOS VALORES
 * ----------------------------------
 * `shareValue` es lo que la persona ESCRIBIO (2 participaciones, 30 %,
 * 45.000...) y `computedAmount` es la plata que sale de ahi. Con solo el
 * calculado no se puede recalcular al cambiar el importe del gasto; con solo
 * el escrito habria que recalcular en cada lectura, y los redondeos no siempre
 * dan lo mismo. Se guardan los dos y `computedAmount` es la verdad para
 * cualquier suma.
 */
@Entity
@Table(name = "oc_budget_movement_split", indexes = {
        @Index(name = "idx_split_movement", columnList = "movementId"),
        @Index(name = "idx_split_user_active", columnList = "userId, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovementSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long movementId;

    /** A quien le toca esta parte. Puede ser el que pago o cualquier otro. */
    @Column(nullable = false)
    private Long userId;

    /** Lo que la persona escribio: participaciones, porcentaje o importe. */
    @Column(precision = 15, scale = 4)
    private BigDecimal shareValue;

    /** La plata que le toca. Esta es la que suma. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal computedAmount;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
