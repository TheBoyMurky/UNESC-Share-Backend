package br.com.murkyweb.unesc_share.interacao.avaliacao.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.shared.persistence.BaseEntity;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "avaliacoes",
		check = @CheckConstraint(
				name = "ck_avaliacao_nota",
				constraint = "nota >= 0.00 and nota <= 5.00"
		),
		uniqueConstraints = @UniqueConstraint(
				name = "uk_avaliacao_usuario_material",
				columnNames = { "usuario_id", "material_id" }
		),
		indexes = @Index(name = "idx_avaliacao_material", columnList = "material_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Avaliacao extends BaseEntity {

	@DecimalMin("0.00")
	@DecimalMax("5.00")
	@Digits(integer = 1, fraction = 2)
	@Column(nullable = false, precision = 3, scale = 2)
	private BigDecimal nota;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant dataCadastro;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "usuario_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_avaliacao_usuario")
	)
	private Usuario usuario;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "material_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_avaliacao_material")
	)
	private Material material;

	public Avaliacao(BigDecimal nota, Usuario usuario, Material material) {
		this.nota = nota;
		this.usuario = usuario;
		this.material = material;
	}

	public void atualizarNota(BigDecimal nota) {
		this.nota = nota;
	}
}
