package br.com.murkyweb.unesc_share.interacao.comentario.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.service.ComentarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/materiais/{materialId}/comentarios")
@Tag(name = "Comentarios", description = "Comentarios de materiais publicados e gerenciamento do comentario proprio.")
@SecurityRequirement(name = "bearerAuth")
public class ComentarioController {
	private final ComentarioService service;

	@GetMapping
	public PagedModel<ComentarioResponse> listar(@PathVariable UUID materialId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(service.listar(materialId, PageRequest.of(page, size, Sort.by("dataCadastro", "id"))));
	}

	@PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	@ResponseStatus(HttpStatus.CREATED)
	public ComentarioResponse cadastrar(@PathVariable UUID materialId, @Valid @RequestBody ComentarioRequest request) {
		return service.cadastrar(materialId, request);
	}

	@PutMapping(value = "/{comentarioId}", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public ComentarioResponse atualizar(@PathVariable UUID materialId, @PathVariable UUID comentarioId,
			@Valid @RequestBody ComentarioRequest request) {
		return service.atualizar(materialId, comentarioId, request);
	}

	@DeleteMapping("/{comentarioId}")
	@ApiResponse(responseCode = "204", description = "Comentario removido logicamente.")
	public ResponseEntity<Void> remover(@PathVariable UUID materialId, @PathVariable UUID comentarioId) {
		service.remover(materialId, comentarioId);
		return ResponseEntity.noContent().build();
	}
}
