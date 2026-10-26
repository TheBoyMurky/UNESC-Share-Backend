# Materiais com Magnet URI — primeiro incremento

O usuario aprovou iniciar pelos Magnets, mantendo as entidades independentes e exigindo compartilhamento desde o cadastro. Este documento descreve esse contrato JSON inicial. As entregas seguintes acrescentaram adicao/download de `.torrent` v1 conforme [TORRENTS.md](TORRENTS.md) e [cadastro inicial com Torrent](CADASTRO_MATERIAL_TORRENT.md), sem upload de arquivos academicos nem moderacao completa por denuncias.

## Decisoes aprovadas

- Cadastro atomico de `Material` e de 1 a 10 registros `Magnet`, com validacao de todos antes da insercao. Falhar em qualquer etapa reverte a transacao inteira.
- O autor vem exclusivamente do UUID do JWT validado pelo Spring Security; nao e aceito do JSON.
- Todo cadastro nasce `PENDENTE`, com zero visualizacoes. A busca geral retorna somente `PUBLICADO`, sempre para usuarios autenticados.
- O autor consulta seus materiais em todos os estados. A equipe de moderacao (MODERADOR/ADMINISTRADOR) pode consultar registros nao publicados para analise/auditoria. Terceiros recebem 404 nesses detalhes.
- Apenas o autor edita seus materiais PENDENTE/PUBLICADO. Qualquer PUT completo coloca o material em PENDENTE, inclusive ao reenviar os mesmos dados. BLOQUEADO/REMOVIDO nao podem ser editados.
- Editar altera titulo, descricao, disciplina e categoria, preservando autor, UUID e Magnets. A adicao posterior de Torrent esta em TORRENTS.md e a de Magnets em MAGNETS.md; nao ha edicao/remocao individual de mecanismos ainda.
- Apenas o autor usa a remocao logica do proprio registro. Administrador/moderador nao impersonam o autor para editar/remover materiais de terceiros; usam a operacao de bloqueio.
- REMOVIDO preserva os dados e os relacionamentos. Repetir a remocao pelo autor retorna 204. Nao existe restauracao ou desbloqueio neste incremento.

### Transicoes

| Operacao | Ator | Estado anterior | Estado seguinte |
| --- | --- | --- | --- |
| Cadastro com Magnets validos | Autenticado | Novo | PENDENTE |
| PUT dos metadados | Autor | PENDENTE/PUBLICADO | PENDENTE |
| Publicacao | Moderador/administrador | PENDENTE com mecanismo | PUBLICADO |
| Bloqueio | Moderador/administrador | PENDENTE/PUBLICADO/BLOQUEADO | BLOQUEADO |
| Remocao logica | Autor | Qualquer estado | REMOVIDO |

Nao existe PATCH generico para o cliente escolher qualquer estado. Publicar bloqueado, removido ou ja publicado retorna 409; bloquear removido tambem retorna 409. Repetir bloqueio e permitido.
A publicacao verifica a existencia de ao menos um Magnet ou Torrent associado, inclusive para registros legados. Essa verificacao nao e uma auditoria de arquivos `.torrent` nem consulta de disponibilidade na rede.

## Estrutura dos arquivos

Os arquivos completos estao nestes caminhos relativos a raiz do repositorio:

```text
src/main/java/br/com/murkyweb/unesc_share/
├── material/
│   ├── controller/MaterialController.java
│   ├── dto/MaterialCadastroRequest.java
│   ├── dto/MaterialEdicaoRequest.java
│   ├── dto/MaterialFiltro.java
│   ├── dto/MaterialResumoResponse.java
│   ├── dto/MaterialDetalheResponse.java
│   ├── repository/MaterialRepository.java
│   ├── repository/MaterialSpecifications.java
│   ├── service/MaterialService.java
│   └── service/MaterialQueryService.java
├── compartilhamento/magnet/
│   ├── dto/MagnetResponse.java
│   ├── repository/MagnetRepository.java
│   ├── service/MagnetParser.java
│   └── service/MagnetService.java
├── compartilhamento/torrent/repository/TorrentRepository.java
└── security/
    ├── UsuarioAutenticado.java
    └── SecurityConfig.java

src/test/java/br/com/murkyweb/unesc_share/
├── compartilhamento/magnet/MagnetParserTests.java
├── compartilhamento/magnet/MagnetServiceTests.java
└── material/
    ├── MaterialApiSupport.java
    ├── MaterialCadastroApiTests.java
    ├── MaterialAcessoApiTests.java
    ├── MaterialFluxoApiTests.java
    └── MaterialTransacaoTests.java
```

