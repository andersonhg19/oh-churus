package com.ohchurus.fasting.entity;

import com.ohchurus.fasting.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "oc_fasting_session", indexes = {
        @Index(name = "idx_session_user_status", columnList = "userId, status, active"),
        @Index(name = "idx_session_user_start", columnList = "userId, startTime, active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FastingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long planConfigId;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime targetEndTime;

    private LocalDateTime actualEndTime;

    @Column(nullable = false)
    private Integer fastingHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
