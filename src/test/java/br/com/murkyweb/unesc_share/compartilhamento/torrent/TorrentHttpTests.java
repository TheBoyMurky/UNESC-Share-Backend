package br.com.murkyweb.unesc_share.compartilhamento.torrent;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;

import br.com.murkyweb.unesc_share.compartilhamento.torrent.service.TorrentParser;
import br.com.murkyweb.unesc_share.security.JwtProperties;

/** Servidor HTTP real: MockMvc nao aplica os limites multipart do Servlet. Sem inserts. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
class TorrentHttpTests {
	@LocalServerPort int port;
	@Autowired JwtEncoder encoder;
	@Autowired JwtProperties properties;
	private static final String BOUNDARY = "unescshare-test-boundary";
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private HttpResponse<String> post(byte[] body, String type) throws Exception {
		return postAt("/materiais/" + UUID.randomUUID() + "/torrents", body, type);
	}
	private HttpResponse<String> postAt(String path, byte[] body, String type) throws Exception {
		var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
				.subject(UUID.randomUUID().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(120))
				.claim("roles", List.of("USUARIO")).build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
				.timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token)
				.header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
	private byte[] multipart(byte[] file) {
		var output = new ByteArrayOutputStream();
		output.writeBytes(TorrentFixtures.bytes("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"teste.torrent\"\r\n"
				+ "Content-Type: application/octet-stream\r\n\r\n"));
		output.writeBytes(file); output.writeBytes(TorrentFixtures.bytes("\r\n--" + BOUNDARY + "--\r\n"));
		return output.toByteArray();
	}
	@Test void servletRejeitaArquivoAcimaDeDoisMiBComProblemDetail413() throws Exception {
		var response = post(multipart(new byte[TorrentParser.MAX_BYTES + 1]), "multipart/form-data; boundary=" + BOUNDARY);
		assertEquals(413, response.statusCode());
		assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/problem+json"));
		assertFalse(response.body().contains("filename"));
	}
	@Test void multipartSemBoundaryRetorna400SemDetalheInterno() throws Exception {
		var response = post(TorrentFixtures.bytes("corpo invalido"), "multipart/form-data");
		assertEquals(400, response.statusCode()); assertTrue(response.body().contains("Requisicao multipart invalida"));
		assertFalse(response.body().contains("Exception"));
	}
	@Test void multipartPequenoChegaAoControllerEArquivoAcademicoDiretoRetorna415() throws Exception {
		assertEquals(404, post(multipart(TorrentFixtures.torrent("sintetico")), "multipart/form-data; boundary=" + BOUNDARY).statusCode());
		assertEquals(415, post(TorrentFixtures.bytes("%PDF-1.7"), "application/pdf").statusCode());
	}
	private byte[] cadastro(String json, String metadataType, boolean blob, byte[] file, boolean extra) {
		var output = new ByteArrayOutputStream();
		output.writeBytes(TorrentFixtures.bytes("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"material\""
				+ (blob ? "; filename=\"blob\"" : "") + "\r\nContent-Type: " + metadataType + "\r\n\r\n" + json + "\r\n"));
		output.writeBytes(TorrentFixtures.bytes("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"teste.torrent\""
				+ "\r\nContent-Type: application/x-bittorrent\r\n\r\n"));
		output.writeBytes(file);
		if (extra) { output.writeBytes(TorrentFixtures.bytes("\r\n--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"extra\"\r\n\r\nextra")); }
		output.writeBytes(TorrentFixtures.bytes("\r\n--" + BOUNDARY + "--\r\n"));
		return output.toByteArray();
	}
	private String metadados() {
		return "{\"titulo\":\"Titulo\",\"descricao\":\"Descricao\",\"disciplinaId\":\"" + UUID.randomUUID()
				+ "\",\"categoriaId\":\"" + UUID.randomUUID() + "\"}";
	}
	@Test void cadastroMultipartRealResolveJsonComOuSemFilenameAntesDeExigirUsuarioPersistido() throws Exception {
		for (boolean blob : List.of(false, true)) {
			// Subject sintetico inexistente: controller/DTO/partes passam, mas service retorna401 sem INSERT.
			var body = cadastro(metadados(), "application/json", blob, TorrentFixtures.torrent("sintetico"), false);
			var response = postAt("/materiais", body, "multipart/form-data; boundary=" + BOUNDARY);
			assertEquals(401, response.statusCode()); assertTrue(response.body().contains("Usuario nao disponivel"));
		}
	}
	@Test void cadastroMultipartRealRejeitaJsonInvalidoTipoErradoEParteExtra() throws Exception {
		assertEquals(400, postAt("/materiais", cadastro("{invalido", "application/json", false, TorrentFixtures.torrent("x"), false),
				"multipart/form-data; boundary=" + BOUNDARY).statusCode());
		assertEquals(415, postAt("/materiais", cadastro(metadados(), "text/plain", false, TorrentFixtures.torrent("x"), false),
				"multipart/form-data; boundary=" + BOUNDARY).statusCode());
		assertEquals(400, postAt("/materiais", cadastro(metadados(), "application/json", false, TorrentFixtures.torrent("x"), true),
				"multipart/form-data; boundary=" + BOUNDARY).statusCode());
	}
	@Test void cadastroMultipartRealAplicaLimiteDoServletERejeitaPdfDireto() throws Exception {
		var response = postAt("/materiais", cadastro(metadados(), "application/json", false, new byte[TorrentParser.MAX_BYTES + 1], false),
				"multipart/form-data; boundary=" + BOUNDARY);
		assertEquals(413, response.statusCode(), response.body());
		assertEquals(415, postAt("/materiais", TorrentFixtures.bytes("%PDF-1.7"), "application/pdf").statusCode());
	}
}
