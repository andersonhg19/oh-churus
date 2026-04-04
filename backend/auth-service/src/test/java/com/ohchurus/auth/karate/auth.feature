Feature: Authentication API Tests

  Background:
    * url baseUrl

  Scenario: Login with valid credentials
    Given path '/v1/auth/login'
    And request { email: 'demo@ohchurus.com', password: 'Demo123!' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.token == '#notnull'
    And match response.object.email == 'demo@ohchurus.com'
    And match response.object.name == '#notnull'
    And match response.object.userId == '#notnull'
    * def authToken = response.object.token

  Scenario: Login with invalid password
    Given path '/v1/auth/login'
    And request { email: 'demo@ohchurus.com', password: 'wrongpassword' }
    When method POST
    Then status 200
    And match response.correct == false
    And match response.errorCode == 101

  Scenario: Login with non-existent email
    Given path '/v1/auth/login'
    And request { email: 'nonexistent@ohchurus.com', password: 'Password123!' }
    When method POST
    Then status 200
    And match response.correct == false
    And match response.errorCode == 101

  Scenario: Register new user
    * def randomEmail = 'karate_' + java.lang.System.currentTimeMillis() + '@ohchurus.com'
    Given path '/v1/auth/register'
    And request { name: 'Karate Test User', email: '#(randomEmail)', password: 'Karate123!' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.name == 'Karate Test User'

  Scenario: Register with duplicate email should fail
    Given path '/v1/auth/register'
    And request { name: 'Duplicate User', email: 'demo@ohchurus.com', password: 'Pass123!' }
    When method POST
    Then status 200
    And match response.correct == false
    And match response.errorCode == 102
