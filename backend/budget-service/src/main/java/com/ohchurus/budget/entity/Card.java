package com.ohchurus.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Una tarjeta de credito: un medio de pago que deja la compra como deuda.
 *
 * No guarda saldo. Lo que se debe es la suma de las compras hechas con ella
 * que aun no se han pagado (Movement.cardId con cardPaid = false): asi el
 * saldo nunca se desincroniza de las compras que lo explican.
 */
@Entity
@Table(name = "oc_budget_card", indexes = {
        @Index(name = "idx_card_user_active", columnList = "userId, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quien la usa y la paga. */
    @Column(nullable = false)
    private Long userId;

    /** El hogar que la ve en Deudas; null = solo su dueno. */
    private Long householdId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 7)
    private String color;

    /** Dia del mes en que se paga, si se sabe. Solo informativo. */
    private Integer paymentDay;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
