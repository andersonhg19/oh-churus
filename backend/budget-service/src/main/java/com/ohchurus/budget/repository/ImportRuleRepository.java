package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.ImportRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportRuleRepository extends JpaRepository<ImportRule, Long> {

    List<ImportRule> findByUserIdAndActiveTrue(Long userId);

    Optional<ImportRule> findByUserIdAndPatternAndActiveTrue(Long userId, String pattern);
}
