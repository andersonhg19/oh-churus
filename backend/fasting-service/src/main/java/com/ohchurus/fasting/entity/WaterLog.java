package com.ohchurus.fasting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "oc_fasting_water_log", indexes = {
        @Index(name = "idx_water_user_date", columnList = "userId, logDate")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WaterLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate logDate;

    @Column(nullable = false)
    @Builder.Default
    private Integer glasses = 0; // number of glasses (250ml each)

    @Column(nullable = false)
    @Builder.Default
    private Integer goalGlasses = 8; // daily goal

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
