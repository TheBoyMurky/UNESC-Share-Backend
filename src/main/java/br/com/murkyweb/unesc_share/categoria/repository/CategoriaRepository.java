package br.com.murkyweb.unesc_share.categoria.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.categoria.domain.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

	Optional<Categoria> findByNomeIgnoreCase(String nome);

	boolean existsByNomeIgnoreCase(String nome);
}
