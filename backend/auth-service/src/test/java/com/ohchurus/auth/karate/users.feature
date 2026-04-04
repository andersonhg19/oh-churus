Feature: User Management API Tests

  Background:
    * url baseUrl
    # Login first to get token
    Given path '/v1/auth/login'
    And request { email: 'admin@ohchurus.com', password: 'Admin123!' }
    When method POST
    Then status 200
    * def authToken = response.object.token
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Get user by ID
    Given path '/v1/users/get/1'
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.id == 1
    And match response.object.name == '#notnull'
    And match response.object.email == '#notnull'

  Scenario: Get non-existent user
    Given path '/v1/users/get/9999'
    When method POST
    Then status 200
    And match response.correct == false
    And match response.errorCode == 103

  Scenario: Get all users paginated
    Given path '/v1/users/all'
    And request { name: null, email: null, page: 0, size: 10 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.page == 0
    And match response.object.size == 10
    And match response.object.list == '#[]'
    And match response.object.totalPage == '#number'

  Scenario: Update user budgetStartDay
    Given path '/v1/users/save'
    And request { id: 2, name: 'Usuario Demo', email: 'demo@ohchurus.com', budgetStartDay: 31 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.budgetStartDay == 31

  Scenario: Access without token should fail
    * configure headers = { }
    Given path '/v1/users/get/1'
    When method POST
    Then status 403
