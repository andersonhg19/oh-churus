Feature: User Management API Tests

  Background:
    * url baseUrl
    # Antes se entraba con admin@ohchurus.com y se leia/editaba el usuario 1 o
    # el 2. Eso ya no describe la aplicacion: no hay cuentas privilegiadas, y
    # /v1/users solo deja tocar TU propia ficha. Ademas esos usuarios solo
    # existen en la base de desarrollo. Cada ejecucion se crea el suyo.
    * def correo = 'karate_users_' + java.lang.System.currentTimeMillis() + '_' + java.util.UUID.randomUUID() + '@ohchurus.com'
    Given path '/v1/auth/register'
    And request { name: 'Karate Usuario', email: '#(correo)', password: 'Karate123!', budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    * def authToken = response.object.token
    * def miId = response.object.userId
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Get user by ID
    Given path '/v1/users/get/' + miId
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.id == miId
    And match response.object.name == 'Karate Usuario'
    And match response.object.email == '#(correo)'

  Scenario: Get non-existent user
    Given path '/v1/users/get/999999999'
    When method POST
    Then status 200
    And match response.correct == false
    And match response.errorCode == 103

  Scenario: Get all users paginated
    # /v1/users/all dejo de ser un directorio abierto: sin correo en el filtro
    # solo te devuelve a ti. Por eso se espera EXACTAMENTE un elemento, y que
    # sea el propio: si algun dia vuelve a listar a todo el mundo, esto se pone
    # rojo.
    Given path '/v1/users/all'
    And request { name: null, email: null, page: 0, size: 10 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.page == 0
    And match response.object.size == 10
    And match response.object.totalPage == '#number'
    And match response.object.list == '#[1]'
    And match response.object.list[0].id == miId

  Scenario: Update user budgetStartDay
    Given path '/v1/users/save'
    And request { id: '#(miId)', name: 'Karate Usuario', email: '#(correo)', budgetStartDay: 31 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.budgetStartDay == 31

  Scenario: Access without token should fail
    * configure headers = null
    Given path '/v1/users/get/' + miId
    When method POST
    Then status 403
