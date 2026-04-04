Feature: Category Management API Tests

  Background:
    * url baseUrl
    * def loginResult = call read('classpath:karate-auth.feature')
    * def authToken = loginResult.authToken
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Get category tree for demo user
    Given path '/v1/categories/tree'
    And request { userId: 2 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[]'

  Scenario: Create a root category
    * def catName = 'Test_' + java.lang.System.currentTimeMillis()
    Given path '/v1/categories/save'
    And request { userId: 2, name: '#(catName)', type: 'EXPENSE', icon: 'test', color: '#FF0000' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.name == '#(catName)'
    And match response.object.type == 'EXPENSE'

  Scenario: Create a child category
    * def parentName = 'Parent_' + java.lang.System.currentTimeMillis()
    Given path '/v1/categories/save'
    And request { userId: 2, name: '#(parentName)', type: 'INCOME', icon: 'star', color: '#00FF00' }
    When method POST
    Then status 200
    And match response.correct == true
    * def parentId = response.object.id

    * def childName = 'Child_' + java.lang.System.currentTimeMillis()
    Given path '/v1/categories/save'
    And request { userId: 2, name: '#(childName)', type: 'INCOME', parentId: '#(parentId)', icon: 'leaf', color: '#0000FF' }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.parentId == parentId

  Scenario: Get category type list
    Given path '/v1/categories/type-list'
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object == '#[2]'
    And match response.object[*].key contains 'INCOME'
    And match response.object[*].key contains 'EXPENSE'

  Scenario: Get all categories paginated
    Given path '/v1/categories/all'
    And request { userId: 2, page: 0, size: 50 }
    When method POST
    Then status 200
    And match response.correct == true
    And match response.object.page == 0
    And match response.object.list == '#[]'
