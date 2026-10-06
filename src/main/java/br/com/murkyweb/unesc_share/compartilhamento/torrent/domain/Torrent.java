package br.com.murkyweb.unesc_share.compartilhamento.torrent.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "torrents",
		indexes = @Index(name = "idx_torrent_material", columnList = "material_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Torrent extends BaseEntity {

	@Column(nullable = false, length = 255)
	private String nomeArquivo;

	@Column(nullable = false, length = 500)
	private String caminhoArquivo;

	@Column(nullable = false, unique = true, length = 128)
	private String infoHash;

	@Column(nullable = false)
	private long tamanho;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant dataCadastro;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "material_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_torrent_material")
	)
	private Material material;

	public Torrent(
			String nomeArquivo,
			String caminhoArquivo,
			String infoHash,
			long tamanho,
			Material material
	) {
		this.nomeArquivo = nomeArquivo;
		this.caminhoArquivo = caminhoArquivo;
		this.infoHash = infoHash;
		this.tamanho = tamanho;
		this.material = material;
	}
}
