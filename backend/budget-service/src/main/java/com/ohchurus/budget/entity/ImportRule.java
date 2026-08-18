package com.ohchurus.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * El diccionario "donde pusiste esto la vez pasada".
 *
 * Se aprende solo: cada vez que confirmas una importacion y le asignas
 * categoria a una fila, se guarda su descripcion apuntando a esa categoria. La
 * siguiente vez, "COMPRA EXITO CALLE 80" ya viene clasificado.
 *
 * El patron se guarda NORMALIZADO (sin tildes, minusculas, sin signos; ver
 * Parecido.normalizar) porque el banco escribe en mayusculas y sin tildes, y
 * comparar en crudo convierte cada variante en una entrada distinta que no
 * sirve para nada.
 */
@Entity
@Table(name = "oc_budget_import_rule", indexes = {
        @Index(name = "idx_import_rule_user", columnList = "userId, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String pattern;

    @Column(nullable = false)
    private Long categoryId;

    /**
     * Cuantas veces se ha usado. No es estadistica: cuando dos reglas compiten
     * por la misma descripcion gana la mas usada, que es casi siempre la
     * correcta.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer hits = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
