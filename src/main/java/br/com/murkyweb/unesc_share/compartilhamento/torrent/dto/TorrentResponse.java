package br.com.murkyweb.unesc_share.compartilhamento.torrent.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.murkyweb.unesc_share.compartilhamento.torrent.domain.Torrent;

/** tamanho e o tamanho do .torrent, nao do conteudo academico. Nao expoe caminhoArquivo. */
public record TorrentResponse(UUID id, String nomeArquivo, String infoHash, long tamanho, Instant dataCadastro) {
	public static TorrentResponse from(Torrent torrent) {
		return new TorrentResponse(torrent.getId(), torrent.getNomeArquivo(), torrent.getInfoHash(), torrent.getTamanho(), torrent.getDataCadastro());
	}
}
