# Comentarios — edicao e remocao pelo autor

## Regras aprovadas

O usuario aprovou este segundo incremento apos o cadastro/listagem. A autorizacao e sempre do autor do Comentario, nao do dono do Material. Um administrador/moderador pode agir sobre o proprio comentario, mas nao impersonar o autor de terceiros por estas rotas.

| Operacao | Condicao do autor | Resultado |
| --- | --- | --- |
| PUT | Comentario ATIVO e Material PUBLICADO | 200 com DTO; muda somente conteudo |
| PUT | Comentario OCULTO/REMOVIDO em Material PUBLICADO | 409, sem restaurar ou editar |
| PUT | Material PENDENTE/BLOQUEADO/REMOVIDO | 404, sem editar |
| DELETE | Qualquer estado de Comentario/Material | 204; Comentario fica REMOVIDO |
| DELETE repetido | Comentario proprio ja REMOVIDO | 204; nao regrava nem restaura |
| PUT/DELETE de terceiro | Qualquer perfil/estado | 404, sem alterar ou devolver texto |

Remocao e **logica**: UUID, conteudo, dataCadastro e referencias continuam no banco. Isso nao e apagamento fisico, anonimização ou implementacao de uma politica de retencao. Nenhum registro real e removido automaticamente. REMOVIDO deixa de aparecer na lista comum.

Editar mantem o mesmo UUID, autor, Material, dataCadastro e estado ATIVO; publicacao permanece imediata. Nao altera metadados/estado/mecanismos/visualizacoes do Material. Nao ha data de edicao, historico de revisoes ou auditoria de decisao neste incremento.

O autor pode remover mesmo se o comentario estiver OCULTO ou o Material nao estiver publicado, desde que conheca os UUIDs. DELETE nao cria uma consulta privada nem devolve o conteudo. GET da colecao continua exigindo Material PUBLICADO e mostrando apenas ATIVO, sem excecao para autor/equipe. Nao ha GET individual, restauracao, desbloqueio ou moderacao nova.

## API

Todos exigem Bearer JWT. Caminhos completos:

```text
PUT    /api/v1/materiais/{materialId}/comentarios/{comentarioId}
DELETE /api/v1/materiais/{materialId}/comentarios/{comentarioId}
```

