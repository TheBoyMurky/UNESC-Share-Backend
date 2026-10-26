# Denuncias: cadastro e consultas proprias

## Decisao aprovada

Primeiro incremento da API de denuncias. O usuario aprovou cadastro/consulta das proprias denuncias e uma unica denuncia aberta por usuario/Material, independentemente do motivo. Moderacao/auditoria foram acrescentadas separadamente no [segundo incremento](DENUNCIAS_MODERACAO.md); as rotas proprias abaixo continuam sem transicoes HTTP.

- Qualquer autenticado pode denunciar Material PUBLICADO, inclusive seu proprio Material e independentemente do perfil.
- Denunciante vem exclusivamente do subject JWT e precisa existir no banco no cadastro.
- Motivo obrigatorio: DIREITOS_AUTORAIS, CONTEUDO_INADEQUADO, LINK_INVALIDO, SPAM ou OUTRO, conforme enum existente.
- Descricao obrigatoria para todos os motivos, nao vazia, strip, ate2000caracteres apos strip, sem U+0000.
- Estado inicial PENDENTE, nunca escolhido pelo cliente.
- Se existir PENDENTE ou EM_ANALISE do mesmo usuario/Material, novo POST retorna409, mesmo com motivo diferente. Nao edita nem substitui o relato anterior.
- PROCEDENTE/IMPROCEDENTE sao estados concluidos: nao impedem novo relato PENDENTE, com novo UUID/data. Estado anterior e registro sao preservados.
- Usuarios diferentes podem denunciar o mesmo Material; o mesmo usuario pode denunciar materiais diferentes.
- Denunciar nao bloqueia, publica, remove ou muda metadados/mecanismos/visualizacoes do Material.
- Consulta somente do proprio denunciante, sem excecao para dono/equipe. Nao ha lista publica, contagem de denuncias no Material ou consulta administrativa nesta entrega.
- Relatos proprios continuam consultaveis quando Material fica PENDENTE/BLOQUEADO/REMOVIDO. Isso nao libera detalhes privados do Material.
- Sem edicao, exclusao, cancelamento, anexos ou notificacoes. Transicoes de moderacao somente no namespace da equipe, conforme segundo incremento, sem ampliar as permissoes das rotas proprias.

Nao ha fluxo de comprovacao de autoria/licenca ou decisao juridica automatica. Motivos sao classificacoes do relato, nao conclusoes sobre o conteudo. Nao enviar arquivos academicos ou provas/anexos nesta API.

## Contrato HTTP

| Metodo | Rota completa | Resultado |
|---|---|---|
| POST | `/api/v1/materiais/{materialId}/denuncias` | 201, DenunciaResponse e Location da consulta individual propria |
| GET | `/api/v1/denuncias/minhas?page=0&size=20` | 200, PagedModel das proprias denuncias |
| GET | `/api/v1/denuncias/{denunciaId}` | 200, DenunciaResponse propria; terceiro/inexistente404 |

