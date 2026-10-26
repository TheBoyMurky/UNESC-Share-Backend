# Avaliacoes: cadastro e consultas

## Regras aprovadas

Primeiro incremento da API de avaliacoes: cadastro/consultas. Entidade e tabela ja existiam; esta entrega implementa DTOs, controller/service, consultas e testes, sem substituir a estrutura atual. Edicao e exclusao fisica pelo autor foram acrescentadas no [segundo incremento](AVALIACOES_EDICAO_REMOCAO.md), com regras aprovadas separadamente.

- Qualquer usuario autenticado, inclusive MODERADOR/ADMINISTRADOR, pode avaliar Material PUBLICADO de outro usuario.
- Nao e permitido avaliar o proprio Material, sem excecao de perfil (403).
- Autor vem exclusivamente do JWT; usuario do subject precisa existir no cadastro.
- Nota obrigatoria entre0,00e5,00, ate duas casas decimais, sem arredondamento silencioso. Notas com tres casas, inclusive4.000/0.000, sao400.
- Uma avaliacao por usuario/Material, com constraint unica no PostgreSQL. Segundo POST retorna409, preservando nota/UUID/data originais; nao funciona como edicao.
- Todas as consultas exigem Material PUBLICADO, tambem para dono/equipe/avaliador. Materiais nao publicados retornam404 e preservam avaliacoes anteriores no banco.
- Consulta individual somente da propria avaliacao. Resumo contem apenas media e quantidade; nao ha listagem de notas individuais de terceiros.
- Sem avaliacoes: quantidade0 e media null. Nota zero participa da media e nao significa ausencia de avaliacao.
- PUT/DELETE da propria nota seguem o segundo incremento. Nao ha moderacao de notas, historico, autoavaliacao, importacao ou limpeza retroativa pela API.

Avaliacao e independente de Material, sem novos campos/cascatas nas entidades. Nenhum arquivo academico e enviado, hospedado ou baixado.

## Contrato HTTP

Prefixo: `/api/v1/materiais/{materialId}/avaliacoes`. Autenticacao Bearer nativa exigida em todas as rotas.

| Metodo | Sufixo | Resultado |
|---|---|---|
| POST | nenhum | 201 com AvaliacaoResponse e Location da consulta `/minha` |
| GET | `/minha` | 200 com AvaliacaoResponse do usuario do JWT; 404 se ainda nao avaliou |
| GET | `/resumo` | 200 com AvaliacaoResumoResponse |
| PUT | `/minha` | 200, atualiza somente propria nota em PUBLICADO, sem autoavaliacao |
| DELETE | `/minha` | 204, exclusao fisica da propria nota em qualquer estado do Material, idempotente |

