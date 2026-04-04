function fn() {
  var config = {
    baseUrl: 'http://localhost:8821/oh-churus'
  };

  // Try to get token for authenticated requests
  var token = karate.properties['token'] || '';
  if (token) {
    karate.configure('headers', { Authorization: 'Bearer ' + token });
  }

  return config;
}
