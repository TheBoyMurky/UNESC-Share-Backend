package br.com.murkyweb.unesc_share.academico.disciplina.controller;

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

import br.com.murkyweb.unesc_share.academico.disciplina.dto.DisciplinaRequest;
import br.com.murkyweb.unesc_share.academico.disciplina.dto.DisciplinaResponse;
import br.com.murkyweb.unesc_share.academico.disciplina.service.DisciplinaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/disciplinas")
@Tag(name = "Disciplinas", description = "Consultas para usuarios autenticados; escrita exclusiva de ADMINISTRADOR.")
@SecurityRequirement(name = "bearerAuth")
public class DisciplinaController {

	private final DisciplinaService service;

	public DisciplinaController(DisciplinaService service) {
		this.service = service;
	}

	@GetMapping
	public PagedModel<DisciplinaResponse> listar(@RequestParam(required = false) UUID cursoId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(service.listar(cursoId, PageRequest.of(page, size, Sort.by("nome", "id"))));
	}

	@GetMapping("/{id}")
	public DisciplinaResponse consultar(@PathVariable UUID id) {
		return service.consultar(id);
	}

	@PostMapping
	public ResponseEntity<DisciplinaResponse> cadastrar(@Valid @RequestBody DisciplinaRequest request) {
		var response = service.cadastrar(request);
		var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
				.buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@PutMapping("/{id}")
	public DisciplinaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody DisciplinaRequest request) {
		return service.atualizar(id, request);
	}

	@DeleteMapping("/{id}")
	@ApiResponse(responseCode = "204", description = "Disciplina excluida.")
	public ResponseEntity<Void> excluir(@PathVariable UUID id) {
		service.excluir(id);
		return ResponseEntity.noContent().build();
	}
}
