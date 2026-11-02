package com.ohchurus.budget.util;

import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Atrapar una excepcion dentro de un metodo @Transactional NO revierte: Spring
 * solo marca la transaccion cuando la excepcion cruza el proxy. Los servicios
 * que convierten el fallo en un ResultDTO de error llaman a esto para que lo
 * escrito a medias no se confirme. Sin transaccion (pruebas unitarias con
 * mocks) no hay nada que revertir.
 */
public final class Transacciones {
    private Transacciones() {}

    public static void revertir() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException sinTransaccion) {
            // nada que revertir
        }
    }
}
