package br.com.murkyweb.unesc_share.interacao.denuncia.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

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

/** Append-only pela aplicacao/ORM; nao protege contra SQL administrativo. */
@Getter
@Entity
@Immutable
@Table(name = "moderacoes_denuncias", indexes = {
		@Index(name = "idx_moderacao_denuncia_data", columnList = "denuncia_id,data_cadastro,id"),
		@Index(name = "idx_moderacao_denuncia_responsavel", columnList = "responsavel_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModeracaoDenuncia extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "denuncia_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_moderacao_denuncia"))
	private Denuncia denuncia;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "responsavel_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_moderacao_denuncia_responsavel"))
	private Usuario responsavel;

	@Column(nullable = false, updatable = false, length = 1000)
	private String justificativa;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private StatusDenuncia estadoAnterior;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private StatusDenuncia estadoNovo;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant dataCadastro;

	public ModeracaoDenuncia(Denuncia denuncia, Usuario responsavel, String justificativa,
			StatusDenuncia estadoAnterior, StatusDenuncia estadoNovo) {
		this.denuncia = denuncia;
		this.responsavel = responsavel;
		this.justificativa = justificativa;
		this.estadoAnterior = estadoAnterior;
		this.estadoNovo = estadoNovo;
	}
}
