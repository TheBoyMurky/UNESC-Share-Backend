package br.com.murkyweb.unesc_share.compartilhamento.magnet.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.dto.MagnetAdicaoRequest;
import br.com.murkyweb.unesc_share.material.dto.MaterialDetalheResponse;
import br.com.murkyweb.unesc_share.material.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@RequestMapping("/materiais/{materialId}/magnets")
@Tag(name = "Magnets", description = "Cadastro de Magnet URIs como mecanismos independentes associados a materiais.")
@SecurityRequirement(name = "bearerAuth")
public class MagnetController {
	private final MaterialService materiais;

	/** Adiciona um lote e devolve o estado atualizado do Material, sem substituir mecanismos. */
	@PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
	public MaterialDetalheResponse adicionar(@PathVariable UUID materialId, @Valid @RequestBody MagnetAdicaoRequest request) {
		return materiais.adicionarMagnets(materialId, request);
	}
}
