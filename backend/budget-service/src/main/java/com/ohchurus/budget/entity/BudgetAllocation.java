package com.ohchurus.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "oc_budget_allocation", indexes = {
        @Index(name = "idx_allocation_user_period", columnList = "userId, periodStart"),
        @Index(name = "idx_allocation_household_period", columnList = "householdId, periodStart"),
        @Index(name = "idx_allocation_category_period", columnList = "categoryId, periodStart")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long categoryId;

    private Long householdId;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal allocatedAmount;

    /* Aqui vivia `status`, que nacio para marcar asignaciones cerradas y nunca
       llego a significar nada: lo unico que lo ponia en algo distinto de
       'ACTIVE' era autoCloseExpired(), un metodo sin endpoint y sin @Scheduled
       al que no llamaba nadie, asi que la consulta que filtraba por
       status = 'ACTIVE' devolvia todas las filas. Lo quita la V7. */

    @Column(length = 255)
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
