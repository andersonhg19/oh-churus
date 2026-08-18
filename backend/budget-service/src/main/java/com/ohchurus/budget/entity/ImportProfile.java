package com.ohchurus.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * En que columna del extracto viene cada cosa, recordado POR BANCO.
 *
 * El importador de Firefly III es una maravilla de configurabilidad y por eso
 * casi nadie llega al final. Aqui se hace la parte que da el 80 % del valor:
 * decir UNA VEZ que columna es la fecha, cual el importe y cual la
 * descripcion, y que la app lo recuerde.
 *
 * Por banco y no por usuario a secas porque la misma persona exporta de
 * Bancolombia y de Nequi, y los dos formatos no se parecen en nada.
 *
 * Las columnas se guardan por INDICE y no por nombre de cabecera: hay bancos
 * que exportan sin cabecera y otros que la cambian entre versiones del portal.
 */
@Entity
@Table(name = "oc_budget_import_profile", indexes = {
        @Index(name = "idx_import_profile_user", columnList = "userId, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String bankName;

    @Column(nullable = false)
    private Integer dateColumn;

    @Column(nullable = false)
    private Integer amountColumn;

    private Integer descriptionColumn;

    private Integer externalIdColumn;

    /** Como escribe las fechas ese banco. Nulo = se prueban los formatos comunes. */
    @Column(length = 40)
    private String datePattern;

    /** "," o ".". En Colombia conviven los dos segun el portal. */
    @Column(length = 1)
    private String decimalSeparator;

    @Column(nullable = false)
    @Builder.Default
    private Boolean hasHeader = true;

    /**
     * Hay bancos que exportan los gastos en positivo. Con esto se invierte el
     * signo al importar, en vez de pedirle al usuario que edite sesenta filas.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean invertSign = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
