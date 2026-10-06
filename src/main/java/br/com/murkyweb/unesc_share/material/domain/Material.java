package br.com.murkyweb.unesc_share.material.domain;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.categoria.domain.Categoria;
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
		name = "materiais",
		indexes = {
				@Index(name = "idx_material_status", columnList = "status"),
				@Index(name = "idx_material_usuario", columnList = "usuario_id"),
				@Index(name = "idx_material_disciplina", columnList = "disciplina_id"),
				@Index(name = "idx_material_categoria", columnList = "categoria_id")
		}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material extends BaseEntity {

	@Column(nullable = false, length = 200)
	private String titulo;

	@Column(nullable = false, length = 4000)
	private String descricao;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant dataCadastro;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatusMaterial status = StatusMaterial.PENDENTE;

	@ColumnDefault("0")
	@Column(nullable = false)
	private long visualizacoes;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "usuario_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_material_usuario")
	)
	private Usuario usuario;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "disciplina_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_material_disciplina")
	)
	private Disciplina disciplina;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "categoria_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_material_categoria")
	)
	private Categoria categoria;

	public Material(
			String titulo,
			String descricao,
			Usuario usuario,
			Disciplina disciplina,
			Categoria categoria
	) {
		this.titulo = titulo;
		this.descricao = descricao;
		this.usuario = usuario;
		this.disciplina = disciplina;
		this.categoria = categoria;
	}

	public void atualizar(
			String titulo,
			String descricao,
			Disciplina disciplina,
			Categoria categoria
	) {
		this.titulo = titulo;
		this.descricao = descricao;
		this.disciplina = disciplina;
		this.categoria = categoria;
	}

	public void alterarStatus(StatusMaterial status) {
		this.status = status;
	}

	public void incrementarVisualizacoes() {
		this.visualizacoes++;
	}
}
