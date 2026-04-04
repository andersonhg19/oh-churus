package com.ohchurus.fasting.repository;

import com.ohchurus.fasting.entity.WaterLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WaterLogRepository extends JpaRepository<WaterLog, Long> {
    Optional<WaterLog> findByUserIdAndLogDate(Long userId, LocalDate logDate);
    List<WaterLog> findByUserIdAndLogDateBetweenOrderByLogDateAsc(Long userId, LocalDate start, LocalDate end);
}
