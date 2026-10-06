package br.com.murkyweb.unesc_share.compartilhamento.magnet.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.domain.Magnet;

public interface MagnetRepository extends JpaRepository<Magnet, UUID> {

	List<Magnet> findAllByMaterialIdOrderByDataCadastroAsc(UUID materialId);

	Optional<Magnet> findByInfoHashIgnoreCase(String infoHash);

	boolean existsByInfoHashIgnoreCase(String infoHash);
}
