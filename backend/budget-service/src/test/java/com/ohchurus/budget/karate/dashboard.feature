Feature: Dashboard API Tests

  Background:
    * url baseUrl
    * def loginResult = call read('classpath:karate-auth.feature')
    * def authToken = loginResult.authToken
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Get dashboard summary
    Given path '/v1/dashboard/summary'
    And request { userId: 2, budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.totalIncome == '#number'
    And match response.object.totalExpense == '#number'
    And match response.object.pendingCount == '#number'
    And match response.object.balance == '#number'
    And match response.object.periodStart == '#notnull'
    And match response.object.periodEnd == '#notnull'

  Scenario: Get dashboard by category
    Given path '/v1/dashboard/by-category'
    And request { userId: 2, budgetStartDay: 1 }
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

  Scenario: Get dashboard pending
    Given path '/v1/dashboard/pending'
    And request { userId: 2, budgetStartDay: 1 }
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
