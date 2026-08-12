package com.ohchurus.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "oc_budget_movement", indexes = {
        @Index(name = "idx_movement_user_date_active", columnList = "userId, date, active"),
        @Index(name = "idx_movement_user_confirmed_active", columnList = "userId, confirmed, active"),
        @Index(name = "idx_movement_account_date_active", columnList = "accountId, date, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    private Long scheduledMovementId;

    /*
     * Periodo (primer dia del mes) al que pertenece esta ocurrencia de un
     * programado. Existe porque la clave de idempotencia no puede ser la fecha:
     * bastaba con mover la fecha de un pendiente a otro mes —o editar el
     * programado— para que el generador lo volviera a crear y el arriendo
     * apareciera dos veces. Con este campo la clave es (scheduledMovementId,
     * periodStart) y no depende de nada que el usuario pueda cambiar.
     */
    private LocalDate periodStart;

    private Long parentMovementId;

    /**
     * En que cuenta ocurrio. De donde salio la plata, o a donde entro.
     *
     * Se deja NULABLE a proposito, y no es dejadez. La migracion V4 reparte
     * todo lo existente a una cuenta "Sin asignar", asi que hoy no hay ni un
     * movimiento sin cuenta; pero si la columna fuera NOT NULL, cualquier
     * cliente antiguo que no mande accountId recibiria un error de base de
     * datos en vez de guardar. En su lugar el servicio pone la cuenta por
     * defecto cuando no viene ninguna, y una prueba comprueba que ningun
     * movimiento acaba sin cuenta. La garantia esta, pero puesta donde puede
     * dar una respuesta util en vez de una excepcion.
     */
    private Long accountId;

    /**
     * El movimiento de apertura: "esta cuenta empezo con esto, este dia".
     *
     * Es un movimiento y no un campo de la cuenta para que tenga FECHA. Con un
     * campo `saldoInicial` solo se puede calcular el saldo de hoy; con una
     * apertura fechada se puede calcular el de cualquier dia, que es lo que
     * hace falta para conciliar contra un extracto viejo.
     *
     * No cuenta como ingreso: aparecer 2.000.000 en "Ingresos" el dia que
     * empiezas a usar la app seria mentira. Lo excluye Computables.suma().
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isOpening = false;

    /**
     * Como se reparte este gasto entre varias personas. Nulo = no se reparte,
     * que es el caso de la inmensa mayoria.
     *
     * El importe del movimiento SIGUE SIENDO EL TOTAL aunque haya reparto: son
     * los 120.000 que salieron del banco. Lo que cambia es cuanto de eso cuenta
     * como gasto TUYO, y eso lo dicen las filas de MovementSplit.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private com.ohchurus.budget.enums.SplitMode splitMode;

    /**
     * "Te paso los 80.000 que te debo."
     *
     * No es ingreso ni gasto: la plata cambia de dueno, no aparece ni
     * desaparece. Lo excluye Computables.suma() por lo mismo que a la
     * transferencia.
     *
     * Y no se reutiliza isTransfer aunque se parezcan: la transferencia mueve
     * plata entre bolsillos de la MISMA persona y la liquidacion entre DOS
     * personas. Mezclarlas obligaria a distinguirlas despues por otro campo.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSettlement = false;

    /** Con quien se salda, cuando es una liquidacion. */
    private Long settledWithUserId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isTransfer = false;

    private Long transferPairId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean confirmed = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
