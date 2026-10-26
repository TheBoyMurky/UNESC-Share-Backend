package br.com.murkyweb.unesc_share.interacao.denuncia.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaRequest;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaResponse;
import br.com.murkyweb.unesc_share.interacao.denuncia.service.DenunciaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@Tag(name = "Denuncias", description = "Cadastro e consulta das denuncias do proprio usuario.")
@SecurityRequirement(name = "bearerAuth")
public class DenunciaController {
	private final DenunciaService service;

	@PostMapping(value = "/materiais/{materialId}/denuncias", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public ResponseEntity<DenunciaResponse> cadastrar(@PathVariable UUID materialId, @Valid @RequestBody DenunciaRequest request) {
		var response = service.cadastrar(materialId, request);
		var location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/denuncias/{id}")
				.buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/denuncias/minhas")
	public PagedModel<DenunciaResponse> minhas(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(service.minhas(PageRequest.of(page, size, Sort.by("dataCadastro", "id"))));
	}

	@GetMapping("/denuncias/{denunciaId}")
	public DenunciaResponse buscar(@PathVariable UUID denunciaId) { return service.buscar(denunciaId); }
}
