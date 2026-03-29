# Siena ORM — Fork de Cuentica

Fork de [Siena ORM](https://github.com/mandubian/siena) adaptado para Cuentica.
Centrado exclusivamente en persistencia JDBC/MySQL.

## Build

```bash
cd source/
mvn clean package              # Compilar + tests (requiere MySQL configurado)
mvn clean package -DskipTests  # Compilar sin tests
mvn clean install              # Instalar en repo Maven local
mvn test -P H2                 # Ejecutar tests sin MySQL (usa H2 en memoria)
mvn test -P MYSQL              # Ejecutar tests completos contra MySQL
```

Requiere Java 17+ y Maven 3.9+.

El perfil `H2` ejecuta un subconjunto de tests (modelo, JSON, ClassInfo) sin necesidad
de tener MySQL configurado. Es el perfil que usa el CI del proyecto `app`.

## Integración con la app

Se integra como módulo Play en `app/application/lib-modules/siena-2.0.7/`.
El punto de entrada es `SienaPlugin.java`, que inicializa el `JdbcPersistenceManager`
al arrancar la aplicación.

Configuración en `application.conf`:

```properties
siena.implementation=siena.jdbc.JdbcPersistenceManager
siena.driver=com.mysql.cj.jdbc.Driver
siena.ddl=none
```

### Proceso de actualización del jar

Cuando se modifique siena, hay que actualizar el módulo en app/:

```bash
# 1. Compilar siena
cd siena/source && mvn clean package -DskipTests

# 2. Copiar el jar al módulo play-siena
cp target/siena-1.1.0-cuentica.jar \
   ../../app/application/lib-modules/siena-2.0.7/lib/

# 3. Recompilar play-siena.jar si se ha tocado SienaPlugin.java
cd ../../app/application/lib-modules/siena-2.0.7/
ant  # o el mecanismo de build del módulo

# 4. Verificar arranque local
cd ../../ && play run
```

---

## Guía de uso

### Definir un modelo

Todos los modelos extienden `siena.Model` y usan anotaciones para mapear a la base de datos:

```java
import siena.*;

@Table("clientes")
public class Cliente extends Model {

    @Id(Generator.AUTO_INCREMENT)
    public Long id;

    @Column("nombre")
    @Max(200)
    @NotNull
    public String nombre;

    @Column("email")
    @Max(255)
    @Unique
    public String email;

    @Column("notas")
    @Text
    public String notas;

    @Column("fecha_alta")
    @DateTime
    public Date fechaAlta;

    @Column("fecha_nacimiento")
    @SimpleDate
    public Date fechaNacimiento;
}
```

### Anotaciones disponibles

#### Sobre la clase

| Anotación | Objetivo | Uso |
|---|---|---|
| `@Table("nombre_tabla")` | Clase | Nombre de la tabla en BD. Sin ella, usa el nombre de la clase. |
| `@Entity` | Clase | Marca la clase como entidad Siena. En Cuentica no se usa: el módulo play-siena hace la mejora de bytecode automáticamente. |

#### Sobre los campos

| Anotación | Uso |
|---|---|
| `@Id(Generator.AUTO_INCREMENT)` | Clave primaria. Estrategias: `AUTO_INCREMENT`, `UUID`, `SEQUENCE`, `NONE` |
| `@Column("nombre")` | Nombre de la columna en BD |
| `@Max(255)` | Longitud máxima (VARCHAR) |
| `@NotNull` | Restricción NOT NULL |
| `@Unique` | Restricción UNIQUE |
| `@Text` | Campo de texto largo (TEXT en vez de VARCHAR) |
| `@DateTime` | Campo datetime/timestamp |
| `@SimpleDate` | Campo date (sin hora) |
| `@Time` | Campo time (solo hora, sin fecha). Para campos `Date` que deben mapearse a TIME en BD. |
| `@Index("nombre_indice")` | Crear un índice |
| `@Ignore` | Ignorar el campo en persistencia |
| `@Filter("campo")` | En campos `Query<T>`: aplica automáticamente un filtro por el campo indicado. Ver sección [Consultas filtradas automáticas](#consultas-filtradas-automáticas). |
| `@DecimalPrecision(...)` | Control de almacenamiento para campos `BigDecimal`. Ver sección [BigDecimal y precisión decimal](#bigdecimal-y-precisión-decimal). |
| `@Polymorphic` | El campo puede almacenar cualquier tipo serializable. Siena lo serializa como bytes usando Java serialization. Útil para datos heterogéneos. |

#### Sobre campos embebidos

| Anotación | Objetivo | Uso |
|---|---|---|
| `@Embedded` | Campo | Serializa el objeto como JSON en la columna. Acepta `mode`: `SERIALIZE_JSON` (por defecto), `SERIALIZE_JAVA`, `NATIVE`. |
| `@EmbeddedMap` | Clase | Marca una clase como apta para ser usada en `Map` embebidos. |
| `@EmbeddedList` | Clase | Marca una clase como apta para ser usada en listas embebidas. |
| `@EmbedIgnore` | Campo | Excluye el campo de la serialización embebida. |
| `@At(int)` | Campo | Posición del campo dentro de una lista embebida. |
| `@Key(String)` | Campo | Clave del campo dentro de un mapa embebido. |
| `@Format(String)` | Campo | Patrón de formato para el campo dentro de un objeto embebido (p. ej., formato de fecha). |

### Referencia completa de anotaciones

Tabla consolidada de todas las anotaciones disponibles, sus paquetes y dónde se aplican:

| Anotación | Paquete | Se aplica a | Descripción breve |
|---|---|---|---|
| `@Table` | `siena` | Clase | Nombre de la tabla |
| `@Entity` | `siena` | Clase | Marca la clase como entidad |
| `@Id` | `siena` | Campo | Clave primaria |
| `@Column` | `siena` | Campo | Nombre de columna |
| `@Max` | `siena` | Campo | Longitud máxima VARCHAR |
| `@NotNull` | `siena` | Campo | Restricción NOT NULL |
| `@Unique` | `siena` | Campo | Restricción UNIQUE |
| `@Text` | `siena` | Campo | Columna TEXT |
| `@DateTime` | `siena` | Campo | Columna DATETIME/TIMESTAMP |
| `@SimpleDate` | `siena` | Campo | Columna DATE |
| `@Time` | `siena` | Campo | Columna TIME (solo hora) |
| `@Index` | `siena` | Campo | Crear índice |
| `@Ignore` | `siena` | Campo | Excluir de persistencia |
| `@Filter` | `siena` | Campo `Query<T>` | Filtro automático en queries |
| `@DecimalPrecision` | `siena.core` | Campo `BigDecimal` | Control de almacenamiento decimal |
| `@Polymorphic` | `siena.core` | Campo | Almacena cualquier tipo serializable |
| `@Embedded` | `siena.embed` | Campo | Serializar objeto como JSON |
| `@EmbeddedMap` | `siena.embed` | Clase | Clase usable en Map embebido |
| `@EmbeddedList` | `siena.embed` | Clase | Clase usable en List embebida |
| `@EmbedIgnore` | `siena.embed` | Campo | Excluir de serialización embebida |
| `@At` | `siena.embed` | Campo | Posición en lista embebida |
| `@Key` | `siena.embed` | Campo | Clave en mapa embebido |
| `@Format` | `siena.embed` | Campo | Patrón de formato en embebidos |
| `@Aggregated` | `siena.core` | Campo `Many<T>` | Relación de agregación (one-to-many) |
| `@Owned` | `siena.core` | Campo | Relación de propiedad |
| `@PreInsert` | `siena.core.lifecycle` | Método | Hook antes de insertar |
| `@PostInsert` | `siena.core.lifecycle` | Método | Hook después de insertar |
| `@PreUpdate` | `siena.core.lifecycle` | Método | Hook antes de actualizar |
| `@PostUpdate` | `siena.core.lifecycle` | Método | Hook después de actualizar |
| `@PreDelete` | `siena.core.lifecycle` | Método | Hook antes de eliminar |
| `@PostDelete` | `siena.core.lifecycle` | Método | Hook después de eliminar |
| `@PreSave` | `siena.core.lifecycle` | Método | Hook antes de guardar (insert o update) |
| `@PostSave` | `siena.core.lifecycle` | Método | Hook después de guardar |
| `@PreFetch` | `siena.core.lifecycle` | Método | Hook antes de cargar |
| `@PostFetch` | `siena.core.lifecycle` | Método | Hook después de cargar |

### CRUD básico

```java
// Crear
Cliente cliente = new Cliente();
cliente.nombre = "Acme S.L.";
cliente.email = "info@acme.es";
cliente.fechaAlta = new Date();
cliente.insert();

// Leer por ID
Cliente encontrado = Model.getByKey(Cliente.class, 42L);

// Actualizar
encontrado.nombre = "Acme S.L. (actualizado)";
encontrado.update();

// Guardar (insert o update según exista)
cliente.save();

// Eliminar
cliente.delete();
```

### Consultas (Query API)

```java
// Obtener todos
List<Cliente> todos = Model.all(Cliente.class).fetch();

// Filtrar
List<Cliente> resultado = Model.all(Cliente.class)
    .filter("nombre", "Acme S.L.")
    .fetch();

// Operadores de comparación
Model.all(Cliente.class).filter("id >", 10).fetch();
Model.all(Cliente.class).filter("id >=", 10).fetch();
Model.all(Cliente.class).filter("id <", 100).fetch();
Model.all(Cliente.class).filter("id !=", 0).fetch();

// IN
List<Long> ids = Arrays.asList(1L, 2L, 3L);
Model.all(Cliente.class).filter("id IN", ids).fetch();

// Ordenar
Model.all(Cliente.class)
    .order("nombre")          // ASC
    .order("-fechaAlta")      // DESC (prefijo -)
    .fetch();

// Limitar resultados
Model.all(Cliente.class).fetch(10);          // primeros 10
Model.all(Cliente.class).fetch(10, offset);  // con offset

// Obtener uno solo
Cliente primero = Model.all(Cliente.class)
    .filter("email", "info@acme.es")
    .get();

// Contar
int total = Model.all(Cliente.class).count();

// Búsqueda de texto
Model.all(Cliente.class)
    .search("acme", "nombre", "email")
    .fetch();

// Combinar
List<Cliente> activos = Model.all(Cliente.class)
    .filter("activo", true)
    .order("-fechaAlta")
    .fetch(20);
```

### Paginación manual con limit y offset

`limit` y `offset` configuran la query sin ejecutarla, lo que permite construirla
en pasos o reutilizarla:

```java
// Equivalente a fetch(20, 40), pero como configuración reutilizable
Query<Factura> query = Model.all(Factura.class)
    .filter("cliente", clienteId)
    .order("-fechaEmision")
    .limit(20)
    .offset(40);

List<Factura> pagina = query.fetch();
int total = query.count();
```

### Paginación automática

`paginate` gestiona el cursor de página internamente. Es útil cuando se navega
página a página sin calcular offsets manualmente:

```java
Query<Factura> query = Model.all(Factura.class)
    .filter("empresa", empresaId)
    .order("-fechaEmision")
    .paginate(25);    // 25 resultados por página

// Primera página
List<Factura> pagina1 = query.fetch();

// Página siguiente
List<Factura> pagina2 = query.nextPage().fetch();

// Página anterior
List<Factura> pagina1Otra = query.previousPage().fetch();
```

### Paginación avanzada con queries con estado (stateful)

Por defecto, las queries son `stateless`: cada llamada a `fetch` es independiente.
Con `stateful`, la query recuerda el cursor entre llamadas, lo que permite reutilizar
el mismo objeto de query para navegar sin reconstruirlo:

```java
// La query recuerda su estado entre peticiones HTTP (p. ej., guardada en sesión)
Query<Gasto> queryGastos = Model.all(Gasto.class)
    .filter("empresa", empresaId)
    .order("-fecha")
    .stateful()
    .paginate(20);

// Primera petición: página 1
List<Gasto> pagina1 = queryGastos.fetch();

// Segunda petición (mismo objeto de query): página 2 automáticamente
List<Gasto> pagina2 = queryGastos.nextPage().fetch();

// Cuando ya no se necesita, liberar recursos
queryGastos.release();
```

`stateless()` (comportamiento por defecto) resetea el cursor en cada `fetch`.
`release()` libera el cursor y desactiva el modo stateful.
`resetData()` limpia los datos de la query pero mantiene los filtros.
`copy()` devuelve una copia independiente de la query.

### Iteración sobre grandes conjuntos de datos

`iter` devuelve un `Iterable` que carga los registros de forma perezosa (lazy),
evitando cargar toda la tabla en memoria. Es la opción adecuada para procesar
exportaciones o recalcular datos de muchos registros:

```java
// Iterar todos los gastos del año sin cargarlos todos en memoria
Iterable<Gasto> gastos = Model.all(Gasto.class)
    .filter("anio", 2024)
    .iter();

for (Gasto gasto : gastos) {
    procesarGasto(gasto);
}

// Iterar un máximo de registros
Iterable<Gasto> primeros500 = Model.all(Gasto.class).iter(500);

// Iterar por páginas internas (útil para control de memoria)
Iterable<Gasto> porPaginas = Model.all(Gasto.class).iterPerPage(100);
```

`iter()` -- todos los registros, carga lazy.
`iter(int limit)` -- máximo `limit` registros.
`iter(int limit, Object offset)` -- máximo `limit` a partir de `offset`.
`iterPerPage(int size)` -- itera internamente en páginas de `size` registros.

### Obtención solo de claves (fetchKeys)

`fetchKeys` recupera únicamente la clave primaria de cada registro, sin cargar
el resto de campos. Útil para comprobar existencia o construir conjuntos de IDs
sin el coste de hidratar objetos completos:

```java
// Obtener solo los IDs de facturas pendientes de cobro
List<Factura> soloIds = Model.all(Factura.class)
    .filter("estado", "pendiente")
    .fetchKeys();

// Los objetos devueltos solo tienen el campo @Id relleno
for (Factura f : soloIds) {
    System.out.println(f.id); // relleno
    System.out.println(f.numero); // null
}

// Con límite
List<Factura> primeros100Ids = Model.all(Factura.class).fetchKeys(100);
```

### Consultas filtradas automáticas

`@Filter("campo")` en un campo de tipo `Query<T>` crea una relación de navegación:
cuando se ejecuta la query, Siena aplica automáticamente el filtro `campo == this`.

Es la manera de declarar relaciones one-to-many sin código adicional:

```java
@Table("empresas")
public class Empresa extends Model {

    @Id(Generator.AUTO_INCREMENT)
    public Long id;

    public String nombre;

    // Cuando se ejecute esta query, Siena filtra automáticamente por empresa == this
    @Filter("empresa")
    public Query<Factura> facturas;

    @Filter("empresa")
    public Query<Gasto> gastos;
}

@Table("facturas")
public class Factura extends Model {

    @Id(Generator.AUTO_INCREMENT)
    public Long id;

    @Column("empresa_id")
    public Empresa empresa;

    public String numero;
}

// Uso: no hace falta construir el filtro manualmente
Empresa empresa = Model.getByKey(Empresa.class, 1L);
List<Factura> facturas = empresa.facturas.fetch();
List<Gasto> gastos = empresa.gastos.order("-fecha").fetch(10);
```

### Serialización de queries (dump/restore)

Las queries pueden serializarse a `String` y restaurarse. Útil para cachear
queries complejas o pasarlas entre peticiones:

```java
// Serializar la query
Query<Factura> query = Model.all(Factura.class)
    .filter("empresa", empresaId)
    .order("-fechaEmision")
    .limit(20);

String serializada = query.dump();

// Restaurar en otra petición
Query<Factura> restaurada = Model.all(Factura.class).restore(serializada);
List<Factura> facturas = restaurada.fetch();
```

### Queries de relaciones (aggregated/owned)

Para queries sobre relaciones de agregación o propiedad declaradas con `@Aggregated`
y `@Owned`:

```java
// Obtener las líneas que pertenecen a una factura concreta (relación agregada)
Factura factura = Model.getByKey(Factura.class, 1L);
List<LineaFactura> lineas = Model.all(LineaFactura.class)
    .aggregated(factura, "lineas")
    .fetch();

// Obtener registros que son propiedad de una entidad
List<Adjunto> adjuntos = Model.all(Adjunto.class)
    .owned(factura, "adjuntos")
    .fetch();
```

### Trabajar con Query como variable

```java
Query<Cliente> query = Model.all(Cliente.class);
query.filter("activo", true);

if (orden != null) {
    query.order(orden);
}

List<Cliente> resultado = query.fetch();
```

### Modelos embebidos

Para almacenar objetos complejos serializados en JSON dentro de un campo:

```java
import siena.embed.*;

public class Configuracion {
    public String tema;
    public String idioma;
}

@Table("empresas")
public class Empresa extends Model {

    @Id(Generator.AUTO_INCREMENT)
    public Long id;

    @Embedded
    public Configuracion configuracion;

    @EmbeddedMap
    public Map<String, String> metadatos;
}
```

Los objetos `@Embedded` se almacenan como JSON en la columna correspondiente.

### Listas embebidas con @EmbeddedList y @At

`@EmbeddedList` se pone en la clase que se va a incluir dentro de la lista.
`@At(int)` indica la posición del campo en el JSON resultante, lo que garantiza
un orden estable en la serialización:

```java
import siena.embed.*;

@EmbeddedList
public class LineaPresupuesto {

    @At(0)
    public String concepto;

    @At(1)
    public Double cantidad;

    @At(2)
    public Double precioUnitario;

    @EmbedIgnore
    public transient Double totalCalculado; // no se serializa
}

@Table("presupuestos")
public class Presupuesto extends Model {

    @Id(Generator.AUTO_INCREMENT)
    public Long id;

    @Embedded
    public List<LineaPresupuesto> lineas;
}
```

### Mapas embebidos con @EmbeddedMap y @Key

`@EmbeddedMap` se pone en la clase que actúa como valor del mapa.
`@Key(String)` indica el nombre de la clave bajo la que se serializa el campo:

```java
import siena.embed.*;

@EmbeddedMap
public class ConfiguracionImpuesto {

    @Key("tipo")
    public String tipoImpuesto;

    @Key("porcentaje")
    public Double porcentaje;
}

@Table("configuraciones_fiscales")
public class ConfiguracionFiscal extends Model {

    @Id(Generator.AUTO_INCREMENT)
    public Long id;

    @Embedded
    public Map<String, ConfiguracionImpuesto> impuestos;
}
```

### BigDecimal y precisión decimal

`@DecimalPrecision` controla cómo se almacena un campo `BigDecimal` en la base de datos.
Hay tres estrategias:

- `StorageType.NATIVE` -- columna `DECIMAL(size, scale)` en MySQL. Es la opción más precisa y la recomendada para importes monetarios.
- `StorageType.DOUBLE` -- almacena como `DOUBLE`. Más rápido, pero con posibles errores de redondeo en decimales largos.
- `StorageType.STRING` -- almacena como cadena. Útil cuando se necesita preservar la representación exacta.

```java
import siena.*;
import siena.core.DecimalPrecision;
import siena.core.DecimalPrecision.StorageType;
import java.math.BigDecimal;

@Table("facturas")
public class Factura extends Model {

    @Id(Generator.AUTO_INCREMENT)
    public Long id;

    // Importe con 2 decimales: columna DECIMAL(12, 2) en MySQL
    @DecimalPrecision(storageType = StorageType.NATIVE, size = 12, scale = 2)
    public BigDecimal importeBase;

    // Tipo de cambio con alta precisión: columna DECIMAL(19, 8)
    @DecimalPrecision(storageType = StorageType.NATIVE, size = 19, scale = 8)
    public BigDecimal tipoCambio;

    // Sin @DecimalPrecision: usa los valores por defecto (NATIVE, size=19, scale=2)
    public BigDecimal descuento;
}
```

Si no se indica `@DecimalPrecision`, Siena usa `StorageType.NATIVE` con `size=19` y `scale=2`.

### Relaciones

Siena soporta relaciones declarativas. Actualmente Cuentica no las usa (las relaciones
se gestionan manualmente con foreign keys y queries), pero están disponibles:

```java
import siena.core.*;

@Table("pedidos")
public class Pedido extends Model {

    @Id(Generator.AUTO_INCREMENT)
    public Long id;

    // Referencia a otro modelo (many-to-one)
    public One<Cliente> cliente = new BaseOne<>(Cliente.class);

    // Colección de líneas (one-to-many, agregada)
    @Aggregated
    public Many<LineaPedido> lineas = new BaseMany<>(LineaPedido.class);
}

// Uso
Pedido pedido = Model.getByKey(Pedido.class, 1L);
Cliente c = pedido.cliente.get();           // carga lazy
List<LineaPedido> l = pedido.lineas.asList(); // carga las líneas
```

### Lifecycle hooks

Ejecutan código automáticamente antes/después de operaciones de persistencia:

```java
import siena.core.lifecycle.*;

@Table("auditable")
public class Documento extends Model {

    @Id(Generator.AUTO_INCREMENT)
    public Long id;

    public String titulo;
    public Date creadoEn;
    public Date actualizadoEn;

    @PreInsert
    public void antesDeInsertar() {
        creadoEn = new Date();
        actualizadoEn = new Date();
    }

    @PreUpdate
    public void antesDeActualizar() {
        actualizadoEn = new Date();
    }

    @PostDelete
    public void despuesDeEliminar() {
        // Limpiar recursos asociados
    }
}
```

Hooks disponibles: `@PreInsert`, `@PostInsert`, `@PreUpdate`, `@PostUpdate`,
`@PreDelete`, `@PostDelete`, `@PreSave`, `@PostSave`, `@PreFetch`, `@PostFetch`.

### Operaciones batch

Para operaciones masivas más eficientes:

```java
import siena.core.batch.*;

// Insertar varios a la vez
List<Cliente> nuevos = crearClientes();
Batch<Cliente> batch = Model.batch(Cliente.class);
batch.insert(nuevos);

// Actualizar en lote
batch.update(listaModificada);

// Eliminar en lote
batch.delete(listaParaBorrar);
```

### Operaciones asíncronas

> **Nota:** El `JdbcPersistenceManager` que usa Cuentica **no implementa operaciones async**.
> Las interfaces existen en el código (para otros backends), pero llamar a `async()` lanzará
> `SienaException("Not Implemented")`. Se documenta como referencia.

```java
import siena.core.async.*;

// Las operaciones async devuelven SienaFuture
SienaFuture<Void> futuro = modelo.async().save();

// Esperar resultado cuando sea necesario
futuro.get();
```

### Transacciones

En Cuentica, el `PersistenceManager` se obtiene desde el plugin de Play:

```java
import java.sql.Connection;
import play.modules.siena.SienaPlugin;

PersistenceManager pm = SienaPlugin.pm();

// Con nivel de aislamiento explícito (recomendado para operaciones críticas)
pm.beginTransaction(Connection.TRANSACTION_READ_COMMITTED);
try {
    factura.update();
    apunte.insert();
    pm.commitTransaction();
} catch (Exception e) {
    pm.rollbackTransaction();
    throw e;
}

// Sin nivel explícito: usa el nivel por defecto de MySQL (REPEATABLE_READ)
pm.beginTransaction();
try {
    cliente.update();
    pedido.insert();
    pm.commitTransaction();
} catch (Exception e) {
    pm.rollbackTransaction();
    throw e;
}
```

Niveles de aislamiento disponibles en `java.sql.Connection`:

| Constante | Descripción |
|---|---|
| `TRANSACTION_READ_UNCOMMITTED` | Lee cambios no confirmados de otras transacciones |
| `TRANSACTION_READ_COMMITTED` | Solo lee cambios confirmados (recomendado para la mayoría de casos) |
| `TRANSACTION_REPEATABLE_READ` | Garantiza que una misma lectura devuelve el mismo resultado |
| `TRANSACTION_SERIALIZABLE` | Máximo aislamiento, menor concurrencia |

### Generación de DDL (para migraciones)

```java
import siena.jdbc.ddl.DdlGenerator;
import org.apache.ddlutils.*;

DdlGenerator generator = new DdlGenerator();
generator.addTable(Cliente.class);
generator.addTable(Pedido.class);

Database database = generator.getDatabase();
Platform platform = PlatformFactory.createNewPlatformInstance("mysql");

// Generar SQL de creación
String sql = platform.getCreateTablesSql(database, false, false);

// O aplicar cambios directamente
Connection conn = DriverManager.getConnection(url, user, pass);
platform.alterTables(conn, database, true);
```

---

## Estructura del proyecto

```
siena/source/src/main/java/siena/
├── Model.java, Query.java, Util.java     -- Core del ORM
├── PersistenceManager.java               -- Interfaz principal de persistencia
├── Anotaciones: @Id, @Column, @Table...  -- Mapeo objeto-relacional
├── core/
│   ├── One, Many, BaseOne, BaseMany      -- Relaciones
│   ├── async/                            -- Operaciones asíncronas
│   ├── batch/                            -- Operaciones en lote
│   ├── lifecycle/                        -- Hooks @Pre/@Post
│   ├── options/                          -- Opciones de query (stateful, paginate...)
│   ├── DecimalPrecision.java             -- Anotación para BigDecimal
│   └── Polymorphic.java                  -- Anotación para tipos polimórficos
├── embed/                                -- Modelos embebidos (JSON)
│   ├── Embedded, EmbeddedMap, EmbeddedList
│   ├── At, Key, Format                   -- Anotaciones de posición/clave/formato
│   └── EmbedIgnore                       -- Excluir campos de la serialización
├── jdbc/
│   ├── JdbcPersistenceManager.java       -- Implementación JDBC
│   ├── ConnectionManager.java            -- Gestión de conexiones
│   └── ddl/DdlGenerator.java            -- Generación de esquemas
└── logging/                              -- Logging interno
```

## Historial del fork

- **v1.0.0-cuentica** (2024): Limpieza parcial. Eliminación de GAE, soporte LocalDateTime.
- **v1.1.0-cuentica** (2026): Limpieza completa:
  - Eliminación de backends: SDB (Amazon SimpleDB), Remote, PostgreSQL, H2 FullText.
  - Fix: `SimpleDateFormat` reemplazado por `DateTimeFormatter` (thread-safe).
  - Fix: `Class.newInstance()` eliminado (deprecated en Java 17).
  - Fix: `blob.getBytes()` corregido (JDBC usa índice 1-based).
  - Fix: resource leaks en `BaseQuery.dump()`/`restore()` con try-with-resources.
  - Actualización a Java 17, JUnit 4.13, H2 2.2, MySQL Connector 8.0.33, commons-dbcp2.
  - Suite de tests H2 (331 tests sin necesidad de MySQL): `mvn test -P H2`.
  - Limpieza de `SienaPlugin.java` en el módulo play-siena de app/.
