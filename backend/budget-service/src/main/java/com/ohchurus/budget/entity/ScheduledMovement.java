package com.ohchurus.budget.entity;

import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.enums.WeekendPolicy;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "oc_budget_scheduled_movement", indexes = {
        @Index(name = "idx_scheduled_user_active", columnList = "userId, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    private Integer durationMonths;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    private Integer dayOfMonth;

    /*
     * El patron "el tercer viernes": weekOfMonth = 3, dayOfWeek = 5 (ISO, lunes
     * es 1). Asi se paga la nomina en Colombia, y no hay forma de decirlo con
     * un dia del mes porque cambia de fecha cada mes.
     *
     * Van los dos o ninguno, y cuando estan mandan sobre dayOfMonth. El
     * ordinal 5 significa "el ultimo": el quinto viernes de un mes que solo
     * tiene cuatro es el cuarto, nunca el primero del mes siguiente.
     */
    private Integer weekOfMonth;

    private Integer dayOfWeek;

    /*
     * Que hacer si la ocurrencia cae sabado o domingo. Nulo se lee como KEEP:
     * los programados que existian antes de que esto se pudiera elegir no
     * pueden cambiar de fecha por un despliegue.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private WeekendPolicy weekendPolicy = WeekendPolicy.KEEP;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
