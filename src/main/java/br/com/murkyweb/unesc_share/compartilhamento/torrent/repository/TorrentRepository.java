package br.com.murkyweb.unesc_share.compartilhamento.torrent.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.compartilhamento.torrent.domain.Torrent;

public interface TorrentRepository extends JpaRepository<Torrent, UUID> {

	List<Torrent> findAllByMaterialIdOrderByDataCadastroAsc(UUID materialId);

	Optional<Torrent> findByInfoHashIgnoreCase(String infoHash);

	boolean existsByInfoHashIgnoreCase(String infoHash);

	boolean existsByMaterialId(UUID materialId);

	long countByMaterialId(UUID materialId);

	Optional<Torrent> findByIdAndMaterialId(UUID id, UUID materialId);
}
