# CLAUDE.md

Este archivo proporciona orientacion a Claude Code (claude.ai/code) cuando trabaja con el codigo de este repositorio.

## Descripcion del Proyecto

BOPOS CLOUD V2 es un sistema empresarial de Punto de Venta (POS) construido sobre arquitectura de microservicios, con soporte para operaciones retail multi-pais y cumplimiento del estandar ARTS.

**Stack**: Java 17, Spring Boot 3.2.0, Spring Cloud 2023.0.0-RC1, MongoDB, Maven

**Estado**: Sistema en PRODUCCION - cualquier cambio puede afectar al cliente.

---

## Protocolo de Inicio de Sesion

**OBLIGATORIO al comenzar cada conversacion:**

1. Preguntar al usuario si va a trabajar sobre un proyecto/tarea ya existente o uno nuevo
2. Si es **existente**: solicitar el nombre del archivo de seguimiento (changelog) que se esta usando
3. Si es **nuevo**: crear un archivo de seguimiento en `C:\Users\ander\Documents\proyectos en curso\Bopos-core\` con nombre descriptivo (ej: `nombre-feature-changelog.md`)
4. Leer el archivo de seguimiento para retomar contexto antes de proponer cualquier accion

**Ruta base de archivos de seguimiento:** `C:\Users\ander\Documents\proyectos en curso\Bopos-core\`

---

## Control de Actividades

Cada proyecto o feature activo debe tener un **archivo de seguimiento** (changelog) que registre:

- Fases completadas con detalle de archivos modificados
- Estado actual (que esta hecho, que falta)
- Notas tecnicas relevantes (mapeo de campos, decisiones tomadas)
- Compilaciones exitosas con fecha

**Formato del archivo:**
```markdown
# Nombre del Proyecto - Registro de Mejoras y Actividades
## Estado: En desarrollo / Completado (rama git)
## Microservicios: nombre-servicio (puerto)
---
## Fase N: Descripcion (estado)
### Cambios completados:
- Archivos modificados con detalle
### Compilacion:
- Fecha y resultado
```

Este archivo es la **fuente de verdad** para retomar trabajo entre sesiones.

---

## Flujo de Trabajo Obligatorio

### Antes de tocar codigo: PLAN primero
```
1. Presentar PLAN con:
   - Archivos a modificar (rutas completas)
   - Que cambios exactos se haran
   - Impacto esperado y riesgos
   - Como se validara que funciona

2. ESPERAR aprobacion del usuario

3. Implementar (si es grande, dividir en iteraciones pequenas)
```

### Principios no negociables
- **Cambios minimos**: No refactorizar "porque si". Solo lo necesario.
- **Compatibilidad**: Todo debe ser compatible con lo existente.
- **No asumir**: Si algo no esta claro, PREGUNTAR antes de continuar.
- **Calidad > Velocidad**: Preferir iterar lento con certeza que rapido con riesgo.
- **Idioma**: Toda comunicacion con el usuario debe ser en espanol.

---

## Comandos de Construccion y Ejecucion

```bash
# Construir todos los servicios
mvn clean package

# Construir servicio especifico
mvn clean package -pl {nombre-servicio}

# Docker - desarrollo
docker-compose up --build -d

# Docker - cliente
docker-compose -p client -f ./docker-compose-client.yml --env-file .env.client up --build

# Logs de servicio
docker-compose logs -f {nombre-servicio}
```

**Nota:** Se usa `mvn` directamente (no hay wrapper `mvnw` en el proyecto).

### Orden de Inicio
1. `discovery-service` (8761) -> 2. `gateway-service` (8888) -> 3. `auth-service` (8881) -> 4. Demas servicios

---

## Manejo de Git y Renombrado de Archivos

**IMPORTANTE**: Para renombrar archivos, usar **siempre** `git mv`:

```bash
# CORRECTO - Git detecta el renombrado
git mv ArchivoViejo.java ArchivoNuevo.java

