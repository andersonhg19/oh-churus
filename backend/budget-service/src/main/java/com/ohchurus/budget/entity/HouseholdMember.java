package com.ohchurus.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "oc_budget_household_member", indexes = {
        @Index(name = "idx_hm_user", columnList = "userId"),
        @Index(name = "idx_hm_household", columnList = "householdId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long householdId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "MEMBER"; // OWNER, MEMBER

    /* Como llama el hogar a esta persona ("Samy"). Ver V10: budget-service no
       puede leer el nombre de la cuenta del otro, y sin esto no habia forma de
       decir en pantalla quien paga que. */
    @Column(length = 50)
    private String displayName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
