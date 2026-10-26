# Edicao e exclusao fisica da propria avaliacao

## Decisao aprovada

O usuario aprovou as regras propostas e a exclusao fisica apenas da propria Avaliacao. Este segundo incremento nao transforma a remocao logica de Material/Comentario em remocao fisica e nao adiciona exclusao de outras entidades.

| Operacao | Condicoes | Resultado |
|---|---|---|
| PUT propria nota | Autenticado, Material PUBLICADO de outro usuario, propria Avaliacao existente | 200, mesma avaliacao com nota nova |
| DELETE propria nota | Autenticado, Material existente em qualquer estado | 204, exclui somente a propria Avaliacao, se existir |

Sem excecao administrativa: equipe/dono do Material nao podem escolher a nota de terceiro. Identidade vem do JWT, nao de usuarioId/id/materialId no JSON ou query string. Nao ha rotas de PUT/DELETE por UUID da avaliacao ou escrita generica na colecao.

Edicao mantem UUID, autor, Material e dataCadastro, alterando somente a nota. As validacoes do cadastro permanecem: BigDecimal entre0,00e5,00, ate duas casas, nota obrigatoria, sem arredondar valores invalidos. Valores4/4.5 normalizados para4.00/4.50. PUT nao e upsert: propria nota ausente404. Uma autoavaliacao legada inserida diretamente no banco nao pode ser editada (403), mas seu autor pode retira-la.

DELETE remove fisicamente a linha de Avaliacao. Nao preserva historico/restauracao pela aplicacao e nao pode ser desfeito pela API. Repetir em Material existente retorna204, sem regravar/excluir mais nada se a propria nota estiver ausente. Material inexistente404. Nao usa deleteAll, exclusao por usuario informado pelo cliente, SQL de limpeza ou cascata.

Apos remover, e possivel cadastrar outra nota por POST quando o Material estiver PUBLICADO e pertencer a outro usuario. E uma nova Avaliacao com novo UUID e dataCadastro, nao restauracao da anterior. A constraint unica(usuario_id,material_id) continua valida.

## HTTP

Rotas completas:

```text
PUT    /api/v1/materiais/{materialId}/avaliacoes/minha
DELETE /api/v1/materiais/{materialId}/avaliacoes/minha
```

