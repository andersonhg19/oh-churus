package com.ohchurus.auth.repository;

import com.ohchurus.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByNameAndActiveTrue(String name);

    Optional<Role> findByName(String name);

    List<Role> findAllByActiveTrueOrderByNameAsc();

    boolean existsByName(String name);
}
