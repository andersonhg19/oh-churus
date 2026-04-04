package com.ohchurus.fasting.repository;

import com.ohchurus.fasting.entity.FastingSession;
import com.ohchurus.fasting.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<FastingSession, Long> {

    Optional<FastingSession> findByUserIdAndStatusAndActiveTrue(Long userId, SessionStatus status);

    List<FastingSession> findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(
            Long userId, LocalDateTime start, LocalDateTime end);

    List<FastingSession> findByUserIdAndActiveTrueOrderByStartTimeDesc(Long userId);
}
