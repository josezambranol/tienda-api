package com.tienda.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class IndexController {

    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of(
                "mensaje", "Tienda API en funcionamiento",
                "endpoints", Map.of(
                        "usuarios", "/api/usuarios",
                        "productos", "/api/productos",
                        "ventas", "/api/ventas"
                )
        );
    }
}