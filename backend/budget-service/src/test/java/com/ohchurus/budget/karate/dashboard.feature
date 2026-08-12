Feature: Dashboard API Tests

  Background:
    * url baseUrl
    * def hoy = java.time.LocalDate.now() + ''

  Scenario: Get dashboard summary without referenceDate
    Given path '/v1/dashboard/summary'
    And request { userId: '#(userId)', budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.totalIncome == '#number'
    And match response.object.totalExpense == '#number'
    And match response.object.balance == '#number'
    And match response.object.budgetTotal == '#number'
    And match response.object.pendingCount == '#number'
    And match response.object.pendingAmount == '#number'
    And match response.object.periodStart == '#notnull'
    And match response.object.periodEnd == '#notnull'

  Scenario: Get dashboard summary with referenceDate
    Given path '/v1/dashboard/summary'
    And request { userId: '#(userId)', budgetStartDay: 1, referenceDate: '2026-03-15' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.periodStart == '2026-03-01'
    And match response.object.periodEnd == '2026-03-31'

  Scenario: Get dashboard by category
    Given path '/v1/dashboard/by-category'
    And request { userId: '#(userId)', budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'

  Scenario: Get dashboard by category with referenceDate
    Given path '/v1/dashboard/by-category'
    And request { userId: '#(userId)', budgetStartDay: 1, referenceDate: '2026-03-15' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'

  Scenario: Get dashboard trend
    Given path '/v1/dashboard/trend'
    And request { userId: '#(userId)', budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.changePercentage == '#number'
    And match response.object.currentIncome == '#number'
    And match response.object.currentExpense == '#number'
    And match response.object.previousIncome == '#number'
    And match response.object.previousExpense == '#number'
    And match response.object.currentPeriodStart == '#notnull'
    And match response.object.currentPeriodEnd == '#notnull'
    And match response.object.previousPeriodStart == '#notnull'
    And match response.object.previousPeriodEnd == '#notnull'

  Scenario: Get dashboard pending
    Given path '/v1/dashboard/pending'
    And request { userId: '#(userId)', budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'

  Scenario: Get dashboard pending with referenceDate
    Given path '/v1/dashboard/pending'
    And request { userId: '#(userId)', budgetStartDay: 1, referenceDate: '2026-03-15' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'

  Scenario: Dashboard with budgetStartDay 31 (handles short months)
    Given path '/v1/dashboard/summary'
    And request { userId: '#(userId)', budgetStartDay: 31, referenceDate: '2026-02-10' }
    When method POST
    Then status 200
    And match response.correct == true
    # Febrero no tiene 31: el periodo empieza el 31 de enero, no revienta ni se
    # corre al 1 de febrero.
    And match response.object.periodStart == '2026-01-31'

  Scenario: El userId del cuerpo se ignora y manda el token
    # Este escenario comprobaba antes que pedir el panel SIN userId era un error
    # 400. Eso describe una aplicacion que ya no existe: el userId del cuerpo se
    # sigue aceptando por compatibilidad con el frontend, pero no se usa. Lo que
    # hay que vigilar hoy es lo contrario —que mandar el id de otra persona no
    # sirva para ver su panel— asi que el escenario pasa a cubrir eso.
    * def nombreCat = 'Panel_' + java.lang.System.currentTimeMillis()
    Given path '/v1/categories/save'
    And request { userId: '#(userId)', name: '#(nombreCat)', type: 'EXPENSE' }
    When method POST
    Then status 200
    * def categoriaId = response.object.id

    Given path '/v1/movements/save'
    And request { userId: '#(userId)', categoryId: '#(categoriaId)', date: '#(hoy)', amount: 777, description: 'Gasto propio' }
    When method POST
    Then status 200
    And match response.correct == true

    # Se pide el panel con el id de un usuario que no soy yo: la respuesta tiene
    # que seguir siendo la MIA (mi gasto de 777 esta ahi).
    Given path '/v1/dashboard/summary'
    And request { userId: 999999999, budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And assert response.object.totalExpense >= 777

  Scenario: Get split summary
    Given path '/v1/dashboard/split-summary'
    And request { userId: '#(userId)', budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
