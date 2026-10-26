package br.com.murkyweb.unesc_share.interacao.comentario.domain;

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

/** Historico append-only pela aplicacao; nao substitui auditoria completa ou protecao contra SQL externo. */
@Getter
@Entity
@Immutable
@Table(name = "moderacoes_comentarios", indexes = {
		@Index(name = "idx_moderacao_comentario_data", columnList = "comentario_id,data_cadastro,id"),
		@Index(name = "idx_moderacao_comentario_responsavel", columnList = "responsavel_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModeracaoComentario extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comentario_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_moderacao_comentario"))
	private Comentario comentario;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "responsavel_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_moderacao_comentario_responsavel"))
	private Usuario responsavel;

	@Column(nullable = false, updatable = false, length = 1000)
	private String motivo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private StatusComentario estadoAnterior;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private StatusComentario estadoNovo;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant dataCadastro;

	public ModeracaoComentario(Comentario comentario, Usuario responsavel, String motivo,
			StatusComentario estadoAnterior, StatusComentario estadoNovo) {
		this.comentario = comentario;
		this.responsavel = responsavel;
		this.motivo = motivo;
		this.estadoAnterior = estadoAnterior;
		this.estadoNovo = estadoNovo;
	}
}
