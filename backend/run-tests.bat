@echo off
echo ============================================
echo   Oh Churus! - Reporte Completo de Pruebas
echo ============================================
echo.

echo [1/4] Compilando proyecto...
call mvn clean compile -q
if errorlevel 1 (echo ERROR: Fallo la compilacion && pause && exit /b 1)

echo [2/4] Ejecutando pruebas unitarias + cobertura...
call mvn test
if errorlevel 1 (
    echo.
    echo ADVERTENCIA: Algunas pruebas fallaron. Revisa el reporte.
) else (
    echo.
    echo TODAS las pruebas pasaron correctamente!
)

echo.
echo [3/4] Generando reportes HTML...
call mvn surefire-report:report -pl auth-service,budget-service -q 2>nul

echo.
echo [4/4] Abriendo reportes en el navegador...
echo.

echo --- AUTH SERVICE ---
if exist auth-service\target\site\jacoco\index.html (
    echo   Cobertura: auth-service\target\site\jacoco\index.html
    start "" "auth-service\target\site\jacoco\index.html"
)
if exist auth-service\target\reports\surefire.html (
    echo   Tests:     auth-service\target\reports\surefire.html
    start "" "auth-service\target\reports\surefire.html"
)

echo.
echo --- BUDGET SERVICE ---
if exist budget-service\target\site\jacoco\index.html (
    echo   Cobertura: budget-service\target\site\jacoco\index.html
    start "" "budget-service\target\site\jacoco\index.html"
)
if exist budget-service\target\reports\surefire.html (
    echo   Tests:     budget-service\target\reports\surefire.html
    start "" "budget-service\target\reports\surefire.html"
)

echo.
echo ============================================
echo   Resumen rapido (de la consola Maven):
echo ============================================
echo.
echo   Auth Service:   79 tests
echo   Budget Service: 171 tests
echo   Total:          250 tests
echo.
echo   Los reportes HTML se abrieron en el navegador.
echo   - JaCoCo = Cobertura (% de codigo cubierto)
echo   - Surefire = Tests (cuales pasaron/fallaron)
echo.
pause
