package br.com.murkyweb.unesc_share.interacao.comentario.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioModeracaoRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioModeracaoResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.service.ComentarioModeracaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@RequestMapping("/materiais/{materialId}/comentarios")
@Tag(name = "Moderacao de comentarios", description = "Operacoes exclusivas de MODERADOR e ADMINISTRADOR.")
@SecurityRequirement(name = "bearerAuth")
public class ComentarioModeracaoController {
	private final ComentarioModeracaoService service;

	@GetMapping("/moderacao")
	public PagedModel<ComentarioResponse> listar(@PathVariable UUID materialId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(service.listar(materialId, pagina(page, size)));
	}

	@GetMapping("/{comentarioId}/historico-moderacao")
	public PagedModel<ComentarioModeracaoResponse> historico(@PathVariable UUID materialId, @PathVariable UUID comentarioId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(service.historico(materialId, comentarioId, pagina(page, size)));
	}

	@PostMapping(value = "/{comentarioId}/ocultacao", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public ComentarioResponse ocultar(@PathVariable UUID materialId, @PathVariable UUID comentarioId,
			@Valid @RequestBody ComentarioModeracaoRequest request) {
		return service.ocultar(materialId, comentarioId, request);
	}

	@PostMapping(value = "/{comentarioId}/reexibicao", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public ComentarioResponse reexibir(@PathVariable UUID materialId, @PathVariable UUID comentarioId,
			@Valid @RequestBody ComentarioModeracaoRequest request) {
		return service.reexibir(materialId, comentarioId, request);
	}

	private PageRequest pagina(int page, int size) {
		return PageRequest.of(page, size, Sort.by("dataCadastro", "id"));
	}
}