POST aceita application/json e application/*+json. Corpo completo:

```json
{
  "nota": 4.75
}
```

JSON usa ponto decimal, nao virgula. Valores4 ou4.5 tambem sao validos e normalizados para escala2 sem arredondar. Interfaces devem formatar numeros como4,00ou4,50 para exibicao brasileira; um cliente JSON pode descartar zeros finais na representacao numerica. A API usa BigDecimal, nao texto formatado com virgula.

Campos adicionais nao escolhem autor/material/id/data/estatisticas. Parametros usuarioId/materialId/status em consultas nao substituem identidade do JWT, Material da rota ou predicado PUBLICADO. DTOs nao expoem entidades JPA, nomes, email, senha/hash ou caminhos de arquivos.

Resposta de cadastro ou `/minha` (UUIDs abaixo sao placeholders):

```json
{
  "id": "<UUID da avaliacao>",
  "nota": 4.75,
  "dataCadastro": "2026-10-21T15:00:00Z",
  "usuarioId": "<UUID do avaliador autenticado>",
  "materialId": "<UUID do material>"
}
```

Resumo:

```json
{
  "media": 4.01,
  "quantidade": 2
}
```

Sem notas: `{"media":null,"quantidade":0}`. Uma nota0,00 retorna media0,00 e quantidade1. Consulta `/minha` sem nota retorna404, nao um DTO vazio. Nao ha GET da colecao inteira nem GET por UUID de avaliacao; Location do cadastro aponta para a consulta realmente implementada, relativa ao usuario autenticado.

O resumo nao garante anonimato estatistico: quando ha uma unica avaliacao, a media permite inferir essa nota. A restricao e nao expor registros individuais/identidades de outros avaliadores, nao ocultar matematicamente valores que compoem um agregado pequeno.

## Precisao e calculo

Mantidas as configuracoes existentes: BigDecimal, PostgreSQL numeric(3,2), Bean Validation de intervalo/casas e CHECK entre0e5; constraint unica(usuario_id,material_id). DTO acrescenta @NotNull para rejeitar nota ausente antes de persistir.

Cadastro normaliza com setScale(2, UNNECESSARY) depois da validacao MVC: nunca usa HALF_UP para corrigir nota invalida. O banco numeric pode arredondar valores enviados diretamente por SQL; a rejeicao explicita de mais de duas casas e garantida pelo contrato validado da API, nao por alegacao de que numeric rejeita toda escala excedente.

Resumo usa projecao Spring Data JPA com SUM(BigDecimal) e COUNT na mesma consulta, com materialId e statusPUBLICADO fixos. Nao usa AVG convertido em double, nem carrega todas as entidades para somar em memoria. Service divide soma por quantidade com BigDecimal, escala2 e HALF_UP **somente na media apresentada**: notas4,00e4,01 geram media4,01; soma10,00/3 gera3,33. Nenhuma media/quantidade e armazenada ou incrementada manualmente em Material, evitando valores derivados desatualizados.

As consultas nao excluem/recodificam registros anteriores, nem aplicam uma limpeza retroativa de possiveis autoavaliacoes feitas diretamente no banco. A proibicao de autoavaliacao e regra do novo cadastro HTTP/service; SQL externo pode ignora-la. Nao ha populador de avaliacoes.

## Arquitetura e seguranca

Controller usa RequestBody/Valid/consumes e binding UUID nativos do Spring Web. Service usa @PreAuthorize(isAuthenticated()), @Transactional(readOnly=true) nas consultas e @Transactional no cadastro. As rotas utilizam a regra authenticated ja existente da SecurityFilterChain; nao exigem perfil administrativo e nao acrescentam filtro JWT/mecanismo de autenticacao proprio.

Cadastro: identifica usuario persistido, adquire PESSIMISTIC_WRITE no Material, confere PUBLICADO e autoria, verifica duplicidade e insere/flush na mesma transacao. Reutiliza o lock das mutacoes de Material para coordenar bloqueio/remocao/edicao e duas avaliacoes concorrentes, mesmo antes de existir a linha da avaliacao. Constraint unica do banco e a garantia final de um registro por usuario/Material. Sem locks em memoria, SQL de upsert ou chaveadvisory nova.

Leituras checam existencia/PUBLICADO para404 e tambem colocam PUBLICADO nas consultas de nota e de soma/contagem, sem depender apenas do pre-check. Como nas demais leituras da API, nao ha snapshot/ETag: uma mudanca de estado entre instrucoes pode resultar em consulta propria404 ou resumo vazio, mas nao em devolver notas de Material privado por consulta sem predicado. Dados ja retornados nao sao revogados retroativamente.

Avaliar nao muda estado, titulo, descricao, disciplina, categoria, views, autor ou mecanismos do Material. Sem novas entidades/tabelas/colunas/indices/dependencias/variaveis/Flyway. DTOs de requisicao e avaliacao individual redigem nota em toString; nao registrar corpos, dados pessoais, SQL com valores, credenciais ou tokens. Autenticacao conserva a limitacao de token curto sem revogacao imediata.

## Arquivos completos no repositorio

Caminhos relativos a raiz do backend:

```text
src/main/java/br/com/murkyweb/unesc_share/interacao/avaliacao/
  controller/AvaliacaoController.java        [novo]
  dto/AvaliacaoRequest.java                  [novo]
  dto/AvaliacaoResponse.java                 [novo]
  dto/AvaliacaoResumoResponse.java           [novo]
  repository/AvaliacaoRepository.java        [alterado]
  repository/AvaliacaoTotais.java            [novo]
  service/AvaliacaoService.java              [novo]
src/test/java/br/com/murkyweb/unesc_share/
  interacao/avaliacao/AvaliacaoServiceTests.java [novo]
  material/AvaliacaoApiTests.java               [novo]
  material/AvaliacaoTransacaoTests.java          [novo]
```

Entidade existente em `interacao/avaliacao/domain/Avaliacao.java` nao foi alterada. Seu metodo atualizarNota e preexistente e nao foi exposto por nenhuma rota nova.

## Erros

- 400: nota ausente/nula/invalida/fora do intervalo/com casas excedentes, UUID/JSON invalidos.
- 401: JWT ausente/invalido/expirado ou usuario do subject ausente no cadastro.
- 403: tentativa de avaliar o proprio Material publicado, qualquer perfil.
- 404: Material inexistente/nao publicado; `/minha` sem avaliacao propria; rota individual porUUID inexistente.
- 405: GET da colecao ou PUT/DELETE genericos da colecao/resumo; escritas proprias somente em `/minha`, sem listagem geral.
- 409: avaliacao duplicada ou conflito de integridade ao persistir.
- 415: POST com tipo de corpo nao JSON.

Validacao pode ocorrer antes de consultar o Material. ProblemDetail/ResponseEntityExceptionHandler e handlers Bearer nativos sao reutilizados. Falha de integridade e traduzida para mensagem fixa sem causaSQL/dados rejeitados.

## Executar e testar

Com PostgreSQL local disponivel e variaveis do .env exportadas sem exibir seus valores:

```bash
./mvnw -Dtest=AvaliacaoApiTests,AvaliacaoServiceTests,AvaliacaoTransacaoTests test
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Manter bootstrap administrativo desativado. Nao e necessario executar SQL de limpeza ou recriar o banco. O agente pode executar Maven; Docker permanece exclusivo do usuario.

Teste manual em cliente HTTP: selecione Material PUBLICADO de outro usuario, configure Bearer desse avaliador, envie POST com o JSON acima e consulte `/minha` e `/resumo`. Repetir POST retorna409. Use o dono para confirmar403, nao publicado para404 e nota3.755 para400. Um POST manual bem-sucedido cria nota real; o segundo incremento permite excluir fisicamente a propria nota, sem restauracao pela API. Prefira fixtures automatizadas descartaveis, sem dados reais para testes.

Cobertura: perfis/JWT, limites0e5, normalizacao de escala sem arredondamento, trescasas/null/campos extras, autoria/rota, duplicidade sem alteracao, privacidade, media/zero/ausencia, predicados no banco, estados do Material, erros/tipos e protecao do service fora do controller.

Dois testes usam fixtures sinteticas temporariamente confirmadas e requisicoes sem transacao externa. Um insere/flush real, confere existencia no banco, provoca falha e verifica ausencia em nova transacao. Outro dispara duas requisicoes MockMvc em threads simultaneas com JWT real, mesmo usuario/Material e transacoes independentes: espera201+409 e uma unica nota. Esse teste nao e trafego de rede/socket nem benchmark; nao prova que uma thread esperou um tempo especifico no lock.

Limpeza emfinally, em transacao propria, somente por UUIDs das fixtures geradas pelo teste: notas/Magnet do Material sintetico e os registros sinteticos Material/Disciplina/Categoria/Curso/Instituicao/usuarios. Sem deleteAll/cascatas ou catalogo real. Os demais testes de API usam rollback. Nao ha acesso a conteudo BitTorrent ou comandos Docker.

## Proximos incrementos

Edicao e exclusao fisica foram aprovadas e implementadas no [segundo incremento](AVALIACOES_EDICAO_REMOCAO.md), incluindo reavaliacao com novo registro. Cadastro/consultas proprias de denuncias implementados em [DENUNCIAS.md](DENUNCIAS.md); proxima entrega e definir sua moderacao/auditoria. Sem moderacao de notas, quotas/rate limiting, auditoria global/retencao ou ranking por media neste incremento.

## Verificacao executada

Em21/10/2026, os31testes especificos passaram e `./mvnw clean verify` concluiu com **460 testes**, zero falhas/erros/ignorados e JAR executavel gerado. Novos:19API,10unitarios,1rollback real aposINSERT e1concorrente. Duas requisicoesMockMvc simultaneas, com JWTs exclusivos do perfiltest e transacoes independentes, resultaram201+409/umaunica nota; teste de rollback comprovou insercao antes da falha e ausencia em nova transacao. Fixtures temporarias confirmadas foram limpas porUUIDs proprios emfinally; catalogo/dados reais preservados.

Backend anterior encerrado e atualizado iniciado em localhost:8080, context-path `/api/v1`, com bootstrap administrativo desativado e zero novas disciplinas na carga. POSTavaliacao/GETminha/GETresumo semJWT retornaram401 emHTTP real, sem gravacoes. JAR inclui novas classes de producao e nao inclui classes/configuracao de teste; `git diff --check` passou. Nenhum comando Docker/acesso a conteudoBitTorrent ou alteracao do.envreal ocorreu. Nenhuma entidade/tabela foi modificada nesta entrega.
