package com.ohchurus.auth.repository;

import com.ohchurus.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndActiveTrue(String email);

    Optional<User> findByIdAndActiveTrue(Long id);

    boolean existsByEmailAndActiveTrue(String email);

    boolean existsByEmailAndActiveTrueAndIdNot(String email, Long id);

    @Query("SELECT u FROM User u WHERE u.active = true " +
            "AND (:name IS NULL OR LOWER(CAST(u.name AS string)) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
            "AND (:email IS NULL OR LOWER(CAST(u.email AS string)) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))")
    Page<User> findAllWithFilters(@Param("name") String name,
                                  @Param("email") String email,
                                  Pageable pageable);
}
