package br.com.murkyweb.unesc_share.interacao.denuncia.controller;

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

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaModeracaoRequest;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaModeracaoResponse;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaResponse;
import br.com.murkyweb.unesc_share.interacao.denuncia.service.DenunciaModeracaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@RequestMapping("/denuncias/moderacao")
@Tag(name = "Moderacao de denuncias", description = "Operacoes exclusivas de MODERADOR e ADMINISTRADOR.")
@SecurityRequirement(name = "bearerAuth")
public class DenunciaModeracaoController {
	private final DenunciaModeracaoService service;

	@GetMapping
	public PagedModel<DenunciaResponse> listar(@RequestParam(defaultValue = "PENDENTE") StatusDenuncia status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(service.listar(status, pagina(page, size)));
	}

	@GetMapping("/{denunciaId}")
	public DenunciaResponse buscar(@PathVariable UUID denunciaId) { return service.buscar(denunciaId); }

	@GetMapping("/{denunciaId}/historico")
	public PagedModel<DenunciaModeracaoResponse> historico(@PathVariable UUID denunciaId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(service.historico(denunciaId, pagina(page, size)));
	}

	@PostMapping(value = "/{denunciaId}/analise", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public DenunciaResponse analisar(@PathVariable UUID denunciaId, @Valid @RequestBody DenunciaModeracaoRequest request) {
		return service.analisar(denunciaId, request);
	}

	@PostMapping(value = "/{denunciaId}/procedencia", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public DenunciaResponse proceder(@PathVariable UUID denunciaId, @Valid @RequestBody DenunciaModeracaoRequest request) {
		return service.proceder(denunciaId, request);
	}

	@PostMapping(value = "/{denunciaId}/improcedencia", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public DenunciaResponse improceder(@PathVariable UUID denunciaId, @Valid @RequestBody DenunciaModeracaoRequest request) {
		return service.improceder(denunciaId, request);
	}

	private PageRequest pagina(int page, int size) { return PageRequest.of(page, size, Sort.by("dataCadastro", "id")); }
}
