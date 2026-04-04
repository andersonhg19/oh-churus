package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/household")
public class HouseholdController {

    private final HouseholdServiceImpl householdService;

    public HouseholdController(HouseholdServiceImpl householdService) {
        this.householdService = householdService;
    }

    @PostMapping(value = "/create", produces = "application/json")
    public ResponseEntity<ResultDTO> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(householdService.create(name, userId));
    }

    @PostMapping(value = "/add-member", produces = "application/json")
    public ResponseEntity<ResultDTO> addMember(@RequestBody Map<String, Object> body) {
        Long householdId = Long.valueOf(body.get("householdId").toString());
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(householdService.addMember(householdId, userId));
    }

    @PostMapping(value = "/remove-member", produces = "application/json")
    public ResponseEntity<ResultDTO> removeMember(@RequestBody Map<String, Object> body) {
        Long householdId = Long.valueOf(body.get("householdId").toString());
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(householdService.removeMember(householdId, userId));
    }

    @PostMapping(value = "/by-user", produces = "application/json")
    public ResponseEntity<ResultDTO> getByUser(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(householdService.getByUser(userId));
    }
}
