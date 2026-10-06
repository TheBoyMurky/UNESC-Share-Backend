package br.com.murkyweb.unesc_share.compartilhamento.magnet.domain;

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
		name = "magnets",
		indexes = @Index(name = "idx_magnet_material", columnList = "material_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Magnet extends BaseEntity {

	@Column(nullable = false, unique = true, length = 4000)
	private String magnetUri;

	@Column(nullable = false, unique = true, length = 128)
	private String infoHash;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant dataCadastro;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "material_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_magnet_material")
	)
	private Material material;

	public Magnet(String magnetUri, String infoHash, Material material) {
		this.magnetUri = magnetUri;
		this.infoHash = infoHash;
		this.material = material;
	}
}
