package br.com.murkyweb.unesc_share.interacao.denuncia.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.shared.persistence.BaseEntity;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
		name = "denuncias",
		indexes = {
				@Index(name = "idx_denuncia_material", columnList = "material_id"),
				@Index(name = "idx_denuncia_usuario", columnList = "usuario_id"),
				@Index(name = "idx_denuncia_status", columnList = "status")
		}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Denuncia extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MotivoDenuncia motivo;

	@Column(length = 2000)
	private String descricao;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatusDenuncia status = StatusDenuncia.PENDENTE;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant dataCadastro;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "usuario_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_denuncia_usuario")
	)
	private Usuario usuario;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "material_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_denuncia_material")
	)
	private Material material;

	public Denuncia(
			MotivoDenuncia motivo,
			String descricao,
			Usuario usuario,
			Material material
	) {
		this.motivo = motivo;
		this.descricao = descricao;
		this.usuario = usuario;
		this.material = material;
	}

	public void alterarStatus(StatusDenuncia status) {
		this.status = status;
	}
}
