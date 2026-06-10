Feature: Dashboard API Tests

  Background:
    * url baseUrl
    * def loginResult = call read('classpath:karate-auth.feature')
    * def authToken = loginResult.authToken
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Get dashboard summary without referenceDate
    Given path '/v1/dashboard/summary'
    And request { userId: 2, budgetStartDay: 1 }
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
    And request { userId: 2, budgetStartDay: 1, referenceDate: '2026-03-15' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.periodStart == '#notnull'
    And match response.object.periodEnd == '#notnull'

  Scenario: Get dashboard by category
    Given path '/v1/dashboard/by-category'
    And request { userId: 2, budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'

  Scenario: Get dashboard by category with referenceDate
    Given path '/v1/dashboard/by-category'
    And request { userId: 2, budgetStartDay: 1, referenceDate: '2026-03-15' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'

  Scenario: Get dashboard trend
    Given path '/v1/dashboard/trend'
    And request { userId: 2, budgetStartDay: 1 }
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
    And request { userId: 2, budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'

  Scenario: Get dashboard pending with referenceDate
    Given path '/v1/dashboard/pending'
    And request { userId: 2, budgetStartDay: 1, referenceDate: '2026-03-15' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'

  Scenario: Dashboard with budgetStartDay 31 (handles short months)
    Given path '/v1/dashboard/summary'
    And request { userId: 2, budgetStartDay: 31 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.periodStart == '#notnull'

  Scenario: Dashboard summary validation - userId required
    Given path '/v1/dashboard/summary'
    And request { budgetStartDay: 1 }
    When method POST
    # Sin userId, el security filter puede retornar 400 o el endpoint 403
    Then assert responseStatus == 400 || responseStatus == 403

  Scenario: Get split summary
    Given path '/v1/dashboard/split-summary'
    And request { userId: 2, budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
