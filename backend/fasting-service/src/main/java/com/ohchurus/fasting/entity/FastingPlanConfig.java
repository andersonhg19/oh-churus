package com.ohchurus.fasting.entity;

import com.ohchurus.fasting.enums.PlanType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "oc_fasting_plan_config", indexes = {
        @Index(name = "idx_plan_user_active", columnList = "userId, active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FastingPlanConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType planType;

    @Column(nullable = false)
    private Integer fastingHours;

    @Column(nullable = false)
    private Integer eatingHours;

    @Column(length = 5)
    private String suggestedStartTime; // "20:00"

    @Column(nullable = false)
    @Builder.Default
    private Boolean remindersEnabled = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
