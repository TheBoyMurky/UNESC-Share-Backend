package br.com.murkyweb.unesc_share.academico.instituicao.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.murkyweb.unesc_share.academico.instituicao.dto.InstituicaoRequest;
import br.com.murkyweb.unesc_share.academico.instituicao.dto.InstituicaoResponse;
import br.com.murkyweb.unesc_share.academico.instituicao.service.InstituicaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/instituicoes")
@Tag(name = "Instituicoes", description = "Consultas para usuarios autenticados; escrita exclusiva de ADMINISTRADOR.")
@SecurityRequirement(name = "bearerAuth")
public class InstituicaoController {

	private final InstituicaoService service;

	public InstituicaoController(InstituicaoService service) {
		this.service = service;
	}

	@GetMapping
	public PagedModel<InstituicaoResponse> listar(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(service.listar(PageRequest.of(page, size, Sort.by("nome", "id"))));
	}

	@GetMapping("/{id}")
	public InstituicaoResponse consultar(@PathVariable UUID id) {
		return service.consultar(id);
	}

	@PostMapping
	public ResponseEntity<InstituicaoResponse> cadastrar(@Valid @RequestBody InstituicaoRequest request) {
		var response = service.cadastrar(request);
		var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
				.buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@PutMapping("/{id}")
	public InstituicaoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody InstituicaoRequest request) {
		return service.atualizar(id, request);
	}

	@DeleteMapping("/{id}")
	@ApiResponse(responseCode = "204", description = "Instituicao excluida.")
	public ResponseEntity<Void> excluir(@PathVariable UUID id) {
		service.excluir(id);
		return ResponseEntity.noContent().build();
	}
}
