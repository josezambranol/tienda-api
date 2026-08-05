package com.tienda.config;

import com.tienda.entity.Producto;
import com.tienda.entity.Rol;
import com.tienda.entity.Usuario;
import com.tienda.repository.ProductoRepository;
import com.tienda.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner inicializarDatos(UsuarioRepository usuarioRepository,
                                              ProductoRepository productoRepository) {
        return args -> {
            if (usuarioRepository.count() > 0 || productoRepository.count() > 0) {
                return;
            }

            Usuario ana = new Usuario(null, "Ana López", "ana@tienda.com", Rol.AMBOS);
            Usuario carlos = new Usuario(null, "Carlos Pérez", "carlos@tienda.com", Rol.VENDEDOR);
            Usuario maria = new Usuario(null, "María Gómez", "maria@tienda.com", Rol.COMPRADOR);
            usuarioRepository.save(ana);
            usuarioRepository.save(carlos);
            usuarioRepository.save(maria);

            Producto laptop = new Producto(null, "Laptop", new BigDecimal("2500.00"), 10, carlos);
            Producto mouse = new Producto(null, "Mouse", new BigDecimal("150.00"), 50, carlos);
            Producto teclado = new Producto(null, "Teclado", new BigDecimal("350.00"), 30, ana);
            Producto monitor = new Producto(null, "Monitor", new BigDecimal("1800.00"), 0, ana);
            productoRepository.save(laptop);
            productoRepository.save(mouse);
            productoRepository.save(teclado);
            productoRepository.save(monitor);
        };
    }
}