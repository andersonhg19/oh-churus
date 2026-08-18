package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.ImportProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportProfileRepository extends JpaRepository<ImportProfile, Long> {

    List<ImportProfile> findByUserIdAndActiveTrueOrderByBankNameAsc(Long userId);

    Optional<ImportProfile> findByIdAndActiveTrue(Long id);

    Optional<ImportProfile> findByUserIdAndBankNameIgnoreCaseAndActiveTrue(Long userId, String bankName);
}
