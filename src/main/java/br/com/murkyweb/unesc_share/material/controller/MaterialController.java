package br.com.murkyweb.unesc_share.material.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.dto.MaterialCadastroRequest;
import br.com.murkyweb.unesc_share.material.dto.MaterialCadastroTorrentRequest;
import br.com.murkyweb.unesc_share.material.dto.MaterialDetalheResponse;
import br.com.murkyweb.unesc_share.material.dto.MaterialEdicaoRequest;
import br.com.murkyweb.unesc_share.material.dto.MaterialFiltro;
import br.com.murkyweb.unesc_share.material.dto.MaterialResumoResponse;
import br.com.murkyweb.unesc_share.material.service.MaterialQueryService;
import br.com.murkyweb.unesc_share.material.service.MaterialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/materiais")
@Tag(name = "Materiais", description = "Catalogacao de metadados academicos, sem hospedagem dos arquivos compartilhados.")
@SecurityRequirement(name = "bearerAuth")
public class MaterialController {

	private final MaterialService service;
	private final MaterialQueryService consultas;

	public MaterialController(MaterialService service, MaterialQueryService consultas) {
		this.service = service;
		this.consultas = consultas;
	}

	@GetMapping
	public PagedModel<MaterialResumoResponse> listar(@Valid @ModelAttribute MaterialFiltro filtro,
			@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(consultas.listarPublicados(filtro, pagina(page, size)));
	}

	@GetMapping("/meus")
	public PagedModel<MaterialResumoResponse> meus(@Valid @ModelAttribute MaterialFiltro filtro,
			@RequestParam(required = false) StatusMaterial status,
			@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(consultas.listarMeus(status, filtro, pagina(page, size)));
	}

	@GetMapping("/moderacao")
	public PagedModel<MaterialResumoResponse> moderacao(@Valid @ModelAttribute MaterialFiltro filtro,
			@RequestParam(defaultValue = "PENDENTE") StatusMaterial status,
			@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(consultas.listarModeracao(status, filtro, pagina(page, size)));
	}

	@GetMapping("/{id}")
	public MaterialDetalheResponse consultar(@PathVariable UUID id) { return consultas.consultar(id); }

	@PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public ResponseEntity<MaterialDetalheResponse> cadastrar(@Valid @RequestBody MaterialCadastroRequest request) {
		var response = service.cadastrar(request);
		var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<MaterialDetalheResponse> cadastrarComTorrent(
			@Valid @RequestPart("material") MaterialCadastroTorrentRequest material,
			@RequestPart("arquivo") MultipartFile arquivo, HttpServletRequest request) throws IOException, ServletException {
		// RequestPart seleciona uma parte por nome; rejeitar duplicatas evita ambiguidade e arquivos extras.
		var parts = request.getParts();
		if (parts.size() != 2 || parts.stream().filter(part -> part.getName().equals("material")).count() != 1
				|| parts.stream().filter(part -> part.getName().equals("arquivo")).count() != 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie somente as partes material e arquivo, uma vez cada.");
		}
		var response = service.cadastrarComTorrent(material, arquivo);
		var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@PutMapping("/{id}")
	public MaterialDetalheResponse atualizar(@PathVariable UUID id, @Valid @RequestBody MaterialEdicaoRequest request) {
		return service.atualizar(id, request);
	}

	@DeleteMapping("/{id}")
	@ApiResponse(responseCode = "204", description = "Material removido logicamente.")
	public ResponseEntity<Void> remover(@PathVariable UUID id) {
		service.remover(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/publicacao")
	public MaterialDetalheResponse publicar(@PathVariable UUID id) { return service.publicar(id); }

	@PostMapping("/{id}/bloqueio")
	public MaterialDetalheResponse bloquear(@PathVariable UUID id) { return service.bloquear(id); }

	private PageRequest pagina(int page, int size) { return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dataCadastro", "id")); }
}
