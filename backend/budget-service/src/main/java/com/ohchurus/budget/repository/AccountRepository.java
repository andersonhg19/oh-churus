package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByIdAndActiveTrue(Long id);

    List<Account> findByUserIdAndActiveTrueOrderByNameAsc(Long userId);

    Optional<Account> findByUserIdAndIsDefaultTrueAndActiveTrue(Long userId);

    /**
     * Las cuentas que puede ver una persona: las suyas, mas las compartidas de
     * los hogares a los que pertenece. Es el mismo criterio que ControlAcceso
     * aplica a las categorias, escrito una vez en SQL para no traerse la tabla
     * entera y filtrar en memoria.
     */
    @Query("SELECT a FROM Account a WHERE a.active = true "
            + "AND (a.userId = :userId "
            + "     OR (a.householdId IS NOT NULL AND a.householdId IN :householdIds)) "
            + "ORDER BY a.name ASC")
    List<Account> findVisibles(@Param("userId") Long userId,
                               @Param("householdIds") List<Long> householdIds);
}