POST aceita application/json e application/*+json. Corpo completo:

```json
{
  "motivo": "LINK_INVALIDO",
  "descricao": "O mecanismo cadastrado apresenta o seguinte problema: ..."
}
```

Campos extras usuarioId/materialId/status/id/dataCadastro nao escolhem autor, rota, estado ou identificadores. Parametros usuarioId/materialId/status/sort nos GETs nao substituem identidade, filtragem ou ordenacao. Identidade nao e aceita por parametro, nem mesmo para administradores.

DenunciaResponse tem exatamente seis campos, sem usuarioId, nome, email, senha/hash, entidades JPA ou dados adicionais do Material. Exemplo (UUIDs sao placeholders):

```json
{
  "id": "<UUID da denuncia>",
  "motivo": "LINK_INVALIDO",
  "descricao": "O mecanismo cadastrado apresenta o seguinte problema: ...",
  "status": "PENDENTE",
  "dataCadastro": "2026-10-21T18:00:00Z",
  "materialId": "<UUID do material>"
}
```

Location aponta para `/api/v1/denuncias/{denunciaId}`, respeitando o context-path pelo ServletUriComponentsBuilder nativo, sem duplicar `/api/v1` no controller. Consultar essa URL exige o mesmo denunciante, nao apenas conhecer o UUID.

Listagem aplica usuarioId do JWT tanto na consulta quanto na contagem. Mostra todos os estados da propria denuncia e qualquer estado do Material. Page>=0, size1..100, padrao20, ordem fixa dataCadastro/id ASC. Sem registros proprios retorna pagina vazia. Nao ha filtros opcionais de status/material neste incremento.

Descricao e texto simples em JSON. Tags literais sao preservadas como texto, nao HTML seguro ou Markdown habilitado. Frontend deve usar interpolacao/v-text e nunca v-html/innerHTML com relatos nao confiaveis. Nao registrar corpos/descricoes, tokens, credenciais ou valores SQL. toString dos DTOs redige descricao; erros nao ecoam descricao rejeitada ou causas SQL.

## Arquitetura e integridade

Reutiliza Spring Security Resource Server JWT, regra authenticated da SecurityFilterChain e @PreAuthorize(isAuthenticated()) no service. Sem filtros JWT proprios, mecanismos de autenticacao paralelos ou permissao administrativa implicita para relatos alheios. Controller usa RequestBody/Valid/UUID/paginacao e Bean Validation nativos do Spring Web.

DenunciaService usa @Transactional(readOnly=true) para consultas e @Transactional no cadastro. Primeiro resolve usuario persistido; depois adquire PESSIMISTIC_WRITE no Material, confere PUBLICADO, verifica existencia de denuncia aberta no par usuario/Material e insere/flush PENDENTE na mesma transacao. Essa ordem serializa cadastros mesmo antes de existir linha de Denuncia e coordena bloqueio/remocao/edicao concorrentes do Material.

A regra de uma aberta e coordenada pelo **protocolo transacional da API**, com lock do Material compartilhado entre conexoes/instancias e consulta de estados abertos em READ_COMMITTED. Nao foi criada constraint unica global(usuario,material), pois impediria relatos posteriores apos conclusao. Tambem nao foi criado indice unico parcial PostgreSQL/migracao/trigger; escritas SQL externas que ignorem o protocolo podem inserir duplicatas. Nao alegar garantia de unicidade independente da aplicacao. Futuras mutacoes/moderacao devem respeitar Material->Denuncia e conferir as mesmas regras; nao executar limpeza retroativa automatica.

No GET individual, o repository exige denunciaId+usuarioId na mesma consulta. No GET/minhas, o filtro do usuario tambem integra a contagem. Nao reutiliza permissao de detalhes privados de Material para liberar relatos de terceiros. DTO retorna somente materialId: nao titulo, descricao, autor, estado, Magnet URI ou caminho de Torrent do Material privado.

O metodo alterarStatus e os enums existentes permanecem no dominio, mas nao foram expostos pela API. Estados EM_ANALISE/PROCEDENTE/IMPROCEDENTE nos testes sao fixtures sinteticas criadas diretamente para testar regras de leitura/duplicidade, nao evidencia de moderacao implementada.

Nenhuma entidade/schema/coluna/indice/dependencia/variavel/Flyway nova. Tabela/UUID/relacionamentosLAZY/enumsSTRING/InstantUTC existentes foram preservados, sem cascatas adicionais. Nao criar usuarios administrativos, limpar registros reais ou recriar o banco para esta entrega. Sem download de conteudo, consulta de trackers, upload de PDF/EPUB/MP4/ZIP ou anexos.

## Arquivos completos no repositorio

Caminhos relativos a raiz do backend:

```text
src/main/java/br/com/murkyweb/unesc_share/interacao/denuncia/
  controller/DenunciaController.java           [novo]
  dto/DenunciaRequest.java                     [novo]
  dto/DenunciaResponse.java                    [novo]
  repository/DenunciaRepository.java           [alterado]
  service/DenunciaService.java                 [novo]
src/test/java/br/com/murkyweb/unesc_share/
  interacao/denuncia/DenunciaServiceTests.java  [novo]
  material/DenunciaApiTests.java               [novo]
  material/DenunciaTransacaoTests.java         [novo]
```

Entidade Denuncia, MotivoDenuncia, StatusDenuncia, SecurityConfig, configuracoes e ApiExceptionHandler reutilizados sem alteracao.

## Erros

- 400: motivo ausente/invalido, descricao ausente/branca/>2000/comU+0000, JSON/UUID/paginacao invalidos.
- 401: JWT ausente/invalido/expirado ou usuario do subject ausente no cadastro.
- 404: Material inexistente/nao publicado no POST; denuncia inexistente/alheia no GET; rotas nao implementadas.
- 405: escrita em rotas somente de consulta, GET na colecao de cadastro por Material; nao ha PUT/DELETE/PATCH/cancelamento.
- 409: ja existe denuncia aberta do mesmo usuario/Material ou conflito de integridade ao salvar.
- 415: POST nao JSON, inclusive arquivo academico.

Validacao pode ocorrer antes das consultas de Material. ProblemDetail/ResponseEntityExceptionHandler e handlers Bearer nativos sao reutilizados, com mensagens fixas de integridade sem causa SQL ou descricao privada. Material nao publicado retorna404 no cadastro tambem para dono/equipe, nao uma permissao especial.

## Executar e testar

Com PostgreSQL local disponivel, variaveis do .env exportadas sem exibir valores e bootstrap administrativo desativado:

```bash
./mvnw -Dtest=DenunciaApiTests,DenunciaServiceTests,DenunciaTransacaoTests test
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Agente pode executar Maven; Docker continua exclusivo do usuario. Nenhum SQL manual de limpeza e necessario.

Teste manual em cliente HTTP: configure Bearer, escolha Material PUBLICADO e envie POST com motivo/descricao. Consulte Location e GET/minhas. Repetir POST, mesmo com outro motivo, retorna409. Com outro usuario, o GET individual retorna404 e GET/minhas nao inclui o relato. Um cadastro manual bem-sucedido cria relato real, sem exclusao/cancelamento HTTP nesta entrega: prefira fixtures descartaveis e nao inclua dados pessoais desnecessarios na descricao.

Os testes cobrem todos os perfis/motivos, denuncia do proprio Material, estado inicial/identidade fixos, descricao/limites/campos extras, estados abertos/concluidos, independencia de pares, estados do Material, consultas proprias/privacidade, paginacao/ordenacao, erros e ausencia de endpoints de edicao/moderacao. Testes unitarios verificam ordem do lock, estados abertos fixos, filtragem por usuario e mensagens sem dados privados.

Dois testes usam fixtures sinteticas temporariamente confirmadas, sem transacao externa na requisicao. Um confirma INSERT/flush por SQL antes de falha simulada e verifica ausencia em nova transacao, preservando Material/Magnet. Outro dispara duas requisicoes MockMvc simultaneas do mesmo usuario/Material com motivos diferentes, JWTs reais exclusivos de teste e transacoes independentes: espera201+409 e uma unica denunciaPENDENTE. Nao sao trafego de rede/socket nem benchmark ou prova de espera por tempo especifico no lock.

Limpeza emfinally, em transacao propria, apenas porUUIDs das fixtures: denuncias/Magnet do Material sintetico e seus registros sinteticos Material/Disciplina/Categoria/Curso/Instituicao/usuarios. Sem deleteAll/cascatas ou dados reais. Demais testes de API usam rollback. As fixtures sao descartaveis e recriaveis pelos testes.

## Limites e proxima entrega

Fluxo de moderacao, responsavel/justificativa e historico minimo foram acrescentados no [segundo incremento](DENUNCIAS_MODERACAO.md), com conflitos de interesse e sem bloqueio automatico. Notificacoes, anexos, quotas/rate limiting, cancelamento e retencao continuam pendentes. Uma aberta por par nao substitui protecao contra abuso distribuido. Autenticacao conserva token curto sem revogacao imediata. Nao apresentar o prototipo como pronto para publicacao irrestrita.

Proximo passo recomendado: revisar permissoes/contratos HTTP e planejar integracao com frontend separado. Nenhuma denuncia bloqueia automaticamente um Material; o segundo incremento tambem mantem essa separacao.

## Verificacao executada

Em21/10/2026, os29testes especificos passaram e `./mvnw clean verify` concluiu com **511 testes**, zero falhas/erros/ignorados e JAR executavel gerado. Novos:19API,8unitarios,1rollback real aposINSERT e1concorrente. Dois POSTs simultaneos do mesmo usuario/Material, com motivos diferentes, resultaram201+409 e uma unica denunciaPENDENTE. O rollback confirmou INSERT no banco antes da falha e ausencia em nova transacao. Fixtures sinteticas temporarias foram limpas porUUIDs proprios emfinally, preservando dados reais do catalogo e usuarios reais.

Backend anterior encerrado e atualizado iniciado em localhost:8080, context-path `/api/v1`, bootstrap administrativo desativado e zero novas disciplinas criadas. POSTdenuncia/GETminhas/GETdetalhe semJWT retornaram401 emHTTP real, sem gravacoes. Acoes autenticadas foram verificadas nos testes com JWTs exclusivos do perfiltest, sem credenciais reais. `git diff --check` passou; JAR contem novas classes de producao, sem classes/configuracao de teste. Nenhum comandoDocker/acesso a conteudoBitTorrent/alteracao do.envreal ocorreu; nenhuma entidade/schema foi alterada nesta entrega.
