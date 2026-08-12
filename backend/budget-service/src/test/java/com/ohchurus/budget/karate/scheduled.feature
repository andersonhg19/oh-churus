Feature: Scheduled Movement API Tests

  Background:
    * url baseUrl
    # Igual que en movements: la categoria se crea aqui. Un programado sobre una
    # categoria que no es tuya no se guarda, y la categoria 1 de la base de
    # desarrollo no existe en una instalacion limpia.
    * def nombreCat = 'ProgCat_' + java.lang.System.currentTimeMillis()
    Given path '/v1/categories/save'
    And request { userId: '#(userId)', name: '#(nombreCat)', type: 'EXPENSE', icon: 'test', color: '#FF0000' }
    When method POST
    Then status 200
    And match response.correct == true
    * def categoriaId = response.object.id

  Scenario: Create a scheduled movement
    Given path '/v1/scheduled/save'
    And request { userId: '#(userId)', categoryId: '#(categoriaId)', name: 'Monthly Rent', amount: 1500000, frequency: 'MONTHLY', startDate: '2026-01-01', dayOfMonth: 1 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.name == 'Monthly Rent'
    And match response.object.frequency == 'MONTHLY'

  Scenario: Create scheduled with duration
    Given path '/v1/scheduled/save'
    And request { userId: '#(userId)', categoryId: '#(categoriaId)', name: 'Loan Payment', amount: 500000, frequency: 'MONTHLY', durationMonths: 12, startDate: '2026-01-01' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.durationMonths == 12
    # Doce cuotas desde el 1 de enero terminan el 31 de diciembre, no el 1 de
    # enero siguiente: ese dia ya seria la cuota trece.
    And match response.object.endDate == '2026-12-31'

  Scenario: Get frequency list
    Given path '/v1/scheduled/frequency-list'
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[8]'

  Scenario: Generate pending movements
    Given path '/v1/scheduled/generate-pending'
    And request { userId: '#(userId)', budgetStartDay: 1 }
    When method POST
    Then status 200
    And match response.correct == true

  Scenario: Get all scheduled paginated
    Given path '/v1/scheduled/all'
    And request { userId: '#(userId)', page: 0, size: 10 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.page == 0
