Feature: Movement Management API Tests

  Background:
    * url baseUrl
    # Cada escenario necesita una categoria SUYA: la 1 de la base de desarrollo
    # no existe en una instalacion limpia, y si existiera seria de otra persona,
    # asi que el control de acceso devolveria "no encontrado".
    * def hoy = java.time.LocalDate.now() + ''
    * def nombreCat = 'MovCat_' + java.lang.System.currentTimeMillis()
    Given path '/v1/categories/save'
    And request { userId: '#(userId)', name: '#(nombreCat)', type: 'EXPENSE', icon: 'test', color: '#FF0000' }
    When method POST
    Then status 200
    And match response.correct == true
    * def categoriaId = response.object.id

  Scenario: Create a movement
    Given path '/v1/movements/save'
    And request { userId: '#(userId)', categoryId: '#(categoriaId)', date: '#(hoy)', amount: 1500000, description: 'Test movement' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.amount == 1500000
    And match response.object.confirmed == true

  Scenario: Get movement by ID
    # Create first
    Given path '/v1/movements/save'
    And request { userId: '#(userId)', categoryId: '#(categoriaId)', date: '#(hoy)', amount: 500000 }
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
    And request { userId: '#(userId)', page: 0, size: 10 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.page == 0
    And match response.object.list == '#[]'

  Scenario: Delete movement (soft delete)
    # Create first
    Given path '/v1/movements/save'
    And request { userId: '#(userId)', categoryId: '#(categoriaId)', date: '#(hoy)', amount: 100 }
    When method POST
    Then status 200
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
    And request { userId: '#(userId)', categoryId: '#(categoriaId)', date: '#(hoy)', amount: 250000, confirmed: false }
    When method POST
    Then status 200
    And match response.object.confirmed == false
    * def pendingId = response.object.id

    # Confirm it
    Given path '/v1/movements/confirm/' + pendingId
    When method POST
    Then status 200
    And match response.correct == true

  Scenario: Get movements by period
    Given path '/v1/movements/by-period'
    And request { userId: '#(userId)', startDate: '#(hoy)', endDate: '#(hoy)' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'
