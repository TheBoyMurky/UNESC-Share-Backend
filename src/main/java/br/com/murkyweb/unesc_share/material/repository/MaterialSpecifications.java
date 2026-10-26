package br.com.murkyweb.unesc_share.material.repository;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.dto.MaterialFiltro;
import jakarta.persistence.criteria.Predicate;

public final class MaterialSpecifications {
	private MaterialSpecifications() { }

	public static Specification<Material> filtrar(StatusMaterial status, UUID usuarioId, MaterialFiltro filtro) {
		return (root, query, builder) -> {
			var predicates = new ArrayList<Predicate>();
			if (status != null) { predicates.add(builder.equal(root.get("status"), status)); }
			if (usuarioId != null) { predicates.add(builder.equal(root.get("usuario").get("id"), usuarioId)); }
			if (filtro.instituicaoId() != null) {
				predicates.add(builder.equal(root.get("disciplina").get("curso").get("instituicao").get("id"), filtro.instituicaoId()));
			}
			if (filtro.cursoId() != null) { predicates.add(builder.equal(root.get("disciplina").get("curso").get("id"), filtro.cursoId())); }
			if (filtro.disciplinaId() != null) { predicates.add(builder.equal(root.get("disciplina").get("id"), filtro.disciplinaId())); }
			if (filtro.categoriaId() != null) { predicates.add(builder.equal(root.get("categoria").get("id"), filtro.categoriaId())); }
			if (!filtro.q().isBlank()) {
				String texto = filtro.q().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
				predicates.add(builder.or(builder.like(builder.lower(root.get("titulo")), "%" + texto + "%", '\\'),
						builder.like(builder.lower(root.get("descricao")), "%" + texto + "%", '\\')));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}
}
