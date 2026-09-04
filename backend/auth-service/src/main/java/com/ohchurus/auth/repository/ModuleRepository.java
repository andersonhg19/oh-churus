package com.ohchurus.auth.repository;

import com.ohchurus.auth.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModuleRepository extends JpaRepository<Module, Long> {

    Optional<Module> findByCode(String code);

    List<Module> findAllByActiveTrueOrderBySortOrderAscNameAsc();

    boolean existsByCode(String code);
}
