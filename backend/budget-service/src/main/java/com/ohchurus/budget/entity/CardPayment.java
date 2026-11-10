package com.ohchurus.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un pago hecho a una tarjeta: cuanto y con que fecha. No es un gasto nuevo. */
@Entity
@Table(name = "oc_budget_card_payment", indexes = {
        @Index(name = "idx_card_payment_card", columnList = "cardId, paymentDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long cardId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
