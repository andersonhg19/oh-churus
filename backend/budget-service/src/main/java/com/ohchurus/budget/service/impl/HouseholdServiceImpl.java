package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class HouseholdServiceImpl {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;

    public HouseholdServiceImpl(HouseholdRepository householdRepository,
                                 HouseholdMemberRepository memberRepository) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
    }

    public ResultDTO create(String name, Long ownerUserId) {
        try {
            Household household = Household.builder().name(name).active(true).build();
            household = householdRepository.save(household);

            HouseholdMember owner = HouseholdMember.builder()
                    .householdId(household.getId())
                    .userId(ownerUserId)
                    .role("OWNER")
                    .active(true)
                    .build();
            memberRepository.save(owner);

            log.info("Household created: id={}, name={}, owner={}", household.getId(), name, ownerUserId);
            return new ResultDTO(household);
        } catch (Exception e) {
            log.error("Error creating household: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error creating household", 500);
        }
    }

    public ResultDTO addMember(Long householdId, Long userId) {
        try {
            /* Solo el dueno del hogar invita. Antes no se comprobaba NADA:
               cualquiera enviaba {householdId: 1, userId: <el suyo>} y entraba
               en la casa de otra pareja, viendo todas sus finanzas compartidas.
               Los householdId son consecutivos, asi que se adivinan probando. */
            if (!esDuenoDelHogar(householdId)) {
                return new ResultDTO(false, "Household not found", 404);
            }
            if (memberRepository.existsByHouseholdIdAndUserIdAndActiveTrue(householdId, userId)) {
                return new ResultDTO(false, "User is already a member", 400);
            }
            HouseholdMember member = HouseholdMember.builder()
                    .householdId(householdId)
                    .userId(userId)
                    .role("MEMBER")
                    .active(true)
                    .build();
            memberRepository.save(member);
            log.info("Member added: household={}, user={}", householdId, userId);
            return new ResultDTO(member);
        } catch (Exception e) {
            log.error("Error adding member: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error adding member", 500);
        }
    }

    public ResultDTO removeMember(Long householdId, Long userId) {
        try {
            if (!esDuenoDelHogar(householdId)) {
                return new ResultDTO(false, "Household not found", 404);
            }
            var member = memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(householdId, userId);
            if (member.isEmpty()) {
                return new ResultDTO(false, "Member not found", 404);
            }
            if ("OWNER".equals(member.get().getRole())) {
                return new ResultDTO(false, "Cannot remove the household owner", 400);
            }
            member.get().setActive(false);
            memberRepository.save(member.get());
            return new ResultDTO(true, "Member removed", 0);
        } catch (Exception e) {
            return new ResultDTO(false, "Error removing member", 500);
        }
    }

    /** ¿Quien pide esto es el OWNER de ese hogar? */
    private boolean esDuenoDelHogar(Long householdId) {
        Long yo = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();
        if (yo == null || householdId == null) return false;
        return memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(householdId, yo)
                .map(m -> "OWNER".equals(m.getRole()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public ResultDTO getByUser(Long userId) {
        try {
            List<HouseholdMember> memberships = memberRepository.findByUserIdAndActiveTrue(userId);
            List<Map<String, Object>> result = memberships.stream().map(m -> {
                Household h = householdRepository.findByIdAndActiveTrue(m.getHouseholdId()).orElse(null);
                List<HouseholdMember> members = memberRepository.findByHouseholdIdAndActiveTrue(m.getHouseholdId());
                Map<String, Object> map = new HashMap<>();
                map.put("householdId", m.getHouseholdId());
                map.put("name", h != null ? h.getName() : "");
                map.put("role", m.getRole());
                map.put("memberCount", members.size());
                map.put("members", members.stream().map(mem -> {
                    Map<String, Object> memMap = new HashMap<>();
                    memMap.put("userId", mem.getUserId());
                    memMap.put("role", mem.getRole());
                    return memMap;
                }).collect(Collectors.toList()));
                return map;
            }).collect(Collectors.toList());
            return new ResultDTO(result);
        } catch (Exception e) {
            return new ResultDTO(false, "Error getting households", 500);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> getHouseholdIds(Long userId) {
        return memberRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(HouseholdMember::getHouseholdId)
                .collect(Collectors.toList());
    }
}
