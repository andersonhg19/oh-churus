package com.ohchurus.auth.repository;

import com.ohchurus.auth.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE "
            + "(:actorUserId IS NULL OR a.actorUserId = :actorUserId) "
            + "AND (:entityType IS NULL OR a.entityType = :entityType) "
            + "AND (:desde IS NULL OR a.occurredAt >= :desde) "
            + "ORDER BY a.occurredAt DESC")
    Page<AuditLog> buscar(@Param("actorUserId") Long actorUserId,
                          @Param("entityType") String entityType,
                          @Param("desde") LocalDateTime desde,
                          Pageable pageable);

    /**
     * El borrado por retencion. Devuelve cuantas filas se fueron para que
     * quede escrito en el log: un borrado silencioso de datos historicos es
     * indistinguible de una perdida.
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.occurredAt < :limite")
    int borrarAnterioresA(@Param("limite") LocalDateTime limite);
}
