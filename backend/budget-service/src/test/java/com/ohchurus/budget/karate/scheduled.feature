Feature: Scheduled Movement API Tests

  Background:
    * url baseUrl
    * def loginResult = call read('classpath:karate-auth.feature')
    * def authToken = loginResult.authToken
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Create a scheduled movement
    Given path '/v1/scheduled/save'
    And request { userId: 2, categoryId: 1, name: 'Monthly Rent', amount: 1500000, frequency: 'MONTHLY', startDate: '2026-01-01', dayOfMonth: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.name == 'Monthly Rent'
    And match response.object.frequency == 'MONTHLY'

  Scenario: Create scheduled with duration
    Given path '/v1/scheduled/save'
    And request { userId: 2, categoryId: 1, name: 'Loan Payment', amount: 500000, frequency: 'MONTHLY', durationMonths: 12, startDate: '2026-01-01' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.durationMonths == 12
    And match response.object.endDate == '#notnull'

  Scenario: Get frequency list
    Given path '/v1/scheduled/frequency-list'
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[8]'

  Scenario: Generate pending movements
    Given path '/v1/scheduled/generate-pending'
    And request { userId: 2, budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true

  Scenario: Get all scheduled paginated
    Given path '/v1/scheduled/all'
    And request { userId: 2, page: 0, size: 10 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.page == 0