O controller usa Bean Validation e os contratos nativos de HTTP. `MaterialService` organiza escritas/transicoes; `MaterialQueryService` aplica visibilidade e converte resultados para DTOs.
`UsuarioAutenticado` apenas le a identidade/authorities do contexto ja validado pelo Resource Server: nao autentica nem decodifica tokens e nao e um filtro JWT proprio.
A busca usa `JpaSpecificationExecutor`/Criteria API, com filtros parametrizados e escaping dos caracteres `%`, `_` e barra invertida na pesquisa literal. Nao sao concatenados trechos SQL a partir de dados recebidos.
As mutacoes de registros existentes usam `@Lock(PESSIMISTIC_WRITE)` dentro da transacao para serializar mudancas de estado. Nao foi adicionado `@Version`, ETag ou controle da versao que o moderador consultou anteriormente; a publicacao atua sobre o registro atual. Uma fila aberta deve ser atualizada antes de decidir, e protecao explicita contra aprovacao de uma versao desatualizada fica para futura evolucao.
`MagnetService.associar` exige transacao existente (`Propagation.MANDATORY`). O parser usa URI/URLDecoder do JDK e regras do dominio. As entidades/tabelas/constraints permanecem as existentes; nao ha nova dependencia ou migration.

## Endpoints

Todos exigem Bearer JWT. Os caminhos da tabela sao relativos a base `/api/v1`:

| Metodo | Caminho | Regra |
| --- | --- | --- |
| POST | `/materiais` | Autenticado; JSON com Magnets ou multipart com Torrent; 201 + Location |
| GET | `/materiais` | Autenticado; pesquisa somente PUBLICADO |
| GET | `/materiais/meus` | Identidade do token; todos os estados ou filtro de status |
| GET | `/materiais/moderacao` | Moderador/administrador; PENDENTE por padrao |
| GET | `/materiais/{id}` | PUBLICADO para autenticados; outros estados para autor/equipe |
| PUT | `/materiais/{id}` | Apenas autor, com regras de estado; 200 |
| DELETE | `/materiais/{id}` | Apenas autor; remocao logica; 204 |
| POST | `/materiais/{id}/publicacao` | Moderador/administrador; 200 |
| POST | `/materiais/{id}/bloqueio` | Moderador/administrador; 200 |
| POST | `/materiais/{id}/magnets` | Autor em PENDENTE/PUBLICADO; lote atomico, limite acumulado10, retorna detalhe200/PENDENTE |

Na tabela, prefixe cada caminho com `/api/v1`, por exemplo `POST /api/v1/materiais`. O controller e os matchers nao repetem o context-path.

### Cadastro

```json
{
  "titulo": "Material academico autorizado",
  "descricao": "Descricao do conteudo, origem e permissao de compartilhamento.",
  "disciplinaId": "00000000-0000-0000-0000-000000000001",
  "categoriaId": "00000000-0000-0000-0000-000000000002",
  "magnetUris": [
    "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Exemplo"
  ]
}
```

Substitua os UUIDs por registros existentes. O hash acima e apenas exemplo de formato e nao garante conteudo disponivel.
Titulo e descricao sao obrigatorios, com limites de 200 e 4000 caracteres; espacos externos sao removidos. Disciplina/categoria sao obrigatorias. Cada URI tem ate 4000 caracteres e sao exigidas de 1 a 10.
Autor, status, contador e IDs gerados nao podem ser definidos pelo cliente. Campos extras nao permitem substituir essas regras ou associar entidades JPA arbitrarias.
PUT recebe apenas titulo, descricao, disciplinaId e categoriaId, todos obrigatorios; nao e upsert e nao aceita alteracao de mecanismos. Um `magnetUris` extra em PUT nao e aplicado.

### Respostas

A listagem devolve `PagedModel` com resumo: id, titulo, status, dataCadastro, visualizacoes e UUIDs de autor/disciplina/categoria. Nao faz uma consulta de Magnets por item da pagina.
O detalhe acrescenta descricao e `magnets`, cada um com UUID proprio, URI, Info Hash e dataCadastro. A entrega de Torrent acrescenta `torrents` com DTOs de metainfo, sem caminho local. Nao expoe entidades JPA, senha/hash/email do autor nem caminhos de arquivos do servidor.
As datas continuam `Instant`/UTC. GET nao incrementa visualizacoes neste incremento; a politica do contador sera definida depois.
O detalhe de REMOVIDO fica acessivel somente ao autor/equipe para auditoria. Ocultar no catalogo nao revoga copias de Magnet URI nem remove conteudo da rede BitTorrent.

