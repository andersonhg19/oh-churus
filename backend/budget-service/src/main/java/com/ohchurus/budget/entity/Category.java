package com.ohchurus.budget.entity;

import com.ohchurus.budget.enums.CategoryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "oc_budget_category", indexes = {
        @Index(name = "idx_category_user_active", columnList = "userId, active"),
        @Index(name = "idx_category_user_parent_active", columnList = "userId, parentId, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    private Long parentId;

    @Column(length = 50)
    private String icon;

    @Column(length = 7)
    private String color;

    private Long householdId; // null = personal, value = shared household category

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType type;

    /**
     * "Es dinero que me van a devolver."
     *
     * Una categoria marcada asi NO descuenta su sobregiro del total a repartir
     * del mes siguiente: se queda esperando el reembolso.
     *
     * El nombre importa. Se llama por su caso de uso y no "excluida del
     * arrastre" porque nadie sabe si quiere lo segundo; en cambio todo el mundo
     * sabe si le van a devolver la plata. El caso real: pusiste la cuenta del
     * almuerzo del equipo y la empresa te reembolsa — sin esto, ese mes tu
     * presupuesto entero aparece roto por una plata que ni era tuya.
     *
     * Cuando el gasto se REPARTE (ola 3.2) esto no hace falta: en tu categoria
     * solo entra tu parte. El interruptor cubre el caso de haber pagado el
     * total sin nadie con quien repartirlo dentro de la app.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean reimbursable = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