PUT aceita application/json ou application/*+json. Corpo completo, reutilizando AvaliacaoRequest:

```json
{
  "nota": 4.50
}
```

200 devolve AvaliacaoResponse com id/nota/dataCadastro/usuarioId/materialId, sem dados pessoais/entidades JPA. Nao gera UUID/data novos e nao retorna Location de cadastro.

DELETE nao exige corpo; 204 tem corpo vazio, sem nota ou dados privados. Se um terceiro tentar selecionar outro usuario por parametros/corpo, eles sao ignorados: sera removida somente a nota do proprio subject, se houver. Se nao houver propria nota, retorna204 e preserva a nota alheia. Em contraste, PUT sem propria nota retorna404; dono do Material publicado recebe403 pela regra contra autoavaliacao.

As consultas GET/minha e GET/resumo permanecem limitadas a Material PUBLICADO, tambem para dono/equipe. Poder retirar a propria nota de Material privado nao cria permissao para ler suas notas/resumo.

Media/quantidade sao recalculadas via SUM/COUNT existentes. Edicao altera media, nao quantidade. Exclusao retira a nota da soma/contagem; sem notas restantes, media=null/quantidade0. Nenhum contador ou media e persistido em Material.

## Transacoes e seguranca

Reutiliza Spring Security @PreAuthorize(isAuthenticated()), SecurityFilterChain authenticated, Spring Web RequestBody/Valid/UUID e ProblemDetail nativos. Nao ha filtros JWT proprios ou permissao administrativa implicita. Metodos publicos de mutacao usam @Transactional, enquanto consultas permanecem readOnly.

Ordem fixa de locks JPA PESSIMISTIC_WRITE: **Material -> Avaliacao**. Busca da Avaliacao exige simultaneamente usuarioId do JWT e materialId da rota. O lock do Material tambem e compartilhado com cadastro, edicao/remocao/publicacao/bloqueio de Material e demais mutacoes existentes; protege o par mesmo na ausencia de linha de avaliacao.

PUT confere PUBLICADO e autoria do Material depois do lock, busca a propria avaliacao bloqueada, atualiza a nota e saveAndFlush. DELETE bloqueia Material sem exigir PUBLICADO, busca propria nota bloqueada, chama delete(entity) e flush na mesma transacao. Falha de integridade reverte a mutacao e retorna409 com mensagem fixa sem causa SQL. As duas rotas nao consultam nota de terceiros.

Material/Usuario/Disciplina/Curso/Instituicao/Categoria/Torrent/Magnet/Comentario nao sao excluidos ou modificados. Nenhuma cascata nova, entidade/coluna/estado/historico/schema/dependencia/variavel/Flyway foi acrescentada. Nao executar limpeza em massa ou remocao de avaliacoes reais para disponibilizar esta funcionalidade.

Sem @Version/ETag ou historico de notas. Locks coordenam transacoes, mas nao rejeitam toda edicao feita sobre uma tela desatualizada; ultima edicao valida pode prevalecer. DELETE/minha sempre significa retirar a nota atual do usuario: se ele criar outra nota entre duas tentativas de DELETE, a nova tentativa pode remover o novo registro. Nao ha protecao de geracao/requisicao idempotente alem das regras descritas. Retencao/auditoria futura exigira nova decisao; nenhuma recuperacao de notas apagadas foi implementada.

## Arquivos completos no repositorio

Caminhos relativos a raiz:

```text
src/main/java/br/com/murkyweb/unesc_share/interacao/avaliacao/
  controller/AvaliacaoController.java                [alterado]
  repository/AvaliacaoRepository.java                [alterado]
  service/AvaliacaoService.java                      [alterado]
src/test/java/br/com/murkyweb/unesc_share/
  material/AvaliacaoEdicaoRemocaoApiTests.java        [novo]
  material/AvaliacaoApiTests.java                    [alterado]
  material/AvaliacaoTransacaoTests.java               [alterado]
  interacao/avaliacao/AvaliacaoServiceTests.java      [alterado]
```

Reutilizados sem alteracao: entidade Avaliacao, AvaliacaoRequest/Response/ResumoResponse, projecao de totais, estrutura de JWT, SecurityConfig e tratamento de erros. Documentacao inicial e contrato de cadastro/consultas em [AVALIACOES.md](AVALIACOES.md).

## Erros

- 400: nota/JSON/UUID invalidos; nota nula/ausente, fora do intervalo ou casas excedentes no PUT.
- 401: JWT ausente/invalido/expirado.
- 403: editar nota do proprio Material publicado, inclusive autoavaliacao legada.
- 404: Material inexistente; PUT em Material nao publicado ou sem avaliacao propria.
- 409: conflito de integridade ao salvar/remover, sem detalhes SQL.
- 415: PUT com corpo nao JSON.

DELETE de propria nota ausente em Material existente retorna204, nao404, inclusive se houver apenas nota de terceiro. Validacao pode ocorrer antes das consultas, como no resto da API. PUT/DELETE genericos da colecao e do resumo continuam405; nao ha operacao por UUID da avaliacao.

## Executar e testar

Com PostgreSQL local disponivel, .env exportado sem exibir segredos e bootstrap administrativo desativado:

```bash
./mvnw -Dtest=AvaliacaoApiTests,AvaliacaoEdicaoRemocaoApiTests,AvaliacaoServiceTests,AvaliacaoTransacaoTests test
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Teste manual em cliente HTTP: com Bearer do avaliador e uma propria nota existente em Material PUBLICADO, envie PUT com o JSON acima. Consulte GET/minha e GET/resumo; depois envie DELETE sem corpo e confira204 e GET/minha404. Repetir DELETE retorna204. Outro POST valido cria uma nova nota. DELETE manual bem-sucedido apaga definitivamente a propria avaliacao, sem restauracao pela API; nao use notas reais para testes descartaveis.

Testes novos cobrem perfis, preservacao de UUID/data/autor, validacao sem arredondamento, campos/parametros forjados, estados de Material, ausencia/idempotencia, nota de terceiros, alteracao de media/quantidade, reavaliacao com nova identidade e preservacao de registros vinculados. Torrents usados nessa verificacao sao somente metadados sinteticos em transacoes revertidas, sem criar/baixar arquivo.

Dois novos testes de rollback usam fixtures temporariamente confirmadas, requisicao sem transacao externa, UPDATE/DELETE com flush real e verificacao SQL antes de injetar falha. Leitura em nova transacao confirma nota/UUID/data e totais originais restaurados. Dois novos testes de concorrencia disparam requisicoes MockMvc em threads com JWT/transacoes independentes: DELETE+DELETE retornam204/204; PUT+DELETE retorna200ou404 no PUT,204 no DELETE, e sempre ausencia final sem recriar a nota. Nao sao trafego de rede real nem prova de tempo especifico de espera no lock.

Esses cenarios reutilizam as fixtures sinteticas/limpeza exata emfinally do teste de transacao: apenas notas/Magnet do Material sintetico e seus registros academicos/usuarios sinteticos gerados pelo teste, sem deleteAll/cascatas ou registros reais. Demais casos de API usam rollback. Nenhum Docker e executado pelo agente.

## Proxima entrega

Cadastro/consultas proprias de denuncias implementados posteriormente em [DENUNCIAS.md](DENUNCIAS.md); proxima entrega e definir sua moderacao/auditoria. Auditoria global, historico de notas, rate limiting e protecao de concorrencia otimista permanecem fora deste incremento. A premissa de catalogo BitTorrent nao foi alterada.

## Verificacao executada

Em21/10/2026, o conjunto de53testes de avaliacoes passou e `./mvnw clean verify` concluiu com **482 testes**, zero falhas/erros/ignorados e JAR executavel gerado. Sao22novos:13API,5unitarios,2rollback real e2concorrentes. A primeira compilacao dos testes identificou uma sobrecarga ambigua de Mockito em verificacao de nao exclusao de Material; foi corrigida com tipo explicito, sem alterar comportamento de producao.

UPDATE/DELETE reais foram conferidos antes das falhas simuladas e a restauracao de nota/UUID/data/totais foi verificada em nova transacao. DELETE+DELETE resultou204/204; PUT+DELETE terminou sem avaliacao, sem recriar a linha. Fixtures sinteticas confirmadas foram limpas porUUIDs proprios emfinally; nenhuma avaliacao real ou registro real do catalogo foi removido. Nao foram apagados arquivos .torrent ou conteudos academicos.

Backend anterior encerrado e atualizado iniciado em localhost:8080, context-path `/api/v1`, com bootstrap administrativo desativado e zero novas disciplinas na carga. PUT/DELETE novos semJWT retornaram401 emHTTP real, sem alterar registros. Acoes autenticadas foram verificadas comJWTs exclusivos de teste, sem credenciais reais. `git diff --check` passou e JAR inclui producao, sem classes/configuracao de teste. Nenhum comandoDocker/acesso a conteudoBitTorrent ou alteracao do.envreal ocorreu; nenhuma entidade/schema foi alterada.
