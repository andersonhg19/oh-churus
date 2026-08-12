package com.ohchurus.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "oc_budget_movement", indexes = {
        @Index(name = "idx_movement_user_date_active", columnList = "userId, date, active"),
        @Index(name = "idx_movement_user_confirmed_active", columnList = "userId, confirmed, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    private Long scheduledMovementId;

    /*
     * Periodo (primer dia del mes) al que pertenece esta ocurrencia de un
     * programado. Existe porque la clave de idempotencia no puede ser la fecha:
     * bastaba con mover la fecha de un pendiente a otro mes —o editar el
     * programado— para que el generador lo volviera a crear y el arriendo
     * apareciera dos veces. Con este campo la clave es (scheduledMovementId,
     * periodStart) y no depende de nada que el usuario pueda cambiar.
     */
    private LocalDate periodStart;

    private Long parentMovementId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isTransfer = false;

    private Long transferPairId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean confirmed = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
