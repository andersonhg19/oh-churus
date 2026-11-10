package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.CardPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardPaymentRepository extends JpaRepository<CardPayment, Long> {

    /** El ultimo pago de la tarjeta, para mostrarlo. */
    Optional<CardPayment> findFirstByCardIdOrderByPaymentDateDescIdDesc(Long cardId);
}
