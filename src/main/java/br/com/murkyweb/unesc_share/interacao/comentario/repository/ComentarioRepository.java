package br.com.murkyweb.unesc_share.interacao.comentario.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;

public interface ComentarioRepository extends JpaRepository<Comentario, UUID> {

	Page<Comentario> findAllByMaterialIdAndStatus(
			UUID materialId,
			StatusComentario status,
			Pageable pageable
	);
}
