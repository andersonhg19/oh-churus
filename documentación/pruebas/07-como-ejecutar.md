# Como Ejecutar las Pruebas

---

## Backend - Pruebas Unitarias y de Controlador

```bash
# Desde la raiz del backend
cd backend

# TODOS los tests de TODOS los servicios
mvn test

# Solo auth-service
mvn test -pl auth-service

# Solo budget-service
mvn test -pl budget-service

# Un test especifico
mvn test -pl auth-service -Dtest=UserServiceImplTest

# Con reporte de cobertura JaCoCo
mvn test jacoco:report -pl auth-service
# El reporte HTML queda en: target/site/jacoco/index.html

# Compilar sin tests (para verificar que compila)
mvn clean package -DskipTests
```

### Ver reporte de cobertura
Despues de ejecutar `mvn test jacoco:report`, abrir en el navegador:
- `backend/auth-service/target/site/jacoco/index.html`
- `backend/budget-service/target/site/jacoco/index.html`

---

## Backend - Pruebas Karate (Integracion)

**Prerequisito:** Los servicios deben estar corriendo.

```bash
# 1. Levantar los servicios con Docker
docker-compose up -d

# 2. Esperar a que esten saludables
docker-compose ps   # Verificar que todos digan "healthy"

# 3. Ejecutar pruebas Karate
mvn test -pl auth-service -Dtest=KarateRunnerTest
mvn test -pl budget-service -Dtest=KarateRunnerTest
```

**Nota:** Las pruebas Karate hacen requests reales al servidor, por eso necesitan los servicios corriendo.

---

## Frontend - Pruebas Jest

```bash
cd frontend

# TODOS los tests
npm test

# Con reporte de cobertura
npx jest --coverage

# Con cobertura de TODO el proyecto (no solo archivos importados)
npx jest --coverage \
  --collectCoverageFrom='src/**/*.{ts,tsx}' \
  --collectCoverageFrom='!src/**/__tests__/**' \
  --collectCoverageFrom='!src/types/**'

# Un archivo especifico
npx jest src/screens/__tests__/DashboardScreen.test.tsx

# Solo tests que matchean un patron
npx jest --testPathPattern="service"

# En modo watch (util para desarrollo)
npx jest --watch
```

---

## Resultados Esperados

### Backend - auth-service
```
Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Cobertura: 91.9%
```

### Backend - budget-service
```
Tests run: 180, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Cobertura: 91.3%
```

### Frontend
```
Test Suites: 30 passed, 30 total
Tests:       179 passed, 179 total
Cobertura Statements: 74.3%
```

---

## Resolucion de Problemas Comunes

**"Tests fail with NoClassDefFoundError"**
-> Ejecutar `mvn clean compile test-compile` antes de `mvn test`

**"Karate tests fail with connection refused"**
-> Los servicios no estan corriendo. Ejecutar `docker-compose up -d` primero.

**"Frontend tests fail with module not found"**
-> Ejecutar `npm install` en la carpeta frontend.

**"JaCoCo report not generated"**
-> Asegurarse de ejecutar `mvn test jacoco:report` (ambos goals).
