Feature: Authentication API Tests

  Background:
    * url baseUrl
    # Cada escenario se fabrica el usuario que necesita, con un correo distinto
    # por ejecucion. Antes se apoyaban en demo@ohchurus.com, que solo existe en
    # la base de datos de desarrollo: contra una instalacion limpia —la del
    # CI— estos escenarios no probaban nada, fallaban en el primer login.
    * def correo = 'karate_auth_' + java.lang.System.currentTimeMillis() + '_' + java.util.UUID.randomUUID() + '@ohchurus.com'

  Scenario: Register new user
    Given path '/v1/auth/register'
    And request { name: 'Karate Test User', email: '#(correo)', password: 'Karate123!' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.name == 'Karate Test User'
    And match response.object.email == '#(correo)'
    # Registrarse deja la sesion abierta: el frontend entra directo, sin pedir
    # la contrasena otra vez.
    And match response.object.token == '#notnull'
    And match response.object.userId == '#notnull'

  Scenario: Login with valid credentials
    Given path '/v1/auth/register'
    And request { name: 'Karate Login', email: '#(correo)', password: 'Karate123!' }
    When method POST
    Then status 200
    And match response.correct == true

    Given path '/v1/auth/login'
    And request { email: '#(correo)', password: 'Karate123!' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.token == '#notnull'
    And match response.object.email == '#(correo)'
    And match response.object.name == 'Karate Login'
    And match response.object.userId == '#notnull'

  Scenario: Login with invalid password
    Given path '/v1/auth/register'
    And request { name: 'Karate Password', email: '#(correo)', password: 'Karate123!' }
    When method POST
    Then status 200

    Given path '/v1/auth/login'
    And request { email: '#(correo)', password: 'wrongpassword' }
    When method POST
    Then status 200
    And match response.correct == false
    And match response.errorCode == 101

  Scenario: Login with non-existent email
    Given path '/v1/auth/login'
    And request { email: '#(correo)', password: 'Password123!' }
    When method POST
    Then status 200
    And match response.correct == false
    # Mismo error que la contrasena mala: distinguirlos diria quien tiene cuenta.
    And match response.errorCode == 101

  Scenario: Register with duplicate email should fail
    Given path '/v1/auth/register'
    And request { name: 'Karate Duplicado', email: '#(correo)', password: 'Karate123!' }
    When method POST
    Then status 200
    And match response.correct == true

    Given path '/v1/auth/register'
    And request { name: 'Otro Cualquiera', email: '#(correo)', password: 'Pass123!' }
    When method POST
    Then status 200
    And match response.correct == false
    And match response.errorCode == 102
