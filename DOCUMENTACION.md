# Guía técnica paso a paso

Documento que explica, de forma sencilla y paso a paso, **qué hace la API, cómo está
organizada el código y cómo funciona la lógica interna** de cada capa: desde que el
navegador hace una petición hasta que la base de datos guarda el dato.

---

## 1. La idea general

Es una **API REST** para administrar una tienda. Permite tres grupos de operaciones:

- **Clientes** (`/api/usuarios`): crear, listar, ver, editar y borrar personas.
- **Productos** (`/api/productos`): el catálogo de la tienda con su precio y stock.
- **Ventas/compras** (`/api/ventas`): cuando un cliente "compra" un producto, se
  descuenta el stock y se guarda el registro de la venta.

La clave está en que **no es un simple CRUD**: la venta tiene lógica real
(validar stock, descontar, calcular total) y todo ocurre de forma **atómica**.

---

## 2. Arquitectura por capas

El código está dividido en "pisos" con una responsabilidad clara cada uno. La idea
es que nadie se salte de capa: el controlador solo recibe peticiones, el servicio
solo hace lógica, el repositorio solo habla con la base de datos.

```
Petición HTTP
     │
     ▼
┌──────────────┐  Recibe la petición, valida y mapea el JSON. NADA de lógica.
│  controller  │
└──────────────┘
     │  llama
     ▼
┌──────────────┐  Aquí vive la lógica de negocio (reglas reales del sistema).
│   service    │
└──────────────┘
     │  llama
     ▼
┌──────────────┐  Habla con la base de datos (SQL/consultas).
│  repository  │
└──────────────┘
     │
     ▼
┌──────────────┐  H2 (archivo en disco, los datos persisten).
│  base datos  │
└──────────────┘
```

### El flujo de una petición, paso a paso

Tomemos `GET /api/productos`:

1. El navegador (curl/Postman) hace `GET http://localhost:8080/api/productos`.
2. Spring enruta la petición a `ProductoController.listar()`.
3. El controlador no hace nada "inteligente": solo pide al `ProductoService` la lista.
4. El servicio delega en `ProductoRepository.findAll()`.
5. Spring Data JPA se encarga de traducir ese método a SQL (`SELECT * FROM productos`)
   usando la entidad `Producto` como mapeo de la tabla.
6. El resultado viaja de vuelta por las capas y Spring lo convierte en JSON automáticamente.

---

## 3. Las entidades (los "objetos" que se guardan)

Cada entidad es una clase Java que **mapea una tabla de la base de datos**. Vive en
`entity/`. Cuando Spring Boot arranca, mira estas clases para crear/actualizar las tablas
(propiedad `spring.jpa.hibernate.ddl-auto=update`).

### Usuario (cliente)
- `id` → llave primaria, autogenerada.
- `nombre` → obligatorio.
- `email` → obligatorio, único y validado (`@Email`).
- `rol` → un `enum` con tres valores: `COMPRADOR`, `VENDEDOR`, `AMBOS`.
  - No es una tabla aparte: se guarda como texto en la misma fila.
  - Sirve para saber quién puede comprar y quién puede vender.

### Producto
- `id`, `nombre`, `precio` (`BigDecimal` para dinero), `stock` (cuántas unidades hay).
- `vendedor` → relación **@ManyToOne** con `Usuario`: el dueño del producto.
  - En la tabla se guarda como una columna `vendedor_id` que apunta a la persona.

### Venta (la compra)
- Es una **entidad de asociación**: une un `Usuario` (comprador) con un `Producto`.
- `cantidad` → cuántas unidades se compraron.
- `precioUnitario` → el precio **congelado en el momento de la venta**. No se vuelve a
  recalcular aunque luego el producto suba o baje de precio.
- `total` → calculado como `precioUnitario × cantidad`.
- `fechaVenta` → se asigna **sola** cada vez que se guarda, gracias a `@PrePersist`.

---

## 4. El DTO (VentaRequest)

Un **DTO** (Data Transfer Object) es un objeto solo para recibir/enviar datos, que no se
guarda en la base de datos. `VentaRequest` es el ejemplo clave.

Cuando un cliente hace `POST /api/ventas`, **solo puede enviar**:

```json
{ "compradorId": 3, "productoId": 1, "cantidad": 2 }
```

No puede inventar ni el `total` ni la `fecha`, ni tampoco pueden llegar campos raros:
`cantidad` no puede ser menor a 1 (`@Min`). El servidor calcula todo. Así impedimos que
el cliente manipule precios.

---

## 5. Los repositorios

Son interfaces que extienden `JpaRepository`. **Spring Data JPA les escribe los métodos
automáticamente** según el nombre del método.

- `UsuarioRepository` → `findAll`, `findById`, `existsByEmail(...)`, `save`, `delete`.
- `ProductoRepository` → además tiene `findDisponibles()`, que es una consulta personalizada
  (`@Query`) que devuelve solo productos con `stock > 0`.
- `VentaRepository` → `findByCompradorId(...)`, que usa la convención de nombres de Spring
  para hacer `WHERE comprador_id = ?` automáticamente.

No escribimos el SQL a mano: el nombre del método ya lo describe.

---

## 6. Los servicios (el corazón de la lógica)

