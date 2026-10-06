package br.com.murkyweb.unesc_share.interacao.denuncia.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;

public interface DenunciaRepository extends JpaRepository<Denuncia, UUID> {

	Page<Denuncia> findAllByStatus(StatusDenuncia status, Pageable pageable);

	Page<Denuncia> findAllByMaterialId(UUID materialId, Pageable pageable);
}
