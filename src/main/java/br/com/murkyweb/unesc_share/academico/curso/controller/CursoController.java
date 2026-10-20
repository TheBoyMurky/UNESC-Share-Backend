package br.com.murkyweb.unesc_share.academico.curso.controller;

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

import br.com.murkyweb.unesc_share.academico.curso.dto.CursoRequest;
import br.com.murkyweb.unesc_share.academico.curso.dto.CursoResponse;
import br.com.murkyweb.unesc_share.academico.curso.service.CursoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/cursos")
public class CursoController {

	private final CursoService service;

	public CursoController(CursoService service) {
		this.service = service;
	}

	@GetMapping
	public PagedModel<CursoResponse> listar(@RequestParam(required = false) UUID instituicaoId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(service.listar(instituicaoId, PageRequest.of(page, size, Sort.by("nome", "id"))));
	}

	@GetMapping("/{id}")
	public CursoResponse consultar(@PathVariable UUID id) {
		return service.consultar(id);
	}

	@PostMapping
	public ResponseEntity<CursoResponse> cadastrar(@Valid @RequestBody CursoRequest request) {
		var response = service.cadastrar(request);
		var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
				.buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@PutMapping("/{id}")
	public CursoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CursoRequest request) {
		return service.atualizar(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> excluir(@PathVariable UUID id) {
		service.excluir(id);
		return ResponseEntity.noContent().build();
	}
}