`service/` contiene las reglas reales de la aplicación. Algunos puntos importantes:

### Inyección por constructor
En lugar de crear los repositorios de cualquier forma, cada servicio **recibe sus
dependencias por constructor**:

```java
public VentaService(VentaRepository v, ProductoRepository p, UsuarioRepository u) {
    this.ventaRepository = v;
    this.productoRepository = p;
    this.usuarioRepository = u;
}
```

Spring crea e inyecta estas dependencias solo. Facilita probar y mantener el código.

### `UsuarioService` / `ProductoService`
- Evitan duplicar emails (revisan `existsByEmail` antes de guardar).
- Al crear un producto le asignan el vendedor a partir del `vendedorId` que llega por URL.
- Regla de negocio: **si el usuario no es vendedor (rol `COMPRADOR`), no puede crear productos**.

### `VentaService.registrarVenta` (@Transactional) — lo más importante
Este es el punto clave. Está marcado con `@Transactional`, lo que significa que **todo lo
que ocurre dentro es una sola operación atómica**: si algo falla, se revierte absolutamente todo.

El paso a paso interno de una venta:

1. Buscar el **comprador** (`compradorId`). Si no existe → 404.
2. Buscar el **producto** (`productoId`). Si no existe → 404.
3. **Validar stock**: si la cantidad pedida es mayor al stock disponible →
   lanza `StockInsuficienteException` → responde **409 Conflict** con un mensaje claro:
   product, stock disponible y cantidad solicitada.
4. **Descontar stock**: `producto.setStock(stock - cantidad)` y se guarda.
5. **Calcular y guardar la venta**: se congelan el precio, se calcula el total y se guarda.

Como es una transacción: **si paso 4 (descontar stock) se guardara y luego paso 5 fallara,
el stock volvería a su valor anterior**. No se queda "a medias".

---

## 7. Los controladores

`controller/` expone los endpoints. Cada método es un verbo REST:

- `@GetMapping` → leer (GET).
- `@PostMapping` → crear (POST, responde 201).
- `@PutMapping` → actualizar (PUT).
- `@DeleteMapping` → borrar (DELETE, responde 204 sin contenido).

Ejemplo de endpoints expuestos:

| Método | Ruta | Qué hace |
|--------|------|----------|
| GET | `/api/usuarios` | Lista clientes |
| GET | `/api/productos/disponibles` | Solo productos con stock |
| POST | `/api/productos?vendedorId=2` | Crea producto asignándole vendedor |
| POST | `/api/ventas` | Registra venta y descuenta stock |
| GET | `/api/ventas/comprador/3` | Ventas de un comprador |

Nota importante: el endpoint `GET /api/productos/disponibles` se declara **antes** que
`GET /api/productos/{id}`, para que el literal `disponibles` no se confunda con un id.

---

## 8. Manejo de errores (@RestControllerAdvice)

`exception/GlobalExceptionHandler` captura errores de forma centralizada y les pone el
**código HTTP correcto**:

- `RecursoNoEncontradoException` → **404 Not Found** (ej: pedir un id que no existe).
- `StockInsuficienteException` → **409 Conflict** (ej: pedir 999 laptops y solo hay 6).
- Errores de validación de datos (`@Valid`) → **400 Bad Request** con el detalle del campo.

Así el cliente siempre recibe una respuesta JSON clara con el error, nunca un crash feo.

---

## 9. La base de datos (H2 en disco)

La configuración está en `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:h2:file:./data/tienda;AUTO_SERVER=TRUE
spring.jpa.hibernate.ddl-auto=update
```

- Se usa **modo DISCO** (`jdbc:h2:file:...`), no en memoria. Esto significa que la base de
  datos se guarda en archivos dentro de `data/` y **los datos sobreviven a los reinicios**.
- `ddl-auto=update` crea o ajusta las tablas según las entidades al iniciar.
- `DataInitializer` (en `config/`) inserta datos de ejemplo **solo si la base está vacía**
  (comprueba `count() > 0`), para no duplicar en cada arranque.
- Consola web disponible en `/h2-console` (usuario `sa`, sin contraseña).

---

## 10. Ejemplo completo de una compra

1. `GET /api/usuarios` → Existe "María Gómez" (id 3, rol COMPRADOR).
2. `GET /api/productos` → Existe "Laptop" (id 1, precio 2500, **stock 10**).
3. `POST /api/ventas` con `{ "compradorId": 3, "productoId": 1, "cantidad": 2 }`.
4. El sistema valida que hay stock (10 ≥ 2), **descuenta a 8**, y guarda:
   `cantidad: 2, precioUnitario: 2500, total: 5000, fecha: <ahora>`.
5. `GET /api/productos/1` → se ve la Laptop ahora con **stock 8**. ✓
6. Si pidiéramos `cantidad: 999`, respondería **409 Conflict**: stock 8 < 999.

---

## 11. Cómo correrlo

```bash
mvn clean compile   # compila
mvn spring-boot:run # arranca en http://localhost:8080
```

Consola H2: `http://localhost:8080/h2-console` (JDBC `jdbc:h2:file:./data/tienda`, user `sa`).
Para limpiar datos y volver a los de ejemplo, borra la carpeta `data/` y reinicia.