PUT recebe application/json ou application/*+json:

```json
{
  "conteudo": "Texto corrigido do comentario."
}
```

Texto obrigatorio ate4000caracteres, strip de espacos externos e rejeicao de U+0000, como no cadastro. Campos extras id/usuarioId/materialId/status/dataCadastro nao alteram os respectivos campos. Texto e JSON simples, nao HTML a renderizar. Resposta usa ComentarioResponse existente, sem nome/email/senha/perfil ou entidades JPA.

DELETE nao precisa de corpo e devolve204 sem representacao ou texto, inclusive ao repetir. O comentario precisa pertencer ao Material da rota; usar outro Material retorna404 mesmo para o autor. Chamadas invalidas podem ser rejeitadas pela validacao nativa antes de verificar autoria.

GET individual nao foi adicionado: como a URI agora possui PUT/DELETE, GET nela retorna405 nativo, nao a representacao do comentario. PUT/DELETE na colecao sem comentarioId continuam sem suporte.

## Arquivos e arquitetura

Codigo completo no workspace, nos caminhos relativos a raiz:

```text
src/main/java/br/com/murkyweb/unesc_share/interacao/comentario/
├── controller/ComentarioController.java
├── repository/ComentarioRepository.java
└── service/ComentarioService.java

src/test/java/br/com/murkyweb/unesc_share/
├── interacao/comentario/ComentarioServiceTests.java
└── material/
    ├── ComentarioApiTests.java
    ├── ComentarioEdicaoRemocaoApiTests.java
    └── ComentarioMutacaoTransacaoTests.java
```

DTOs, entidade e enum existentes foram reutilizados. Nenhum campo/schema, dependencia, migration, variavel ou configuracao de seguranca nova. Controller usa PutMapping/DeleteMapping, RequestBody/Valid e ResponseEntity nativos; service continua protegido por PreAuthorize isAuthenticated, incluindo chamadas fora do controller.

Mutacoes sao transacionais e usam locks JPA PESSIMISTIC_WRITE, sempre na ordem **Material primeiro, Comentario depois**. O repository de Comentario filtra simultaneamente comentarioId e materialId. Depois da busca, o service compara o autor ao UUID do JWT ja validado pelo Resource Server.

Essa ordem coordena edicao/remocao com as mutacoes atuais do Material e serializa as alteracoes do Comentario. Fluxos futuros de moderacao devem respeitar a mesma ordem. Nao ha lock em memoria, filtro JWT proprio, mecanismo de autenticacao paralelo ou permissao administrativa implicita para editar terceiros.

PUT so modifica ATIVO; DELETE faz transicao para REMOVIDO e nunca a reverte. Ja removido nao executa saveAndFlush novamente. Nao sao chamados delete fisico ou cascatas nas operacoes da API. Erros DataIntegrityViolationException de cadastro/edicao/remocao passam pelo mesmo tratamento fixo409, sem expor causaSQL ou texto rejeitado.

Sem @Version/ETag: edicoes concorrentes sao serializadas, mas nao ha rejeicao por versao antiga; ultima edicao valida pode prevalecer. Remocao logica nao e historico completo de auditoria e mantem o texto no banco. Rate limiting, quotas, retencao, moderacao e revisao de seguranca de producao permanecem pendentes.

## Erros

- 400: texto/JSON/UUID invalidos.
- 401: JWT ausente/invalido/expirado.
- 404: Material/comentario inexistente, comentario fora do Material da rota, terceiro ou tentativa de edicao em Material nao publicado.
- 409: edicao de OCULTO/REMOVIDO em Material publicado ou conflito de integridade.
- 415: PUT com tipo HTTP nao JSON, como PDF direto.

ProblemDetail/ResponseEntityExceptionHandler e handlers Bearer permanecem nativos. Mensagens nao incluem conteudo privado, credenciais, tokens ou SQL. A regra de permitir DELETE em Material nao publicado nao amplia GET/listagem nem permite editar nessa situacao.

## Executar e testar

Com PostgreSQL disponivel, .env carregado silenciosamente e bootstrap administrativo desativado:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Exemplo manual com UUIDs conhecidos e token do autor (nao compartilhar tokens/senhas no chat ou Git):

```bash
curl --fail-with-body \
  -X PUT \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  --data '{"conteudo":"Texto corrigido."}' \
  "http://localhost:8080/api/v1/materiais/${MATERIAL_ID}/comentarios/${COMENTARIO_ID}"

curl --fail-with-body \
  -X DELETE \
  -H "Authorization: Bearer ${TOKEN}" \
  "http://localhost:8080/api/v1/materiais/${MATERIAL_ID}/comentarios/${COMENTARIO_ID}"
```

PUT/DELETE manuais alteram dados reais; DELETE retira da lista comum sem apagar o texto do banco e nao ha restauracao via API. Tokens na linha de comando podem aparecer na lista de processos; para uso real prefira cliente que proteja segredos.

Testes, com ambiente local carregado:

```bash
./mvnw -Dtest=ComentarioEdicaoRemocaoApiTests,ComentarioApiTests,ComentarioServiceTests,ComentarioMutacaoTransacaoTests test
./mvnw clean verify
```

API usa PostgreSQL e JWT nativos com fixtures e rollback: cobre os tres perfis, cruzamento de todos os estados do Material/Comentario, tentativas do dono/equipe/terceiros, limite do texto/campos extras, vinculo de rota, ausencia de vazamento de texto, idempotencia204 e impossibilidade de reativar REMOVIDO por edicao. Testes unitarios verificam ordem de locks, ausencia de gravacao de ja removido e bloqueio de alteracao por terceiros. Sem teste E2E de duas requisicoes concorrentes; nao alegar controle de versao otimista.

Dois testes de transacao usam **fixtures temporariamente confirmadas**, para observar a restauracao real apos rollback da requisicao: dao UPDATE/flush em texto ou status, conferem a alteracao no banco, injetam falha e verificam, em transacao independente, que texto/estado/datas/identificadores originais foram restaurados. Nao dependem apenas de rollback externo do proprio teste.

A limpeza desses dois cenarios ocorre em finally, em transacao propria, por UUIDs exatos criados pelo teste: Comentario, Magnet, Material, Disciplina, Categoria, Curso, Instituicao e Usuario sinteticos. Nao usa deleteAll, cascatas ou registros reais; verifica ausencia apos limpeza. Essas fixtures descartaveis podem ser recriadas pelos testes. Os demais testes de API continuam revertendo suas fixtures. Nao ha escrita/leitura de arquivos ou conteudo BitTorrent, nem comandos Docker.

A moderacao foi acrescentada no [terceiro incremento](COMENTARIOS_MODERACAO.md): ocultacao/reexibicao e auditoria minima restritas a equipe, sem restauracao de REMOVIDO e sem alterar as permissoes do autor acima. A API de avaliacoes foi implementada em [AVALIACOES.md](AVALIACOES.md), com edicao/exclusao propria no [segundo incremento](AVALIACOES_EDICAO_REMOCAO.md). Proxima entrega: definir denuncias. Edicao/remocao de mecanismos Torrent/Magnet e auditoria global continuam separadas.

## Verificacao executada

Em21/10/2026, `./mvnw clean verify` passou com **401 testes**, zero falhas/erros/ignorados e JAR executavel gerado. Sao19novos:14de API,3unitarios e2de rollback real apos UPDATE. Os conjuntos selecionados tambem passaram. Os dois cenarios confirmaram restauracao do registro original e limpeza das fixtures temporarias; dados reais do catalogo nao foram removidos.

Backend atualizado reiniciado em localhost:8080, context-path `/api/v1`, bootstrap administrativo desativado e carga criando zero novas disciplinas. PUT/DELETE sem JWT retornaram401, sem alteracao de registros. `git diff --check` passou e o JAR inclui codigo de producao, sem classes/configuracao de testes. Nenhum comando Docker foi executado, nenhum conteudo BitTorrent foi acessado e o `.env` real permaneceu inalterado pelo agente.