# INCORRECTO - Git ve como "eliminar + crear" (archivos fantasma en Sourcetree)
mv ArchivoViejo.java ArchivoNuevo.java
```

**Si ya ocurrio el problema** (archivos fantasma): ejecutar `git add -A` para limpiar.

**Renombrado masivo**: Usar script con `git mv` + actualizar contenido interno + `git add -A` al final.

---

## Arquitectura

```
Gateway (8888) -> Eureka (8761) -> Microservicios -> MQTT Broker / MongoDB
```

**Jerarquia Multi-Tenant**: Company -> Subsidiary -> Store -> PosMaster -> Till -> TillSession

### Servicios Principales

| Puerto | Servicio | Proposito |
|--------|----------|-----------|
| 8761 | discovery-service | Registro Eureka |
| 8888 | gateway-service | API Gateway |
| 8881 | auth-service | Autenticacion JWT |
| 8882 | administration-service | Empresas, tiendas, usuarios |
| 8886 | sale-service | Ventas, productos, precios |
| 8890 | promotion-service | Promociones |
| 8892 | product-arts-service | Productos ARTS, UOM, proveedores |
| 8896 | arts-inventory-service | Inventario ARTS |
| 8897 | dte-service-f1 | DTE Chile Factura1 |

---

## Componentes de Servicio Nuevo (Checklist)

Al crear una entidad nueva dentro de un servicio, verificar que incluya:

```
[] Entity (con campos de auditoria)
[] DTO Input (Request)
[] DTO Filter (si aplica)
[] DTO Output (Response - SIN campos de auditoria)
[] Repository (extends MongoRepository)
[] CustomRepository interface (si hay queries complejos)
[] CustomRepositoryImpl (sufijo Impl obligatorio)
[] Service interface
[] ServiceImpl (con metodos base: saveAndUpdate, getById, getAll)
[] Controller (versionado /v2/, todos los endpoints POST)
[] Registro en SecurityConfig
[] Mensajes en enum Message.Msj
[] Actualizar coleccion Postman
```

---

## Estandares de Mapeo (Entity <-> DTO)

### Opcion preferida: ModelMapper directo en ServiceImpl

```java
@Service
public class MiServiceImpl implements MiService {
    private final ModelMapper modelMapper;

    // Mapear Entity -> DTO
    ResultMiDTO dto = modelMapper.map(entity, ResultMiDTO.class);

    // Mapear lista
    List<ResultMiDTO> dtos = entities.stream()
        .map(e -> modelMapper.map(e, ResultMiDTO.class))
        .collect(Collectors.toList());
}
```

### Opcion alternativa: Mapper interface + MapperImpl

Algunos servicios (ej: product-arts-service) usan interfaces `@Mapper` con `MapperImpl` que internamente usan ModelMapper. **Respetar el patron existente** en cada servicio; no mezclar ambos patrones dentro del mismo microservicio.

---

## Estandares de DTOs y Respuestas

### Response DTO (Result*DTO)

Los campos de auditoria (`creation`, `lastUpdate`) van **solo en la Entity**, NO en el Response DTO.

### Convencion de Nombres en Response DTO

**Convencion preferida** para campos de nombres completados (nuevo codigo):
```java
private String companyName;      // NO: nameCompany
private String subsidiaryName;   // NO: nameSubsidiary
private String userName;         // NO: nameUser
private String productName;
private String supplierName;
private String storeName;
```

**Nota**: Algunos servicios existentes usan `nameCompany`, `nameSubsidiary`, `nameUser`. No renombrar campos existentes por retrocompatibilidad; usar la convencion preferida solo en codigo nuevo.

### Todos los Endpoints son POST

```java
@PostMapping(value = "/save", produces = "application/json")
@PostMapping(value = "/get/{id}", produces = "application/json")  // NO @GetMapping
@PostMapping(value = "/all", produces = "application/json")
```

### Formato de Respuesta Paginada

Usar `PageDTO` para listas paginadas:
```json
{
  "correct": true,
  "message": "OK",
  "errorCode": 0,
  "object": {
    "page": 0,
    "size": 10,
    "totalPage": 1,
    "list": [...]
  }
}
```

---

## Estandares de Desarrollo

### Campos Obligatorios en Entidades

```java
private String idCompany;              // Siempre obligatorio
private Long idSubsidiary;             // Cuando aplique (Long, no String)
private String idStore;                // Cuando aplique
private String idUser;                 // Auditoria
private LocalDateTime creation;        // Auditoria (solo Entity)
private LocalDateTime lastUpdate;      // Auditoria (solo Entity)
private Boolean active = true;
```

### Metodos Base Obligatorios en Servicios

- `saveAndUpdate(InputDTO, language)` - Crear o actualizar
- `getById(String id, language)` - Obtener por ID
- `getAll(FilterDTO, language)` - Listar con filtros paginados

### Validaciones: Usar Utils Centralizados

**Preferir siempre** `ValidationUtils` (o `ValidationUtil` segun el servicio) sobre validaciones manuales inline:

```java
// PREFERIDO - Centralizado con cache
validationUtils.requireCommonData(idCompany, idSubsidiary, idUser, language);
validationUtils.validateProductExists(idProduct, language);
validationUtils.validateSupplierExists(idSupplier, language);
validationUtils.validateStore(idStore, language);

// SOLO si no existe util disponible - validacion manual
if (dto.getIdCompany() == null || dto.getIdCompany().isBlank())
    return error("idCompany is required");
```

Si un servicio no tiene ValidationUtils, crearlo con el patron de arts-inventory-service o product-arts-service como referencia.

### Completado de Datos (Enriquecimiento de Responses)

**Preferir** `CompletionUtils` (o `CompleteDataUtil`) centralizado con caches:

```java
// PREFERIDO - Centralizado
completionUtils.enrich(resultDTO, language);       // Un solo DTO
completionUtils.enrichList(listDTO, language);      // Lista con cache optimizado

