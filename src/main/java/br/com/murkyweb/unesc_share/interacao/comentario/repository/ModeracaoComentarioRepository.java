package br.com.murkyweb.unesc_share.interacao.comentario.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.ModeracaoComentario;

/** Expoe somente insercao e consulta; nenhum fluxo de alteracao/exclusao do historico. */
public interface ModeracaoComentarioRepository extends Repository<ModeracaoComentario, UUID> {
	ModeracaoComentario saveAndFlush(ModeracaoComentario moderacao);
	Page<ModeracaoComentario> findAllByComentarioIdAndComentarioMaterialId(UUID comentarioId, UUID materialId, Pageable pageable);
}
