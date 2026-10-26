package br.com.murkyweb.unesc_share.compartilhamento.magnet.service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Valida apenas a URI e o identificador; nunca consulta trackers, peers ou conteudo. */
@Component
public class MagnetParser {

	public MagnetValidado parse(String valor) {
		if (valor == null || valor.isBlank() || valor.length() > 4000) { throw invalido(); }
		String texto = valor.strip();
		if (texto.codePoints().anyMatch(c -> Character.isWhitespace(c) || proibido(c))) { throw invalido(); }
		try {
			URI uri = URI.create(texto);
			String parte = uri.getRawSchemeSpecificPart();
			if (!"magnet".equalsIgnoreCase(uri.getScheme()) || uri.getRawFragment() != null
					|| parte == null || !parte.startsWith("?")) { throw invalido(); }
			String[] parametros = parte.substring(1).split("&", -1);
			if (parametros.length > 64) { throw invalido(); }
			String topico = null;
			String hash = null;
			var extras = new ArrayList<String>();
			for (String parametro : parametros) {
				int igual = parametro.indexOf('=');
				if (igual < 1) { throw invalido(); }
				String nome = URLDecoder.decode(parametro.substring(0, igual), StandardCharsets.UTF_8);
				String conteudo = URLDecoder.decode(parametro.substring(igual + 1), StandardCharsets.UTF_8);
				if (!nome.matches("[A-Za-z0-9._-]{1,40}") || conteudo.codePoints().anyMatch(MagnetParser::proibido)) { throw invalido(); }
				if (nome.equals("xt")) {
					if (topico != null) { throw invalido(); }
					String alvo = conteudo.toLowerCase(Locale.ROOT);
					if (alvo.startsWith("urn:btih:")) {
						hash = normalizarV1(conteudo.substring(9));
						topico = "urn:btih:" + hash;
					} else if (alvo.matches("urn:btmh:1220[0-9a-f]{64}")) {
						hash = alvo.substring(13);
						topico = "urn:btmh:1220" + hash;
					} else { throw invalido(); }
				} else { extras.add(parametro); }
			}
			if (topico == null) { throw invalido(); }
			String canonico = "magnet:?xt=" + topico + (extras.isEmpty() ? "" : "&" + String.join("&", extras));
			if (canonico.length() > 4000) { throw invalido(); }
			return new MagnetValidado(canonico, hash);
		} catch (IllegalArgumentException exception) {
			throw invalido();
		}
	}

	private String normalizarV1(String hash) {
		if (hash.matches("[0-9a-fA-F]{40}")) { return hash.toLowerCase(Locale.ROOT); }
		String base32 = hash.toUpperCase(Locale.ROOT);
		if (!base32.matches("[A-Z2-7]{32}")) { throw invalido(); }
		byte[] bytes = new byte[20];
		int buffer = 0, bits = 0, indice = 0;
		for (char caractere : base32.toCharArray()) {
			int numero = caractere >= 'A' && caractere <= 'Z' ? caractere - 'A' : caractere - '2' + 26;
			buffer = (buffer << 5) | numero;
			bits += 5;
			if (bits >= 8) {
				bits -= 8;
				bytes[indice++] = (byte) (buffer >> bits);
			}
		}
		return HexFormat.of().formatHex(bytes);
	}

	private ResponseStatusException invalido() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Magnet URI invalido ou nao suportado. Informe um unico xt btih v1 ou btmh SHA-256 v2.");
	}

	private static boolean proibido(int caractere) {
		return Character.isISOControl(caractere) || caractere >= Character.MIN_SURROGATE && caractere <= Character.MAX_SURROGATE;
	}

	public record MagnetValidado(String magnetUri, String infoHash) {
		@Override public String toString() { return "MagnetValidado[magnetUri=REDACTED]"; }
	}
}
