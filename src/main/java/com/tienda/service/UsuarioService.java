package com.tienda.service;

import com.tienda.entity.Usuario;
import com.tienda.exception.RecursoNoEncontradoException;
import com.tienda.repository.UsuarioRepository;

import java.util.List;

public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario obtenerPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario con id " + id + " no encontrado"));
    }

    public Usuario crear(Usuario usuario) {
        if (usuarioRepository.existsByEmail(usuario.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con el email " + usuario.getEmail());
        }
        return usuarioRepository.save(usuario);
    }

    public Usuario actualizar(Long id, Usuario datos) {
        Usuario existente = obtenerPorId(id);
        existente.setNombre(datos.getNombre());
        if (datos.getEmail() != null && !datos.getEmail().equals(existente.getEmail())
                && usuarioRepository.existsByEmail(datos.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con el email " + datos.getEmail());
        }
        existente.setEmail(datos.getEmail());
        existente.setRol(datos.getRol());
        return usuarioRepository.save(existente);
    }

    public void eliminar(Long id) {
        Usuario existente = obtenerPorId(id);
        usuarioRepository.delete(existente);
    }
}
