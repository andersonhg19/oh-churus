package com.ohchurus.fasting.controller;

import com.ohchurus.fasting.dto.input.AguaMetaDTO;
import com.ohchurus.fasting.dto.input.AguaVasosDTO;
import com.ohchurus.fasting.dto.input.AyunoEdicionDTO;
import com.ohchurus.fasting.dto.input.AyunoFinDTO;
import com.ohchurus.fasting.dto.input.AyunoInicioDTO;
import com.ohchurus.fasting.dto.input.AyunoPeriodoDTO;
import com.ohchurus.fasting.dto.input.AyunoPlanDTO;
import com.ohchurus.fasting.dto.input.AyunoUsuarioDTO;
import com.ohchurus.fasting.dto.output.ResultDTO;
import com.ohchurus.fasting.service.impl.FastingServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/fasting")
public class FastingController {

    /*
     * La identidad sale del TOKEN, nunca del cuerpo.
     *
     * Hasta ahora los trece endpoints leian body.getUserId(), o sea que la
     * ponia el cliente: cambiando un numero se leia y se modificaba el ayuno,
     * el agua y los logros de otra persona. budget-service ya se habia
     * arreglado; este servicio se habia quedado atras, y con el la frase "la
     * identidad sale del JWT" era falsa en la mitad del producto.
     *
     * El campo userId sigue existiendo en los DTO por compatibilidad con el
     * frontend, que aun lo envia: simplemente ya no se mira.
     */

    /*
     * Los cuerpos ya no son Map<String, Object>.
     *
     * Se leian a mano con Long.valueOf(body.get("userId").toString()), asi que
     * un cuerpo vacio o un campo con el tipo cambiado reventaba con un
     * NullPointerException o un NumberFormatException: 500 con el error de
     * Spring, que el frontend no sabe leer. Los nombres JSON son los mismos.
     */

    private final FastingServiceImpl fastingService;

    public FastingController(FastingServiceImpl fastingService) {
        this.fastingService = fastingService;
    }

    // ===== PLAN CONFIG =====

    @PostMapping(value = "/plan/get", produces = "application/json")
    public ResponseEntity<ResultDTO> getPlan(@Valid @RequestBody AyunoUsuarioDTO body) {
        return ResponseEntity.ok(fastingService.getPlanConfig(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping(value = "/plan/save", produces = "application/json")
    public ResponseEntity<ResultDTO> savePlan(@Valid @RequestBody AyunoPlanDTO body) {
        return ResponseEntity.ok(fastingService.savePlanConfig(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId(), body.getPlanType(),
                body.getFastingHours(), body.getEatingHours(),
                body.getSuggestedStartTime(), body.getRemindersEnabled()));
    }

    @PostMapping(value = "/plan/presets", produces = "application/json")
    public ResponseEntity<ResultDTO> getPresets() {
        return ResponseEntity.ok(fastingService.getPresets());
    }

    // ===== SESSIONS =====

    @PostMapping(value = "/session/start", produces = "application/json")
    public ResponseEntity<ResultDTO> startFasting(@Valid @RequestBody AyunoInicioDTO body) {
        return ResponseEntity.ok(fastingService.startFasting(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId(), body.getStartTime()));
    }

    @PostMapping(value = "/session/stop", produces = "application/json")
    public ResponseEntity<ResultDTO> stopFasting(@Valid @RequestBody AyunoFinDTO body) {
        return ResponseEntity.ok(fastingService.stopFasting(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId(), body.getEndTime()));
    }

    @PostMapping(value = "/session/cancel", produces = "application/json")
    public ResponseEntity<ResultDTO> cancelFasting(@Valid @RequestBody AyunoUsuarioDTO body) {
        return ResponseEntity.ok(fastingService.cancelFasting(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping(value = "/session/active", produces = "application/json")
    public ResponseEntity<ResultDTO> getActiveSession(@Valid @RequestBody AyunoUsuarioDTO body) {
        return ResponseEntity.ok(fastingService.getActiveSession(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping(value = "/session/edit", produces = "application/json")
    public ResponseEntity<ResultDTO> editSession(@Valid @RequestBody AyunoEdicionDTO body) {
        return ResponseEntity.ok(fastingService.editSession(body.getSessionId(),
                body.getStartTime(), body.getEndTime()));
    }

    // ===== HISTORY =====

    @PostMapping(value = "/history/by-period", produces = "application/json")
    public ResponseEntity<ResultDTO> getHistory(@Valid @RequestBody AyunoPeriodoDTO body) {
        return ResponseEntity.ok(fastingService.getHistoryByPeriod(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId(),
                body.getBudgetStartDay(), body.getReferenceDate()));
    }

    @PostMapping(value = "/history/summary", produces = "application/json")
    public ResponseEntity<ResultDTO> getSummary(@Valid @RequestBody AyunoPeriodoDTO body) {
        return ResponseEntity.ok(fastingService.getSummaryByPeriod(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId(),
                body.getBudgetStartDay(), body.getReferenceDate()));
    }

    // ===== ACHIEVEMENTS =====

    @PostMapping(value = "/achievements", produces = "application/json")
    public ResponseEntity<ResultDTO> getAchievements(@Valid @RequestBody AyunoUsuarioDTO body) {
        return ResponseEntity.ok(fastingService.getAchievements(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId()));
    }

    // ===== WATER =====

    @PostMapping(value = "/water/today", produces = "application/json")
    public ResponseEntity<ResultDTO> getWaterToday(@Valid @RequestBody AyunoUsuarioDTO body) {
        return ResponseEntity.ok(fastingService.getWaterToday(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping(value = "/water/add", produces = "application/json")
    public ResponseEntity<ResultDTO> addWater(@Valid @RequestBody AguaVasosDTO body) {
        return ResponseEntity.ok(fastingService.addWater(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId(), body.getGlasses()));
    }

    @PostMapping(value = "/water/set-goal", produces = "application/json")
    public ResponseEntity<ResultDTO> setWaterGoal(@Valid @RequestBody AguaMetaDTO body) {
        return ResponseEntity.ok(fastingService.setWaterGoal(com.ohchurus.fasting.util.SecurityUtils.getAuthenticatedUserId(), body.getGoalGlasses()));
    }
}