### Busca, filtros e paginacao

```text
GET /api/v1/materiais?q=algoritmos&instituicaoId={id}&cursoId={id}&disciplinaId={id}&categoriaId={id}&page=0&size=20
GET /api/v1/materiais/meus?status=PENDENTE&page=0&size=20
GET /api/v1/materiais/moderacao?status=PENDENTE&page=0&size=20
```

Os mesmos filtros academicos/textuais podem ser combinados nas tres listas. `q` tem ate 200 caracteres e procura trecho literal de titulo/descricao, sem distinguir caixa. Filtros sao combinados com AND; referencias sem resultado ou filtros academicos inconsistentes retornam pagina vazia.
`page` inicia em zero e `size` aceita de 1 a 100 (padrao 20). Ordem fixa: dataCadastro DESC, id DESC.
Somente `/meus` e `/moderacao` possuem filtro de status. Parametros extras `usuarioId` ou `status` na busca geral nao substituem os predicados do servidor. `/meus` nunca aceita escolher outro autor.

## Validacao dos Magnets

A [BEP 9](https://www.bittorrent.org/beps/bep_0009.html) descreve os topicos v1 `urn:btih` e v2 `urn:btmh`, alem dos parametros opcionais. Este incremento aceita um unico `xt`:

- V1: hash hexadecimal de 40 caracteres ou Base32 de 32; ambos viram o mesmo hash hexadecimal minusculo, preservando zeros.
- V2: `urn:btmh:1220` seguido por 64 caracteres hexadecimais. O [BEP 52](https://www.bittorrent.org/beps/bep_0052.html) usa SHA2-256, e a [tabela multicodec](https://raw.githubusercontent.com/multiformats/multicodec/master/table.csv) identifica esse algoritmo pelo codigo 0x12; 0x20 indica os 32 bytes. O banco guarda o hash completo de 64 caracteres, sem o prefixo multihash.

A URI armazenada e normalizada para `magnet:?xt=...`, com topico/hash canonicos. Parametros adicionais sao preservados, inclusive trackers repetidos; o servidor nao os acessa, nao resolve peers e nao baixa metadados ou conteudo.
Limites adicionais: ate 64 parametros, nomes de parametro ASCII alfanumericos/ponto/sublinhado/hifen com ate 40 caracteres; rejeita fragmentos, caminhos/authority, escapes invalidos, espacos crus, controles e Unicode substituto isolado. Espacos em nomes de exibicao devem estar codificados.
Magnets hibridos com varios `xt`, ainda que previstos pela BEP, retornam 400 como nao suportados neste incremento: o modelo tem um unico Info Hash por entidade. Outros algoritmos multihash tambem ficam fora deste escopo.

Repetir um hash na mesma requisicao, inclusive via Base32/hex ou alterando trackers, retorna 409. Hash ja presente em `magnets` tambem retorna 409, mesmo que o material esteja bloqueado/removido, conforme as constraints atuais. Nao ha reassociacao automatica de Magnet a outro material.
A entrega de Torrent acrescentou verificacao cruzada protegida por lock transacional: um hash so pode pertencer a um Material entre as duas tabelas, permitindo Torrent+Magnet nesse mesmo Material. Novo cadastro com Magnet cujo hash ja existe em Torrent de outro cadastro retorna 409. Nao presume equivalencia entre hashes v1/v2; detalhes em TORRENTS.md.
Normalizacao + constraints protegem hashes iguais cadastrados por esta API sob concorrencia; as constraints textuais atuais nao substituem um indice case-insensitive para escritas externas que ignorem a normalizacao.
A validacao e sintatica e de formato/identidade: nao comprova disponibilidade, seguranca ou licenca do conteudo. Compartilhe apenas materiais que possam ser legitimamente distribuidos e nao inclua credenciais pessoais/chaves de trackers privados nas URIs publicadas.

## Erros e seguranca

- 400: campos/filtros/UUIDs invalidos ou Magnet invalido/nao suportado.
- 401: token ausente/invalido/expirado; no cadastro, identidade sem usuario persistido.
- 403: usuario sem perfil de moderacao tentando acessar fila/publicacao/bloqueio.
- 404: material inexistente, privado para o solicitante, ou operacao de autor sobre material de terceiro; referencias inexistentes no cadastro/edicao.
- 409: hash duplicado, conflito de integridade ou transicao de estado proibida.
- 415: tipo nao suportado, como PDF direto; multipart de cadastro agora possui contrato proprio em CADASTRO_MATERIAL_TORRENT.md.

Erros MVC usam `ResponseEntityExceptionHandler`/`ProblemDetail` e erros de seguranca usam handlers Bearer nativos. Nao ha credenciais ou tokens em logs, nem valores rejeitados nos erros da API.
Representacoes `toString` do cadastro, resultado do parser e DTO de Magnet redigem a URI para evitar vazamento acidental de parametros privados em logs; a serializacao JSON autorizada continua retornando a URI.
Multipart foi habilitado nas rotas de Torrent e de cadastro inicial de Material, com limites e validacao especificos. POST JSON com Magnets continua compativel e PUT de metadados continua JSON-only; nenhuma rota aceita PDF direto. Isso nao e limite global de bytes para todo JSON; rate limiting e limites adicionais de infraestrutura ainda estao pendentes.
JWT continua de curta duracao, sem refresh/revogacao imediata. Em25/10/2026 o usuário aprovou manter autoaprovação de Material para MODERADOR e ADMINISTRADOR, inclusive material próprio; não aplicar o impedimento específico da moderação de Denuncia a Material. Publicação continua exigindo PENDENTE com mecanismo. Não há histórico de decisão de Material, motivo de bloqueio, desbloqueio/restauração ou ETag. Regressão explícita dos dois perfis em MaterialFluxoApiTests; interface de decisão documentada no frontend em docs/DECISOES_MATERIAL_FRONTEND.md. Nao considerar esta base pronta para publicacao irrestrita em producao.

## Executar e testar

Na raiz do repositorio, com PostgreSQL disponivel:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Nao reative o bootstrap administrativo nem reintroduza suas credenciais. Obtenha os tokens pelo login normal para testes manuais, mantendo a senha fora do historico do shell e do chat.
Use apenas material proprio/autorizado, sem alterar o catalogo oficial para preparar testes. Cada cadastro validado cria registros reais; exclusao e logica.

Testes automatizados:

```bash
./mvnw -Dtest=MagnetParserTests,MagnetServiceTests,MaterialCadastroApiTests,MaterialAcessoApiTests,MaterialFluxoApiTests,MaterialTransacaoTests test
./mvnw clean verify
```

Os testes usam PostgreSQL, filtros/encoder/decoder JWT nativos e tokens exclusivamente de teste. Cobrem formato/canonicalizacao, Base32/v2, duplicidades, perfis, identidade/visibilidade, busca literal/filtros, transicoes, remocao logica e ausencia de upload.
O teste de atomicidade injeta falha apos inserir e dar flush em Material e dois Magnets; encerra a transacao com rollback e verifica em outra transacao que ambos desapareceram. Nao e simulacao de duas requisicoes concorrentes reais.
Os fixtures usam transacoes revertidas e os runners ficam desativados no perfil test. Nao criam materiais reais permanentes nem consultam a rede BitTorrent. O Hibernate ainda pode atualizar o schema por `ddl-auto=update`.
Nenhum comando Docker e executado pelo agente. Qualquer informacao/operacao Docker deve ser solicitada ao usuario.

Verificacao final em 20/10/2026: `./mvnw clean verify` passou com 241 testes, zero falhas, erros ou testes ignorados, e gerou o JAR executavel. O incremento acrescentou 75 casos de materiais/Magnets; o conjunto inicial de 73 passou isoladamente antes de dois casos adicionais de endurecimento do parser/representacoes textuais.
O backend atualizado foi reiniciado em localhost:8080, com bootstrap administrativo desativado e zero novas disciplinas criadas pela carga academica. Nao foram solicitadas credenciais reais nem criados materiais permanentes para testar; nenhum comando Docker foi executado e o `.env` real permaneceu inalterado pelo agente.

## Proximas entregas

Adicao/download de `.torrent` v1 e politica de hashes cruzados implementados em TORRENTS.md; cadastro inicial sem Magnet em CADASTRO_MATERIAL_TORRENT.md; adicao posterior de Magnets em MAGNETS.md. Proximos incrementos: politica de edicao/remocao de mecanismos e interacoes/moderacao, historico/motivo, analise de denuncias, desbloqueio/restauracao e protecao contra aprovacao de versao desatualizada, conforme prioridade/decisoes aprovadas.
