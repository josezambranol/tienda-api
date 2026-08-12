package com.tienda;

import com.tienda.repository.ProductoRepository;
import com.tienda.repository.UsuarioRepository;
import com.tienda.repository.VentaRepository;
import com.tienda.service.ProductoService;
import com.tienda.service.UsuarioService;
import com.tienda.service.VentaService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TiendaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TiendaApplication.class, args);
    }

    @Bean
    public UsuarioService usuarioService(UsuarioRepository usuarioRepository) {
        return new UsuarioService(usuarioRepository);
    }

    @Bean
    public ProductoService productoService(
            ProductoRepository productoRepository,
            UsuarioRepository usuarioRepository) {
        return new ProductoService(productoRepository, usuarioRepository);
    }

    @Bean
    public VentaService ventaService(
            VentaRepository ventaRepository,
            ProductoRepository productoRepository,
            UsuarioRepository usuarioRepository) {
        return new VentaService(ventaRepository, productoRepository, usuarioRepository);
    }
}
