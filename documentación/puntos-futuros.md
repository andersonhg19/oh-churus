# Oh Churus! - Puntos Futuros (Backlog)

## Módulos y funcionalidades pospuestas para fases posteriores del proyecto.

---

### 1. Módulo de Roles y Permisos
**Origen:** Fase 1 original
**Descripción:** Sistema de roles (ADMIN, USER) con tabla UserRole para asignar múltiples roles por usuario. Control de acceso por endpoint según rol.
**Entidades:**
- Role (id, name, active, createdAt, updatedAt)
- UserRole (id, userId, roleId)
**Impacto:** Modificar SecurityConfig para validar roles en endpoints. Agregar endpoint de gestión de roles.

---

### 2. Módulo de Configuraciones del Sistema (AppConfiguration)
**Origen:** Core Service original
**Descripción:** Tabla clave-valor para configuraciones globales de la app (moneda por defecto, decimales, nombre app, versión). CRUD completo con endpoint.
**Entidades:**
- AppConfiguration (id, key, value, description, active, createdAt, updatedAt)
**Notas:** Por ahora las configuraciones globales van en `application.properties`.

---

### 3. Preferencias por Usuario (currency, decimalPlaces)
**Origen:** Auth Service - simplificación Fase 1
**Descripción:** Permitir que cada usuario tenga su propia moneda y decimales. Campos a agregar en User:
- `currency` (String(10), DEFAULT 'COP') - código ISO de moneda
- `decimalPlaces` (Integer, DEFAULT 0) - cantidad de decimales a mostrar
**Impacto:** Actualmente estos valores son globales via `application.properties`. Migrar a User permite personalización por usuario.
**Notas:** `budgetStartDay` ya está en User. Solo falta currency y decimalPlaces.

---

### 4. Modo Offline / Caché Local
**Descripción:** Implementar almacenamiento local en el frontend con sincronización al reconectar. Útil para uso móvil sin conexión.

---

### 5. Notificaciones
**Descripción:** Servicio de notificaciones (email/push) para recordar movimientos pendientes, alertas de presupuesto excedido, etc.

---

### 6. Reportes y Exportación
**Descripción:** Generar reportes PDF/Excel de movimientos por período, distribución por categoría, tendencias mensuales.

---

### 7. Multi-moneda
**Descripción:** Soporte para múltiples monedas con tasas de cambio. Actualmente se maneja una sola moneda por usuario.

---

### 8. Audit Service
**Descripción:** Microservicio dedicado para registrar cambios en entidades (quién cambió qué y cuándo). Event-based con ApplicationEventPublisher.

---

### 9. Metas de Ahorro
**Descripción:** El usuario define metas (ej: "Vacaciones $2,000,000") y el sistema trackea el progreso. Visual con la ardilla llenando su nido.
