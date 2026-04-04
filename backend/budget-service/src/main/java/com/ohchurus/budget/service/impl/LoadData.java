package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.entity.*;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
public class LoadData implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final MovementRepository movementRepository;
    private final ScheduledMovementRepository scheduledMovementRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;

    @Value("${app.seed-data-enabled:true}")
    private boolean seedDataEnabled;

    public LoadData(CategoryRepository categoryRepository, MovementRepository movementRepository,
                    ScheduledMovementRepository scheduledMovementRepository,
                    HouseholdRepository householdRepository,
                    HouseholdMemberRepository householdMemberRepository) {
        this.categoryRepository = categoryRepository;
        this.movementRepository = movementRepository;
        this.scheduledMovementRepository = scheduledMovementRepository;
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
    }

    @Override
    public void run(String... args) {
        if (seedDataEnabled) {
            seedData();
        }
    }

    private void seedData() {
        if (categoryRepository.count() > 0) {
            log.info("Data already exists, skipping seed");
            return;
        }

        seedDemoCategories();
        Long householdId = seedHousehold();
        seedAnderson(householdId);
        seedSamy(householdId);
    }

    private Long seedHousehold() {
        // Crear household "Familia" con Anderson (owner) y Samy (member)
        Household household = householdRepository.save(
                Household.builder().name("Familia").active(true).build());
        householdMemberRepository.save(HouseholdMember.builder()
                .householdId(household.getId()).userId(3L).role("OWNER").active(true).build());
        householdMemberRepository.save(HouseholdMember.builder()
                .householdId(household.getId()).userId(4L).role("MEMBER").active(true).build());
        log.info("Seed: Household 'Familia' id={} with Anderson(owner) + Samy(member)", household.getId());
        return household.getId();
    }

    // ================================================================
    // DEMO USER (userId=2) - categorias para pruebas Karate
    // ================================================================
    private void seedDemoCategories() {
        Long uid = 2L;

        Category salario = saveCat(uid, "Salario", null, CategoryType.INCOME, "wallet", "#4CAF50");
        saveCat(uid, "Salario principal", salario.getId(), CategoryType.INCOME, "briefcase", "#66BB6A");
        saveCat(uid, "Freelance", salario.getId(), CategoryType.INCOME, "laptop", "#81C784");

        Category inversiones = saveCat(uid, "Inversiones", null, CategoryType.INCOME, "trending-up", "#2196F3");
        saveCat(uid, "Dividendos", inversiones.getId(), CategoryType.INCOME, "bar-chart", "#42A5F5");
        saveCat(uid, "Intereses", inversiones.getId(), CategoryType.INCOME, "percent", "#64B5F6");

        Category vivienda = saveCat(uid, "Vivienda", null, CategoryType.EXPENSE, "home", "#F44336");
        saveCat(uid, "Arriendo", vivienda.getId(), CategoryType.EXPENSE, "key", "#EF5350");
        saveCat(uid, "Servicios publicos", vivienda.getId(), CategoryType.EXPENSE, "zap", "#E57373");
        saveCat(uid, "Mantenimiento", vivienda.getId(), CategoryType.EXPENSE, "tool", "#EF9A9A");

        Category alimentacion = saveCat(uid, "Alimentacion", null, CategoryType.EXPENSE, "shopping-cart", "#FF9800");
        saveCat(uid, "Mercado", alimentacion.getId(), CategoryType.EXPENSE, "shopping-bag", "#FFA726");
        saveCat(uid, "Restaurantes", alimentacion.getId(), CategoryType.EXPENSE, "coffee", "#FFB74D");

        Category transporte = saveCat(uid, "Transporte", null, CategoryType.EXPENSE, "truck", "#9C27B0");
        saveCat(uid, "Combustible", transporte.getId(), CategoryType.EXPENSE, "droplet", "#AB47BC");
        saveCat(uid, "Transporte publico", transporte.getId(), CategoryType.EXPENSE, "navigation", "#BA68C8");

        Category entretenimiento = saveCat(uid, "Entretenimiento", null, CategoryType.EXPENSE, "film", "#E91E63");
        saveCat(uid, "Suscripciones", entretenimiento.getId(), CategoryType.EXPENSE, "tv", "#EC407A");
        saveCat(uid, "Salidas", entretenimiento.getId(), CategoryType.EXPENSE, "music", "#F06292");

        Category educacion = saveCat(uid, "Educacion", null, CategoryType.EXPENSE, "book", "#00BCD4");
        saveCat(uid, "Matricula", educacion.getId(), CategoryType.EXPENSE, "award", "#26C6DA");
        saveCat(uid, "Materiales", educacion.getId(), CategoryType.EXPENSE, "bookmark", "#4DD0E1");

        Category salud = saveCat(uid, "Salud", null, CategoryType.EXPENSE, "heart", "#795548");
        saveCat(uid, "Medicamentos", salud.getId(), CategoryType.EXPENSE, "thermometer", "#8D6E63");
        saveCat(uid, "Consultas", salud.getId(), CategoryType.EXPENSE, "activity", "#A1887F");

        log.info("Seed: 25 demo categories");
    }

    // ================================================================
    // ANDERSON (userId=3) - data real del presupuesto Excel
    // ================================================================
    private void seedAnderson(Long householdId) {
        Long uid = 3L;
        LocalDate today = LocalDate.of(2026, 3, 28);

        // Categorias COMPARTIDAS (household) - TODAS las del Excel son del nucleo familiar
        Category salario = saveSharedCat(householdId, uid, "Salario", null, CategoryType.INCOME, "briefcase", "#4CAF50");
        Category casa = saveSharedCat(householdId, uid, "Casa", null, CategoryType.EXPENSE, "home", "#F44336");
        Category deuda = saveSharedCat(householdId, uid, "Deuda", null, CategoryType.EXPENSE, "credit-card", "#E91E63");
        Category suscripcion = saveSharedCat(householdId, uid, "Suscripcion", null, CategoryType.EXPENSE, "tv", "#9C27B0");
        Category samy = saveSharedCat(householdId, uid, "Samy", null, CategoryType.EXPENSE, "heart", "#E91E63");
        Category andy = saveSharedCat(householdId, uid, "Andy", null, CategoryType.EXPENSE, "user", "#2196F3");
        Category mascota = saveSharedCat(householdId, uid, "Mascota", null, CategoryType.EXPENSE, "github", "#FF9800");
        Category carro = saveSharedCat(householdId, uid, "Carro", null, CategoryType.EXPENSE, "truck", "#795548");
        Category regalo = saveSharedCat(householdId, uid, "Regalo", null, CategoryType.EXPENSE, "gift", "#FF5722");
        saveSharedCat(householdId, uid, "Obra Social", null, CategoryType.EXPENSE, "users", "#00BCD4");

        // Categoria PERSONAL de Anderson (para pruebas)
        saveCat(uid, "Personal Andy", null, CategoryType.EXPENSE, "star", "#607D8B");

        // ========================================
        // MOVIMIENTOS del Excel - YA EJECUTADOS (confirmed=true)
        // ========================================
        // R3: Ingreso | Salario | Salario Samy | 3797000 | Ejecutado
        saveMov(uid, salario.getId(), today, "3797000", "Salario Samy", true);
        // R4: Ingreso | Salario | Salario Andy | 7969000 | Ejecutado
        saveMov(uid, salario.getId(), today, "7969000", "Salario Andy", true);
        // R5: Egreso | Deuda | Solventa (ultima Vez) | 1126200 | Ejecutado
        saveMov(uid, deuda.getId(), today, "1126200", "Solventa (ultima vez)", true);
        // R6: Egreso | Casa | Devolución fumigación Hexa | 150000 | Ejecutado
        saveMov(uid, casa.getId(), today, "150000", "Devolucion dinero fumigacion a Hexa", true);
        // R7: Egreso | Deuda | Devolución inicial vacaciones Hexa | 250000 | Ejecutado
        saveMov(uid, deuda.getId(), today, "250000", "Devolucion dinero inicial vacaciones a Hexa", true);
        // R8: Egreso | Deuda | Pago deuda celular Hexa | 800000 | Ejecutado
        saveMov(uid, deuda.getId(), today, "800000", "Pago deuda de celular a Hexa (ultima cuota)", true);
        // R9: Egreso | Deuda | Pago Alejandra | 500000 | Ejecutado
        saveMov(uid, deuda.getId(), today, "500000", "Pago Alejandra (ultima cuota)", true);
        // R10: Egreso | Andy | Plan celular mamá | 137319 | Ejecutado
        saveMov(uid, andy.getId(), today, "137319", "Pago de plan celular y celular de mi mama", true);
        // R18: Egreso | Deuda | Cuota Éxito | 260974 | Ejecutado
        saveMov(uid, deuda.getId(), LocalDate.of(2026, 3, 30), "260974", "Cuota Exito (ultima cuota)", true);
        // R21: Egreso | Casa | Factura de energía | 133850 | Ejecutado
        saveMov(uid, casa.getId(), today, "133850", "Factura de energia", true);
        // R27: Egreso | Andy | Pago tarjeta crédito | 175000 | Ejecutado
        saveMov(uid, andy.getId(), today, "175000", "Pago tarjeta de credito", true);
        // R32: Ingreso | Salario | Pago Paisa Code | 1400000 | Ejecutado
        saveMov(uid, salario.getId(), today, "1400000", "Pago Paisa Code", true);

        // ========================================
        // MOVIMIENTOS del Excel - PRESUPUESTADOS (confirmed=false, pendientes)
        // ========================================
        // R11: Arriendo - NO insertar manual, se genera desde programado recurrente
        // R12: Egreso | Suscripción | ChatGPT | 80000 | Presupuestado
        saveMov(uid, suscripcion.getId(), today, "80000", "ChatGPT", false);
        // R13: Egreso | Suscripción | Youtube | 42000 | Presupuestado
        saveMov(uid, suscripcion.getId(), today, "42000", "Youtube", false);
        // R14: Egreso | Deuda | Pago Daniel | 3000000 | Presupuestado
        saveMov(uid, deuda.getId(), today, "3000000", "Pago Daniel", false);
        // R15: Egreso | Deuda | Pago don Javier | 1000000 | Presupuestado
        saveMov(uid, deuda.getId(), today, "1000000", "Pago don Javier", false);
        // R16: Egreso | Andy | Cuota Celudmovil Andy | 190710 | Presupuestado
        saveMov(uid, andy.getId(), today, "190710", "Cuota Celudmovil Andy", false);
        // R17: Egreso | Samy | Cuota Celudmovil Samy | 160000 | Presupuestado
        saveMov(uid, samy.getId(), today, "160000", "Cuota Celudmovil Samy", false);
        // R19: Egreso | Samy | Pago plan celular | 35000 | Presupuestado
        saveMov(uid, samy.getId(), today, "35000", "Pago plan celular Samy", false);
        // R20: Egreso | Casa | Mercado | 1000000 | Presupuestado
        saveMov(uid, casa.getId(), today, "1000000", "Mercado", false);
        // R22: Egreso | Casa | Factura internet | 118991 | Presupuestado
        saveMov(uid, casa.getId(), today, "118991", "Factura de internet", false);
        // R23: Egreso | Casa | Factura gas | 30000 | Presupuestado
        saveMov(uid, casa.getId(), today, "30000", "Factura gas", false);
        // R24: Egreso | Casa | Natillera | 315000 | Presupuestado
        saveMov(uid, casa.getId(), today, "315000", "Natillera", false);
        // R25: Egreso | Carro | Pago SOAT | 1121400 | Presupuestado
        saveMov(uid, carro.getId(), today, "1121400", "Pago SOAT", false);
        // R26: Egreso | Regalo | Viaje vacaciones | 970000 | Presupuestado
        saveMov(uid, regalo.getId(), today, "970000", "Viaje de vacaciones", false);
        // R28: Egreso | Deuda | Pago hexa maquina doris | 324000 | Presupuestado
        saveMov(uid, deuda.getId(), today, "324000", "Pago Hexa maquina Doris", false);
        // R29: Egreso | Carro | Gasolina | 250000 | Presupuestado
        saveMov(uid, carro.getId(), today, "250000", "Gasolina", false);
        // R30: Egreso | Mascota | Baño Chapy | 70000 | Presupuestado
        saveMov(uid, mascota.getId(), today, "70000", "Bano Chapy", false);
        // R31: Egreso | Samy | Pasajes Samy | 200000 | Presupuestado
        saveMov(uid, samy.getId(), today, "200000", "Pasajes Samy", false);

        // ========================================
        // PROGRAMADOS recurrentes (solo los que se repiten mes a mes)
        // Salarios e ingresos no van como programados porque ya estan como ejecutados este mes
        // El arriendo NO se inserta como movimiento manual - se genera desde programado
        // ========================================
        saveSched(uid, casa.getId(), "Arriendo", "1450000", Frequency.MONTHLY, today, null, 28);

        log.info("Seed Anderson: 10 categories, 29 movements (12 confirmed + 17 pending), 1 scheduled");
    }

    // ================================================================
    // SAMY (userId=4)
    // ================================================================
    private void seedSamy(Long householdId) {
        Long uid = 4L;

        // Samy NO necesita categorias propias para las compartidas (Casa, Deuda, etc.)
        // Samy ve las compartidas del household + su personal
        saveCat(uid, "Personal Samy", null, CategoryType.EXPENSE, "star", "#E91E63");

        log.info("Seed Samy: 1 personal category (shared categories come from household)");
    }

    // ================================================================
    // HELPERS
    // ================================================================
    private Category saveCat(Long userId, String name, Long parentId, CategoryType type, String icon, String color) {
        return categoryRepository.save(Category.builder()
                .userId(userId).name(name).parentId(parentId)
                .type(type).icon(icon).color(color).active(true).build());
    }

    private Category saveSharedCat(Long householdId, Long userId, String name, Long parentId, CategoryType type, String icon, String color) {
        return categoryRepository.save(Category.builder()
                .userId(userId).name(name).parentId(parentId)
                .householdId(householdId)
                .type(type).icon(icon).color(color).active(true).build());
    }

    private void saveMov(Long userId, Long categoryId, LocalDate date, String amount, String description, boolean confirmed) {
        movementRepository.save(Movement.builder()
                .userId(userId).categoryId(categoryId).date(date)
                .amount(new BigDecimal(amount)).description(description)
                .confirmed(confirmed).active(true).build());
    }

    private void saveSched(Long userId, Long categoryId, String name, String amount,
                           Frequency frequency, LocalDate startDate, Integer durationMonths, Integer dayOfMonth) {
        scheduledMovementRepository.save(ScheduledMovement.builder()
                .userId(userId).categoryId(categoryId).name(name)
                .amount(new BigDecimal(amount)).frequency(frequency)
                .startDate(startDate).durationMonths(durationMonths)
                .dayOfMonth(dayOfMonth).active(true).build());
    }
}
