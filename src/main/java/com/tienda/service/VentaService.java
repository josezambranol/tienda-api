package com.tienda.service;

import com.tienda.dto.VentaRequest;
import com.tienda.entity.Producto;
import com.tienda.entity.Usuario;
import com.tienda.entity.Venta;
import com.tienda.exception.RecursoNoEncontradoException;
import com.tienda.exception.StockInsuficienteException;
import com.tienda.repository.ProductoRepository;
import com.tienda.repository.UsuarioRepository;
import com.tienda.repository.VentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public VentaService(VentaRepository ventaRepository,
                        ProductoRepository productoRepository,
                        UsuarioRepository usuarioRepository) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<Venta> listarTodas() {
        return ventaRepository.findAll();
    }

    public List<Venta> listarPorComprador(Long compradorId) {
        if (!usuarioRepository.existsById(compradorId)) {
            throw new RecursoNoEncontradoException("Comprador con id " + compradorId + " no encontrado");
        }
        return ventaRepository.findByCompradorId(compradorId);
    }

    @Transactional
    public Venta registrarVenta(VentaRequest request) {
        Usuario comprador = usuarioRepository.findById(request.getCompradorId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Comprador con id " + request.getCompradorId() + " no encontrado"));

        Producto producto = productoRepository.findById(request.getProductoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Producto con id " + request.getProductoId() + " no encontrado"));

        int stockDisponible = producto.getStock() != null ? producto.getStock() : 0;
        if (stockDisponible < request.getCantidad()) {
            throw new StockInsuficienteException(
                    "Stock insuficiente para el producto '" + producto.getNombre()
                            + "'. Disponible: " + stockDisponible
                            + ", solicitado: " + request.getCantidad());
        }

        producto.setStock(stockDisponible - request.getCantidad());
        productoRepository.save(producto);

        Venta venta = new Venta();
        venta.setComprador(comprador);
        venta.setProducto(producto);
        venta.setCantidad(request.getCantidad());
        venta.setPrecioUnitario(producto.getPrecio());
        venta.setTotal(producto.getPrecio().multiply(BigDecimal.valueOf(request.getCantidad())));

        return ventaRepository.save(venta);
    }
}