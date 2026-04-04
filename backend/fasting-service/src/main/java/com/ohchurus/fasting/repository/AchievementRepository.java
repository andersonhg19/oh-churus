package com.ohchurus.fasting.repository;

import com.ohchurus.fasting.entity.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    List<Achievement> findByUserIdOrderByUnlockedAtDesc(Long userId);
    boolean existsByUserIdAndCode(Long userId, String code);
}
