package br.com.murkyweb.unesc_share.academico.instituicao.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;

public interface InstituicaoRepository extends JpaRepository<Instituicao, UUID> {

	Optional<Instituicao> findBySiglaIgnoreCase(String sigla);

	boolean existsByNomeIgnoreCase(String nome);

	boolean existsBySiglaIgnoreCase(String sigla);
}
