Feature: Movement Management API Tests

  Background:
    * url baseUrl
    * def loginResult = call read('classpath:karate-auth.feature')
    * def authToken = loginResult.authToken
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Create a movement
    Given path '/v1/movements/save'
    And request { userId: 2, categoryId: 1, date: '2026-03-17', amount: 1500000, description: 'Test movement' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.amount == 1500000
    And match response.object.confirmed == true
    * def movementId = response.object.id

  Scenario: Get movement by ID
    # Create first
    Given path '/v1/movements/save'
    And request { userId: 2, categoryId: 1, date: '2026-03-17', amount: 500000 }
    When method POST
    Then status 200
    * def movementId = response.object.id

    # Get it
    Given path '/v1/movements/get/' + movementId
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.id == movementId

  Scenario: Get all movements paginated
    Given path '/v1/movements/all'
    And request { userId: 2, page: 0, size: 10 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.page == 0
    And match response.object.list == '#[]'

  Scenario: Delete movement (soft delete)
    # Create first
    Given path '/v1/movements/save'
    And request { userId: 2, categoryId: 1, date: '2026-03-17', amount: 100 }
    When method POST
    * def movId = response.object.id

    # Delete it
    Given path '/v1/movements/delete/' + movId
    When method POST
    Then status 200
    And match response.correct == true

    # Verify it's gone
    Given path '/v1/movements/get/' + movId
    When method POST
    Then status 200
    And match response.correct == false

  Scenario: Confirm a pending movement
    # Create unconfirmed movement
    Given path '/v1/movements/save'
    And request { userId: 2, categoryId: 1, date: '2026-03-17', amount: 250000, confirmed: false }
    When method POST
    * def pendingId = response.object.id

    # Confirm it
    Given path '/v1/movements/confirm/' + pendingId
    When method POST
    Then status 200
    And match response.correct == true

  Scenario: Get movements by period
    Given path '/v1/movements/by-period'
    And request { userId: 2, startDate: '2026-03-01', endDate: '2026-03-31' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'
