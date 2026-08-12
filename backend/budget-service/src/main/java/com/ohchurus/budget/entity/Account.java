package com.ohchurus.budget.entity;

import com.ohchurus.budget.enums.AccountKind;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Una cuenta: donde esta la plata.
 *
 * FIJATE EN LO QUE NO HAY AQUI: no hay campo de saldo.
 *
 * El saldo NUNCA se guarda. Se calcula sumando los movimientos de la cuenta,
 * cada vez que se pregunta. Es la decision estructural de esta funcionalidad y
 * la copian Firefly III y Maybe por el mismo motivo: un saldo guardado es un
 * dato que puede quedar desincronizado de los movimientos que deberia resumir,
 * y cuando eso pasa no hay forma de saber cual de los dos miente. Guardarlo
 * obliga ademas a acordarse de actualizarlo en los siete sitios que crean,
 * editan, confirman, transfieren o borran un movimiento — y basta olvidar uno.
 *
 * El saldo inicial tampoco es un campo: es un MOVIMIENTO de apertura con su
 * fecha explicita (ver Movement.isOpening). Asi el saldo del 3 de marzo se
 * puede calcular igual que el de hoy, porque la apertura tiene fecha y entra
 * en la suma como cualquier otro movimiento. Con un campo `saldoInicial` sin
 * fecha, el historico no existe.
 */
@Entity
@Table(name = "oc_budget_account", indexes = {
        @Index(name = "idx_account_user_active", columnList = "userId, active"),
        @Index(name = "idx_account_household_active", columnList = "householdId, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountKind kind;

    @Column(length = 50)
    private String icon;

    @Column(length = 7)
    private String color;

    /** null = personal; con valor = cuenta compartida del hogar, igual que en Category. */
    private Long householdId;

    /**
     * La cuenta a la que van los movimientos que no se han clasificado.
     *
     * Existe por la migracion: nadie va a clasificar cuatrocientos movimientos
     * para poder volver a abrir la app. Hay como mucho una por usuario y no se
     * puede borrar mientras tenga movimientos, pero por lo demas es una cuenta
     * normal: se le puede cambiar el nombre y usarla como la cuenta de diario.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
