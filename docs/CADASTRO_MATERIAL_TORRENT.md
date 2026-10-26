# Cadastro inicial de Material com .torrent

## Entrega

Agora e possivel cadastrar um Material com um unico `.torrent` v1, sem informar Magnet. O endpoint JSON anterior permanece compativel e continua exigindo 1..10 Magnets.

Foi escolhida a extensao do mesmo recurso por Content-Type, em vez de uma rota `/com-torrent`: evita duplicar a identidade do recurso e usa o [mapeamento por tipo de midia nativo do Spring Web](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html).

| Requisicao a POST /api/v1/materiais | Mecanismo inicial |
| --- | --- |
| application/json ou application/*+json | 1..10 Magnets; contrato anterior |
| multipart/form-data | Um arquivo .torrent v1 + parte JSON material |

Nao existe etapa HTTP que crie material vazio para um segundo envio. Nao ha upload de PDF/EPUB/MP4/ZIP nem download/extracao de conteudo academico. Torrent e Magnet permanecem entidades independentes.

## Contrato multipart

Exige Bearer JWT valido e usuario persistido, de qualquer perfil. Envie exatamente duas partes, uma vez cada:

- `material`: JSON com Content-Type `application/json`, contendo titulo, descricao, disciplinaId e categoriaId. Pode ser uma parte comum ou um Blob JSON com filename, como gerado pelo navegador.
- `arquivo`: um arquivo com nome `.torrent`, cujo corpo precisa ser metainfo v1 valido. Extensao/MIME declarados nao substituem a validacao.

JSON de `material`:

```json
{
  "titulo": "Material autorizado",
  "descricao": "Descricao do conteudo, origem e permissao de compartilhamento.",
  "disciplinaId": "00000000-0000-0000-0000-000000000001",
  "categoriaId": "00000000-0000-0000-0000-000000000002"
}
```

Substitua os UUIDs por referencias existentes. Titulo/descricao obrigatorios, limites de 200/4000 caracteres e strip, iguais ao cadastro anterior. O servidor fixa autor pelo JWT, estado PENDENTE, zero visualizacoes e IDs gerados. Nao aceita escolher hash ou caminho de disco.

Campos JSON desconhecidos continuam ignorados, como no contrato anterior: usuarioId/status/visualizacoes/infoHash nao sobrepoem as regras. `magnetUris` nessa parte nao cria Magnets; cadastro misto inicial nao foi implementado. Partes multipart extras/duplicadas sao rejeitadas, incluindo arquivos academicos adicionais.

Sucesso: `201 Created`, Location do novo `/api/v1/materiais/{id}` e `MaterialDetalheResponse`, com `magnets: []` e um DTO em `torrents`. Nao confundir esse Location com o da adicao posterior de Torrent, que aponta ao download. O download usa `/api/v1/materiais/{materialId}/torrents/{torrentId}` e segue a visibilidade do Material.

O material nao aparece na busca geral ate publicacao pela equipe. Um material com apenas Torrent pode ser publicado, editado ou removido logicamente usando as mesmas regras de autoria/estado existentes; mecanismos sao preservados. Sem privilegios administrativos no cadastro publico.

## Estrutura e codigo

Os arquivos completos estao no workspace, nestes caminhos relativos a raiz:

```text
src/main/java/br/com/murkyweb/unesc_share/material/
├── controller/MaterialController.java
├── dto/MaterialCadastroTorrentRequest.java
└── service/MaterialService.java
src/main/resources/application.properties
src/test/resources/application-test.properties

src/test/java/br/com/murkyweb/unesc_share/
├── material/
│   ├── MaterialCadastroTorrentApiTests.java
│   ├── MaterialCadastroApiTests.java
│   └── TorrentTransacaoTests.java
└── compartilhamento/torrent/TorrentHttpTests.java
```

O controller usa dois `@PostMapping(consumes=...)`, com [@RequestPart e @Valid nativos](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html) para conversao JSON e Bean Validation. Confere tambem a cardinalidade das partes Servlet: RequestPart isoladamente seleciona uma parte, nao e uma politica de rejeicao a duplicatas.

O DTO de cadastro por Torrent tem contrato proprio, sem magnetUris obrigatorio, e nao reutiliza um DTO de edicao com semantica diferente. Os limites dos metadados permanecem os mesmos.

`MaterialService` compartilha a resolucao de autor/referencias entre os dois cadastros. `cadastrarComTorrent` inicia/participa da transacao REQUIRED, insere o Material e chama o `TorrentService.adicionar` existente, que participa da mesma transacao. Nao ha ciclo entre services: TorrentService usa MaterialRepository, nao MaterialService.

O novo UUID e a linha inserida sao internos ate o commit; nao representam material vazio publicado por uma primeira requisicao. Falha na validacao do arquivo/hash, storage ou persistencia reverte tambem o Material. Reutilizar o fluxo de Torrent evita duplicar parser, controle de quantidade, checagem de autoria, InfoHashGuard, storage e tratamento de conflito.

Nenhuma entidade, schema, repository, dependencia ou variavel de ambiente nova. O servico de consulta e os DTOs de detalhe existentes ja retornam os Torrents.
O perfil test limita o pool Hikari a tres conexoes por contexto, com minimum-idle zero: varios contextos Spring em cache nao devem esgotar o limite do PostgreSQL local. Isso preserva o teste de duas transacoes simultaneas de hash e nao altera o pool de dev/producao.

## Limites e seguranca

Continuam valendo as regras de [TORRENTS.md](TORRENTS.md): v1 somente, 2 MiB por arquivo, 3 MiB por requisicao multipart, ate 10 Torrents por Material, hash normalizado e lock PostgreSQL coordenando as duas tabelas. Hash ja associado a outro Material retorna 409, inclusive removido/bloqueado, sem reassociacao automatica ou novo material orfao.

Storage privado em `TORRENT_STORAGE_DIR` (padrao ./data/torrents), chave UUID sem nome/caminho externo e limpeza do arquivo criado em rollback. A limitacao banco+filesystem permanece: nao e uma transacao distribuida; crash/commit desconhecido/falha de limpeza pode exigir reconciliacao operacional. Nenhum arquivo legado e movido ou excluido automaticamente.

O multipart agora e resolvido antecipadamente pelo mecanismo nativo (`spring.servlet.multipart.resolve-lazily=false`), apos os filtros de seguranca e antes da resolucao das partes do controller. Isso preserva o tratamento nativo de excesso de tamanho com 413 mesmo quando a primeira parte resolvida e JSON. A resolucao tardia podia embrulhar esse erro como MultipartException generica ao obter os headers da parte, resultando em 400. Nao foi criado filtro/parser HTTP ou classificador de mensagens de excecao proprio.

Erros usam ProblemDetail/ResponseEntityExceptionHandler e handlers Bearer existentes:

- 400: campos/JSON/UUID/metainfo invalidos, partes ausentes/duplicadas/extras ou multipart malformado.
- 401: token ausente/invalido/expirado ou subject sem usuario persistido.
- 404: disciplina/categoria inexistente.
- 409: hash duplicado/em outro Material ou conflito de integridade.
- 413: limite de arquivo/requisicao excedido.
- 415: tipo de requisicao nao suportado ou parte material sem tipo JSON compativel.
- 503: storage indisponivel.

O PUT de metadados continua JSON-only; nao substitui mecanismos. O POST JSON ainda rejeita ausencia de Magnets, e PDF direto continua 415. JWT continua stateless, de curta duracao e sem cookies/refresh/revogacao imediata. Nao registrar corpos multipart, tokens ou dados privados de trackers em logs.

## Como executar e testar

Com PostgreSQL disponivel, mesmas variaveis locais e bootstrap administrativo desativado:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Para teste manual, crie um arquivo local `material.json` com o JSON acima e use um `.torrent` v1 proprio/autorizado. O arquivo JSON e somente um exemplo local de requisicao, nao foi criado automaticamente. No terminal, TOKEN deve conter um token obtido pelo login normal; nao cole credenciais/tokens no chat ou no Git.

```bash
curl --fail-with-body \
  -H "Authorization: Bearer ${TOKEN}" \
  -F 'material=@material.json;type=application/json' \
  -F 'arquivo=@/caminho/absoluto/exemplo.torrent;type=application/x-bittorrent' \
  http://localhost:8080/api/v1/materiais
```

Nao defina o boundary manualmente; o cliente HTTP o gera. Teste manual bem-sucedido cria dados reais; exclusao de Material e logica. Tokens na linha de comando podem aparecer na lista de processos; prefira um cliente que proteja segredos para uso real.

O frontend separado implementou esse fluxo em23/10/2026: `FormData` com `material` como Blob application/json e `arquivo` como File, sem Content-Type global; o navegador adiciona o boundary. Escolha exclusiva Magnets ou .torrent no mesmo formulario, sem cadastro misto. Guia de codigo/testes/limites em `../UNESC-Share-Frontend/docs/CADASTRO_MATERIAL_TORRENT_FRONTEND.md`.385 testes frontend/type-check/build aprovados; HTTP sintetico nao substitui teste Spring/navegador, e a suite Maven nao foi reexecutada nesse incremento frontend.

Testes automatizados, apos carregar o ambiente silenciosamente no terminal:

```bash
./mvnw -Dtest=MaterialCadastroTorrentApiTests,MaterialCadastroApiTests,TorrentTransacaoTests,TorrentHttpTests test
./mvnw clean verify
```

Testes de API usam JWT/Spring Security reais, PostgreSQL, fixtures sinteticos, diretorio temporario e rollback. Cobrem os tres perfis, 201/Location, normalizacao, ausencia de Magnets, publicacao/download, JSON como Blob, identidade exclusiva do JWT, campos extras, partes invalidas, limites, hashes cruzados, preservacao do cadastro JSON/vendor+json e rollback sem material orfao.

O teste de falha injeta erro apos inserir Material e Torrent/dar flush, com arquivo presente; encerra a transacao com rollback e confere em outra transacao que registros e arquivo desapareceram. Outro caso simula storage indisponivel. Isso nao e um teste de queda abrupta ou atomicidade distribuida.

Testes HTTP reais em loopback/porta aleatoria confirmam conversao das partes JSON comum/Blob, rejeicao de JSON malformado/tipo errado/parte extra, PDF direto415 e limiteServlet413. Nao inserem dados: usam subject sintetico sem usuario persistido quando a requisicao passa pelo controller. Nao acessam trackers nem a rede BitTorrent. Nenhum comando Docker deve ser executado pelo agente.

Proximas entregas: gestao de mecanismos (com politica para impedir remocao do ultimo), cadastro misto inicial/lotes e compatibilidade v2/hibrida, conforme aprovacao. Moderacao completa, auditoria, quotas/rate limiting e reconciliacao operacional continuam pendentes.

## Verificacao executada

Em 20/10/2026, `./mvnw clean verify` passou com **337 testes**, zero falhas, erros ou testes ignorados, e gerou o JAR executavel. Sao 22 casos novos: 17 de API, 2 de falha transacional/storage e 3 de HTTP real, alem dos 315 anteriores. Os conjuntos selecionados passaram antes da suite final.

O HTTP real identificou a resposta400 indevida para excesso de tamanho na resolucao multipart tardia; a configuracao antecipada corrigiu para413 nativo. A primeira suite completa atingiu o limite de conexoes PostgreSQL com os pools dos contextos em cache; apos limitar somente o perfil test a tres conexoes/minimum-idle0, todos os337 passaram. Nao foi alterada a configuracao do banco/Docker nem a de pool normal do backend.

Backend atualizado reiniciado em localhost:8080, context-path `/api/v1`, com bootstrap administrativo desativado e zero novas disciplinas na carga. O JAR inclui o DTO de producao e nao inclui a nova classe de testes, fixtures ou application-test.properties. `git diff --check` passou. Nenhum material permanente foi criado, nenhum tracker/conteudo foi acessado, nenhum comando Docker foi executado e o `.env` real nao foi alterado pelo agente.
