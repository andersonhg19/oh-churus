Feature: Sesion para las pruebas de presupuesto

  # Se da de alta un usuario NUEVO en cada ejecucion en vez de dar por hecho que
  # existe uno de demostracion. La base de datos del CI arranca vacia, y un
  # escenario que depende de datos sembrados a mano no se puede repetir: pasa en
  # el portatil de quien los sembro y falla en todas partes.
  Scenario: Alta y sesion de un usuario recien creado
    * def correo = 'karate_presupuesto_' + java.lang.System.currentTimeMillis() + '@ohchurus.com'
    Given url authUrl + '/v1/auth/register'
    And request { name: 'Karate Presupuesto', email: '#(correo)', password: 'Karate123!', budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    * def authToken = response.object.token
    * def userId = response.object.userId
