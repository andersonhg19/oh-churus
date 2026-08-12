function fn() {
  /*
   * El CI apunta al GATEWAY:
   *   -DbaseUrl=http://localhost:8820/BUDGET-SERVICE/oh-churus
   *   -DauthUrl=http://localhost:8820/AUTH-SERVICE/oh-churus
   * Probar contra el puerto del microservicio se salta el enrutado por Eureka y
   * el prefijo del gateway, o sea que prueba un camino que la app real nunca
   * recorre. Sin las propiedades, en local, se sigue hablando directo con cada
   * servicio.
   */
  var config = {
    baseUrl: karate.properties['baseUrl'] || 'http://localhost:8823/oh-churus',
    authUrl: karate.properties['authUrl'] || 'http://localhost:8821/oh-churus'
  };

  var sesion = karate.callSingle('classpath:karate-auth.feature', config);
  config.authToken = sesion.authToken;
  // Los datos de presupuesto llevan dueno (user_id es NOT NULL): los cuerpos
  // que crean necesitan saber quien es el usuario de esta ejecucion.
  config.userId = sesion.userId;
  karate.configure('headers', { Authorization: 'Bearer ' + sesion.authToken });

  return config;
}
