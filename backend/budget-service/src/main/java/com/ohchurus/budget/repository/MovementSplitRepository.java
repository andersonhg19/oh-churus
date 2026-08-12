package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.MovementSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MovementSplitRepository extends JpaRepository<MovementSplit, Long> {

    List<MovementSplit> findByMovementIdAndActiveTrue(Long movementId);

    /**
     * Todas las partes de un lote de movimientos, en UNA consulta.
     *
     * Existe para que el panel no haga una consulta por fila. Con 40
     * movimientos en pantalla la diferencia entre esto y el bucle ingenuo son
     * 40 viajes a la base por cada refresco.
     */
    @Query("SELECT s FROM MovementSplit s WHERE s.active = true AND s.movementId IN :movimientos")
    List<MovementSplit> findDeVarios(@Param("movimientos") Collection<Long> movimientos);

    boolean existsByMovementIdAndActiveTrue(Long movementId);

    @Modifying
    @Query("UPDATE MovementSplit s SET s.active = false WHERE s.movementId = :movimiento")
    void desactivarDe(@Param("movimiento") Long movimiento);
}
