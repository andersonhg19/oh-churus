package com.ohchurus.fasting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "oc_fasting_achievement", indexes = {
        @Index(name = "idx_achievement_user", columnList = "userId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String code; // e.g. STREAK_7, STREAK_30, HOURS_100

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(length = 10)
    private String icon;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime unlockedAt;
}
