package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.MovementFilterDTO;
import com.ohchurus.budget.dto.input.MovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;

import java.time.LocalDate;

public interface MovementService {

    ResultDTO saveAndUpdate(MovementSaveDTO dto);

    ResultDTO getById(Long id);

    ResultDTO getAll(MovementFilterDTO filter);

    ResultDTO delete(Long id);

    ResultDTO confirm(Long id);

    /** Suelta plata de un pendiente del plan (todo si monto es nulo) sin tocar el presupuesto. */
    ResultDTO liberar(Long id, java.math.BigDecimal monto);
    ResultDTO deshacerLiberacion(Long id);

    /** Confirma varios pendientes; todo o nada. montos[i] nulo = el valor previsto. */
    ResultDTO confirmarVarios(java.util.List<Long> ids, java.util.List<java.math.BigDecimal> montos);

    /** Cambia quien paga un pendiente de categoria compartida. */
    ResultDTO reasignarPagador(Long id, Long nuevoPagador);

    ResultDTO confirmWithAmount(Long id, java.math.BigDecimal newAmount);

    ResultDTO getByPeriod(Long userId, LocalDate startDate, LocalDate endDate);

    ResultDTO getChildren(Long parentId);
}