// Patron de interfaces para auto-deteccion:
// BaseCompletionDTO -> enriquece company, subsidiary, user
// SupplierCompletionDTO -> enriquece supplier name
// ProductCompletionDTO -> enriquece product name
```

Si un servicio no tiene CompletionUtils, implementar el completado directamente en ServiceImpl con caches `ConcurrentHashMap`.

### Mensajes de Error (Internacionalizacion)

1. Verificar si existe en el enum `Message.Msj` del microservicio
2. Si no existe, agregarlo al enum
3. Usar siempre el servicio de language para traduccion:

```java
getMessage(Message.Msj.subsidiaryNotFound.toString(), language)
// o
connectInternalApi.lngChargeMessage(Message.Msj.xxx.toString(), language)
```

**NO hardcodear mensajes** directamente en el codigo.

### Validacion de Unicidad Compuesta

```java
// En Repository
Optional<Entity> findByField1AndField2AndIdSubsidiaryAndActive(...);

// En ServiceImpl - validar antes de guardar
if (existing.isPresent() && !existing.get().getId().equals(existingId)) {
    return error(Message.Msj.duplicatedRegistry, language);
}
```

### Enums con Endpoint de Listado

Para enums expuestos via API:
```java
// Endpoint en Controller
@PostMapping(value = "/mi-tipo-option-list", produces = "application/json")
public ResultDTO miTipoOptionList(@RequestHeader(name = "lng") String language) {
    return service.miTipoOptionList(language);
}

// En ServiceImpl retorna List<Map<String, String>> con "key" y "name"
```

---

## Patrones de Codigo

### Respuesta Estandarizada
```java
new ResultDTO(data)                    // Exito
new ResultDTO(false, "mensaje", 120)   // Error
```

### Versionado API
URLs: `/{context-path}/v2/{recurso}` -> Ejemplo: `/rhi-bpc/v2/products`

### Repositorios Custom
```java
public interface ProductRepository extends MongoRepository<Product, String>, ProductCustomRepository {}
public interface ProductCustomRepository { /* queries custom */ }
public class ProductCustomRepositoryImpl implements ProductCustomRepository { /* DEBE terminar en Impl */ }
```

### Variables de Entorno
Siempre con valores por defecto: `${MONGODB_SERVER:localhost}`

### ChangeLog
Los servicios deben registrar cambios: `changeLogService.register(entity, action, userId);`

---

## Convenciones de Base de Datos

- **Bases de datos**: `rhi_{servicio}_db`
- **Colecciones**: snake_case plural (`products`, `sales_transactions`)
- **Campos**: camelCase (`createdAt`, `idCompany`)
- **IDs referencia**: Prefijo `id` + entidad (`idCompany`, `idStore`)

---

## Validacion y Pruebas

Antes de entregar cambios:

1. **Compilacion exitosa** - `mvn clean package -pl {servicio}` debe pasar
2. **Validar casos felices** - Flujo normal funciona
3. **Validar casos de error** - IDs invalidos, nulls, duplicados
4. **Validar bordes** - Listas vacias, campos opcionales
5. **Documentar** en el archivo de seguimiento

---

## Postman

Todo endpoint nuevo o modificado debe:
1. Agregarse/actualizarse en la coleccion Postman del proyecto
2. Incluir ejemplo de request/response
3. Mantener estructura por microservicio

### Gestion de Coleccion Postman entre iteraciones

**OBLIGATORIO** al trabajar con la coleccion Postman:

1. El archivo oficial es `Bopoos Cloud V2.postman_collection.json` (ubicado en `Document/Postman/collection/`)
2. Cuando el usuario importe una nueva version desde Postman, esta reemplaza al archivo actual
3. **Antes de actualizar**, comparar la version importada contra `Bopoos Cloud V2.postman_collection_old.json` (version previa organizada por Claude) para asegurar que no se pierdan endpoints o cambios de iteraciones anteriores
4. Si hay diferencias, **merge**: tomar la version importada como base y agregar/preservar lo que existia en la version old
5. Aplicar los cambios del feature actual sobre la version mergeada
6. Esto previene la perdida de cambios de coleccion entre sesiones de trabajo

---

## Configuracion Local

Agregar a hosts:
```
127.0.0.1    mongodbbpc
127.0.0.1    discovery-service
127.0.0.1    authentication-service
127.0.0.1    gateway-service
```

Requisitos: Java 17, MongoDB, Maven 3.9.2

---

## Reglas de Produccion

### Prohibido sin autorizacion explicita:
- Modificar logica de negocio existente
- Cambiar estructuras de base de datos
- Alterar flujos de autenticacion/seguridad
- Cambiar configuraciones (.env)
- Modificar endpoints existentes
- Refactorizar codigo que "funciona bien"

### Si el cambio es necesario:
1. Debe ser **minimo y controlado**
2. Debe ser **limpio y optimo**
3. **No debe romper flujos actuales**
4. Si falla la nueva logica, **no debe detener la ejecucion principal**
5. Debe dejar **evidencia en logs** si hay error

### Ante ambiguedad:
- Listar preguntas concretas
- Proponer opciones (A/B) con pros y contras
- Esperar respuesta antes de implementar
