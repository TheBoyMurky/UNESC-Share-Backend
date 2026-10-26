package br.com.murkyweb.unesc_share.interacao.avaliacao.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoRequest;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoResponse;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoResumoResponse;
import br.com.murkyweb.unesc_share.interacao.avaliacao.service.AvaliacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@RequestMapping("/materiais/{materialId}/avaliacoes")
@Tag(name = "Avaliacoes", description = "Resumo e gerenciamento da avaliacao propria do usuario autenticado.")
@SecurityRequirement(name = "bearerAuth")
public class AvaliacaoController {
	private final AvaliacaoService service;

	@PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public ResponseEntity<AvaliacaoResponse> cadastrar(@PathVariable UUID materialId, @Valid @RequestBody AvaliacaoRequest request) {
		var response = service.cadastrar(materialId, request);
		var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/minha").build().toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/minha")
	public AvaliacaoResponse minha(@PathVariable UUID materialId) { return service.minha(materialId); }

	@GetMapping("/resumo")
	public AvaliacaoResumoResponse resumo(@PathVariable UUID materialId) { return service.resumo(materialId); }

	@PutMapping(value = "/minha", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public AvaliacaoResponse atualizar(@PathVariable UUID materialId, @Valid @RequestBody AvaliacaoRequest request) {
		return service.atualizar(materialId, request);
	}

	@DeleteMapping("/minha")
	@ApiResponse(responseCode = "204", description = "Avaliacao removida.")
	public ResponseEntity<Void> remover(@PathVariable UUID materialId) {
		service.remover(materialId);
		return ResponseEntity.noContent().build();
	}
}
