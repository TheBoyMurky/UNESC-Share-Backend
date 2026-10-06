package br.com.murkyweb.unesc_share.material.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;

public interface MaterialRepository
		extends JpaRepository<Material, UUID>, JpaSpecificationExecutor<Material> {

	Page<Material> findAllByStatus(StatusMaterial status, Pageable pageable);

	Page<Material> findAllByUsuarioId(UUID usuarioId, Pageable pageable);
}
