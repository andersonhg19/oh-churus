function fn() {
  var config = {
    baseUrl: 'http://localhost:8823/oh-churus'
  };

  var authResult = karate.callSingle('classpath:karate-auth.feature', config);
  if (authResult.authToken) {
    config.authToken = authResult.authToken;
    karate.configure('headers', { Authorization: 'Bearer ' + authResult.authToken });
  }

  return config;
}
