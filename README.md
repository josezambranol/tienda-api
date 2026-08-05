# Tienda API

API REST de gestión de tienda construida con **Java 17, Spring Boot 3.3.x, Maven y H2 (en disco)**.
Permite administrar **clientes (usuarios)**, **productos** y registrar **compras/ventas** con descuento
atómico de stock.

## Requisitos

- JDK 17 o superior (probado con Java 21)
- Maven 3.9+

## Cómo correr el proyecto

```bash
mvn clean compile      # compila
mvn spring-boot:run    # arranca en http://localhost:8080
```

La consola web de H2 queda disponible en `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:file:./data/tienda`, usuario `sa`, sin contraseña).

La base de datos se guarda en la carpeta `data/` (modo DISCO), por lo que los
datos **persisten entre reinicios**. Los datos de ejemplo solo se insertan la
primera vez (si la base está vacía).

## Endpoints

### /api/usuarios

| Método | Ruta            | Descripción                 | Códigos |
|--------|-----------------|-----------------------------|---------|
| GET    | /api/usuarios   | Lista todos los usuarios    | 200     |
| GET    | /api/usuarios/{id} | Obtiene un usuario       | 200, 404 |
| POST   | /api/usuarios   | Crea un usuario             | 201, 400 |
| PUT    | /api/usuarios/{id} | Actualiza un usuario     | 200, 400, 404 |
| DELETE | /api/usuarios/{id} | Elimina un usuario       | 204, 404 |

Body POST/PUT:
```json
{
  "nombre": "Juan Pérez",
  "email": "juan@tienda.com",
  "rol": "COMPRADOR"
}
```
`rol` puede ser: `COMPRADOR`, `VENDEDOR`, `AMBOS`.

### /api/productos

| Método | Ruta                  | Descripción                        | Códigos |
|--------|-----------------------|------------------------------------|---------|
| GET    | /api/productos        | Lista todos los productos          | 200     |
| GET    | /api/productos/disponibles | Lista productos con stock > 0  | 200     |
| GET    | /api/productos/{id}   | Obtiene un producto                | 200, 404 |
| POST   | /api/productos?vendedorId={id} | Crea un producto de un vendedor | 201, 400, 404 |
| PUT    | /api/productos/{id}   | Actualiza un producto              | 200, 400, 404 |
| DELETE | /api/productos/{id}   | Elimina un producto                | 204, 404 |

Body POST/PUT (el vendedor se indica por query param, no en el JSON):
```json
{
  "nombre": "Laptop",
  "precio": 2500.00,
  "stock": 10
}
```
Ejemplo: `POST /api/productos?vendedorId=2`

### /api/ventas

| Método | Ruta                     | Descripción                          | Códigos |
|--------|--------------------------|--------------------------------------|---------|
| GET    | /api/ventas              | Lista todas las ventas               | 200     |
| GET    | /api/ventas/comprador/{id} | Lista ventas de un comprador       | 200, 404 |
| POST   | /api/ventas              | Registra una venta y descuenta stock | 201, 400, 404, 409 |

Body POST (el total y la fecha **no** se envían; se calculan en el servidor):
```json
{
  "compradorId": 3,
  "productoId": 1,
  "cantidad": 2
}
```

Comportamiento de la venta:
- Es una operación **@Transactional**: valida stock, descuenta el producto y crea el
  registro de venta como una sola operación atómica.
- `precioUnitario` queda congelado al precio del producto en el momento de la venta.
- `total` se calcula como `precioUnitario × cantidad`.
- `fechaVenta` se asigna automáticamente con `@PrePersist`.
- Si no hay stock suficiente responde **409 Conflict**:
  ```json
  {
    "mensaje": "Stock insuficiente para el producto 'Laptop'. Disponible: 8, solicitado: 999",
    "status": 409
  }
  ```

## Estructura del proyecto

```
src/main/java/com/tienda
├── TiendaApplication.java
├── config/       # DataInitializer (datos de ejemplo si la BD está vacía)
├── controller/   # Endpoints REST (usuarios, productos, ventas)
├── dto/          # VentaRequest (entrada de ventas)
├── entity/       # Usuario, Producto, Venta, Rol (JPA)
├── exception/    # Excepciones + GlobalExceptionHandler (@RestControllerAdvice)
├── repository/   # Interfaces JpaRepository
└── service/      # Lógica de negocio (inyección por constructor)
```

## Manejo de errores

| Error                                | Código | Ejemplo de respuesta |
|--------------------------------------|--------|----------------------|
| Recurso no encontrado                | 404    | `{"mensaje": "Producto con id 99 no encontrado", "status": 404}` |
| Stock insuficiente                   | 409    | `{"mensaje": "Stock insuficiente...", "status": 409}` |
| Validación de body / email duplicado | 400    | `{"errores": {"email": "El email debe ser válido"}, "status": 400}` |
