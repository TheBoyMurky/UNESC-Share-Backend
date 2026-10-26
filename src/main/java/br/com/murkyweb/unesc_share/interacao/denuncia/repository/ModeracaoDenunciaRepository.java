package br.com.murkyweb.unesc_share.interacao.denuncia.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.ModeracaoDenuncia;

/** Sem operacoes de edicao/exclusao do historico. */
public interface ModeracaoDenunciaRepository extends Repository<ModeracaoDenuncia, UUID> {
	ModeracaoDenuncia saveAndFlush(ModeracaoDenuncia moderacao);
	Page<ModeracaoDenuncia> findAllByDenunciaId(UUID denunciaId, Pageable pageable);
}
