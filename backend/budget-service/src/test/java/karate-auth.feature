Feature: Get auth token

  Scenario: Login to get token
    Given url 'http://localhost:8821/oh-churus/v1/auth/login'
    And request { email: 'demo@ohchurus.com', password: 'Demo123!' }
    When method POST
    Then status 200
    * def authToken = response.object.token
