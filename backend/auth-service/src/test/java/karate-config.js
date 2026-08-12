function fn() {
  /*
   * El CI apunta al GATEWAY (-DbaseUrl=http://localhost:8820/AUTH-SERVICE/oh-churus).
   * Probar contra el puerto del microservicio se salta el enrutado por Eureka y
   * el prefijo del gateway, o sea que prueba un camino que la app real nunca
   * recorre. Sin la propiedad, en local, se sigue hablando directo con el
   * servicio.
   */
  var config = {
    baseUrl: karate.properties['baseUrl'] || 'http://localhost:8821/oh-churus'
  };

  // Try to get token for authenticated requests
  var token = karate.properties['token'] || '';
  if (token) {
    karate.configure('headers', { Authorization: 'Bearer ' + token });
  }

  return config;
}
