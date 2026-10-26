# Moderacao de denuncias com auditoria minima

## Regras aprovadas

Segundo incremento de denuncias, aprovado pelo usuario: equipe pode analisar e concluir, com justificativa e historico na mesma transacao, sem bloqueio automatico de Material e sem decidir sobre denuncia propria/proprio Material. Cadastro e consultas do denunciante continuam em [DENUNCIAS.md](DENUNCIAS.md).

| Acao | Estado exigido | Novo estado |
|---|---|---|
| Iniciar analise | PENDENTE | EM_ANALISE |
| Concluir procedente | EM_ANALISE | PROCEDENTE |
| Concluir improcedente | EM_ANALISE | IMPROCEDENTE |

Somente MODERADOR/ADMINISTRADOR nas novas rotas. Pular analise, repetir transicao, reabrir concluida ou substituir decisao retorna409, sem evento duplicado. Denuncias concluidas permanecem registradas; um novo relato segue a regra existente de uma aberta por usuario/Material.

As tres acoes sao proibidas ao denunciante e ao autor do Material, inclusive ADMINISTRADOR (403). Outra pessoa elegivel da equipe precisa agir. A restricao se aplica as mutacoes: membros da equipe podem consultar a fila/detalhes/historico mesmo quando estiverem impedidos de decidir. Nao existe atribuicao exclusiva de analista; outro membro elegivel pode concluir uma analise iniciada por colega, com cada responsavel registrado no evento correspondente.

Denuncias podem ser analisadas/concluidas em qualquer estado do Material, inclusive REMOVIDO. Nenhuma transicao publica, bloqueia, remove, restaura ou edita Material. Procedencia indica o resultado da analise da denuncia, nao uma operacao automatica sobre o catalogo. Bloqueio permanece uma decisao separada pela rota existente do Material, com suas regras atuais; este historico nao audita aquela operacao.

Relato original, motivo, UUID, denunciante, Material e dataCadastro sao preservados. Sem edicao/exclusao/cancelamento de denuncia. Consultas proprias nao ganharam excecao administrativa: GET /denuncias/{id} continua somente do denunciante; equipe usa namespace separado.

## Rotas HTTP

Prefixo completo: `/api/v1/denuncias/moderacao`. Todas exigem Bearer de equipe.

| Metodo | Sufixo | Resultado |
|---|---|---|
| GET | `?status=PENDENTE&page=0&size=20` | 200, fila paginada por estado |
| GET | `/{denunciaId}` | 200, detalhes para equipe |
| GET | `/{denunciaId}/historico?page=0&size=20` | 200, historico interno paginado |
| POST | `/{denunciaId}/analise` | 200, DenunciaResponse com EM_ANALISE |
| POST | `/{denunciaId}/procedencia` | 200, DenunciaResponse com PROCEDENTE |
| POST | `/{denunciaId}/improcedencia` | 200, DenunciaResponse com IMPROCEDENTE |

Fila aceita status do enum existente; padrao PENDENTE. Nao e uma busca publica ou lista de todos os estados simultaneamente. Page>=0, size1..100 (padrao20), ordenacao fixa dataCadastro/id ASC em fila e historico. Parametro sort nao altera ordenacao; usuarioId/materialId nao filtram nem alteram permissoes. Fila sem resultados e historico de denuncia existente sem eventos retornam pagina vazia; denuncia inexistente404.

POST aceita application/json ou application/*+json. Corpo completo:

```json
{
  "justificativa": "Relato conferido; iniciada a analise do material indicado."
}
```

Justificativa obrigatoria, strip, ate1000caracteres apos strip, sem U+0000. Estados/acao/responsavel nao sao escolhidos pelo corpo: os endpoints fixam a transicao, estado anterior vem da denuncia bloqueada e responsavel do JWT deve existir no banco. Campos extras nao editam descricao/motivo/denunciante/material/id/data nem forjam o evento. POST nao e idempotente: repeticao409, sem novo evento. Nao ha Location de nova denuncia nessas mutacoes.

## DTOs e privacidade

Fila, detalhes e POSTs reutilizam DenunciaResponse com seis campos: id, motivo, descricao, status, dataCadastro, materialId. Nao expoem nome/email/UUID do denunciante, entidades JPA, titulo/descricao/estado privados do Material ou mecanismos. Identidades usadas para verificar conflito permanecem internas ao service.

Historico interno retorna sete campos, sem copiar relato original:

```json
{
  "id": "<UUID do evento>",
  "denunciaId": "<UUID da denuncia>",
  "responsavelId": "<UUID do membro da equipe>",
  "justificativa": "Relato conferido; iniciada a analise do material indicado.",
  "estadoAnterior": "PENDENTE",
  "estadoNovo": "EM_ANALISE",
  "dataCadastro": "2026-10-21T18:30:00Z"
}
```

UUIDs do exemplo sao placeholders. responsavelId identifica o agente da decisao, nao o denunciante. O cadastro e relato proprios continuam exibindo status atualizado, mas nao recebem campos internos de justificativa/responsavel/eventos. Denunciante sem perfil de equipe nao acessa historico; quem acumula perfil de equipe tem a permissao de leitura da equipe, mas segue impedido de decidir sobre relato proprio/proprio Material.

Isso nao e anonimizacao do texto: o autor pode escrever dados pessoais no relato e o moderador na justificativa. A API nao identifica/redige nomes/emails dentro de texto livre. Evitar dados pessoais desnecessarios e acesso/registro de corpos. Frontend deve escapar descricao/justificativa como texto simples, sem v-html/innerHTML. toString dos DTOs redige justificativa. Erros nao ecoam justificativa rejeitada ou causa SQL.

## Auditoria e schema

Nova entidade/tabela `ModeracaoDenuncia` / `moderacoes_denuncias`:

- UUID PK;
- denuncia_id e responsavel_id com FKs obrigatorias/LAZY, sem cascatas;
- justificativa varchar1000;
- estado_anterior e estado_novo como enums textuais;
- data_cadastro Instant/UTC;
- indices por denuncia/data/id e por responsavel.

Hibernate ddl-auto=update cria a tabela no prototipo. Nenhum banco precisa ser apagado/recriado; sem Flyway, dependencia ou variavel nova. Entidade Denuncia/Usuario/Material e seus campos nao mudaram. Sem preencher historico retroativamente: registros legados, inclusive EM_ANALISE, podem nao ter evento anterior.

Historico somente de transicoes bem-sucedidas. @Immutable, colunas updatable=false, ausencia de setters, repository com apenas insercao/consulta e nenhum PUT/DELETE/POST generico do historico. Nao e log inviolavel: SQL administrativo/credenciais com permissao no banco ainda podem altera-lo/apaga-lo. Retencao, protecao contra administracao direta e auditoria global sao entregas futuras.

## Transacoes e concorrencia

Spring Security nativo protege o namespace `/denuncias/moderacao` e subrotas; @PreAuthorize de equipe protege tambem o service fora do controller. Resource Server JWT, handlers Bearer e ProblemDetail existentes sao reutilizados, sem filtros JWT proprios. Validacao/binding/consumes/paginacao usam Spring Web/Bean Validation nativos.

DenunciaModeracaoService tem leituras readOnly e mutacoes @Transactional. Primeiro valida responsavel persistido e consulta **somente a projecao escalar materialId** da denuncia. Isso evita carregar/cachear estado da Denuncia antes dos locks. Depois adquire PESSIMISTIC_WRITE na ordem **Material -> Denuncia**, com denunciaId+materialId simultaneamente na segunda busca. Entao verifica conflito de interesse e estado exigido, altera/flush da Denuncia e insere/flush de ModeracaoDenuncia na mesma transacao.

Falha de auditoria reverte tambem o estado. Nao usar REQUIRES_NEW, evento assincrono ou log em memoria para essa garantia. O protocolo e compativel com cadastro de denuncias e outras mutacoes de Material; preservar essa ordem em evolucoes futuras. A consulta inicial de IDs nao e lock nem autorizacao final e nao deve substituir as buscas bloqueadas.

Conclusoes concorrentes divergentes sao serializadas: uma conclusao valida prevalece; outra encontra estado final e retorna409, sem segundo evento. Sem @Version/ETag: nao garante que o moderador revisou a ultima versao do Material/relato antes de clicar. Alteracoes ja retornadas por GET nao sao revogadas retroativamente. Tokens continuam curtos e sem revogacao imediata.

Uma denuncia concluida libera novo cadastro no mesmo par somente se nao houver outra aberta e o Material estiver PUBLICADO. Denuncia antiga nao e reaberta, evitando conflitar com novo relato. Nao foi criada constraint unica parcial ou limpeza de denuncias antigas; a regra continua coordenada pelo protocolo da API em READ_COMMITTED, nao por garantia contra SQL externo.

## Arquivos completos no repositorio

Caminhos relativos a raiz:

```text
src/main/java/br/com/murkyweb/unesc_share/interacao/denuncia/
  controller/DenunciaModeracaoController.java        [novo]
  domain/ModeracaoDenuncia.java                     [novo]
  dto/DenunciaModeracaoRequest.java                 [novo]
  dto/DenunciaModeracaoResponse.java                [novo]
  repository/ModeracaoDenunciaRepository.java       [novo]
  repository/DenunciaRepository.java                [alterado]
  service/DenunciaModeracaoService.java              [novo]
src/main/java/br/com/murkyweb/unesc_share/security/
  SecurityConfig.java                              [alterado]
src/test/java/br/com/murkyweb/unesc_share/
  interacao/denuncia/DenunciaModeracaoServiceTests.java [novo]
  material/DenunciaModeracaoApiTests.java              [novo]
  material/DenunciaModeracaoTransacaoTests.java        [novo]
```

Codigo de cadastro/consultas proprias e demais fluxos de Material/Comentario/Avaliacao nao foi alterado.

## Erros

- 400: justificativa ausente/branca/>1000/comU+0000, JSON/UUID/status/paginacao invalidos.
- 401: JWT ausente/invalido/expirado ou responsavel ausente no cadastro da mutacao.
- 403: perfil sem permissao ou conflito de interesse (denunciante/autor do Material).
- 404: denuncia inexistente nas rotas da equipe; rotas proprias continuam retornando404 para relato alheio.
- 409: transicao invalida/repetida/concluida ou conflito de integridade ao salvar estado/historico.
- 405: tentativa de escrita generica no historico/detalhes; sem edicao/exclusao do relato.
- 415: POST com tipo de corpo nao JSON.

Validacao pode ocorrer antes das consultas. Na mutacao, conflito de interesse e conferido antes de estado: membro impedido recebe403 mesmo quando a transicao tambem seria invalida. Motivo de conflito e fixo, sem informar identidade do denunciante.

## Executar e testar

Com PostgreSQL local disponivel e variaveis do .env exportadas sem exibir segredos, bootstrap administrativo desativado:

```bash
./mvnw -Dtest=DenunciaModeracaoApiTests,DenunciaModeracaoServiceTests,DenunciaModeracaoTransacaoTests test
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Agente pode executar Maven; Docker permanece exclusivo do usuario. Nao executar SQL de limpeza/recriacao de banco para disponibilizar a tabela.

Teste manual com Bearer de equipe elegivel: consulte fila, escolha denuncia de outro usuario sobre Material de outro usuario, envie POST/analise com justificativa e depois exatamente uma conclusao. Consulte historico. Repetir conclusao409; usar denunciante/autor do Material403, inclusive administrador. O status do Material deve permanecer inalterado. As acoes manuais gravam estado/historico reais e nao existe exclusao/reabertura HTTP nesta entrega. Se o unico administrador local estiver impedido, e necessario outro membro elegivel; nao ha bypass. Nao reutilizar credenciais de bootstrap ou solicitar senha real para testar: suite usa JWTs/fixtures exclusivos de teste.

Cobertura: perfis/JWT, matriz de transicoes, repeticoes, estados de Material, conflitos nas tres acoes, troca de agente entre analise/conclusao, privacidade/DTOs, consultas proprias inalteradas, paginacao/status/ordenacao, validacao/campos forjados, inexistentes e ausencia de escrita arbitraria de historico. Unitarios verificam projecao escalar/ordem dos locks, impedimentos antes de gravar e erro sem dados privados.

Tres cenarios de rollback usam fixtures temporariamente confirmadas e requisicoes sem transacao externa. Conferem UPDATE do estado e INSERT/flush do evento no PostgreSQL antes de provocar falha; nova transacao confirma estado/UUID/data/relato restaurados e ausencia de evento para analise/procedencia/improcedencia. Um cenario concorrente dispara duas requisicoes MockMvc em threads/transacoes independentes com agentes diferentes e conclusoes opostas, esperando200+409/um evento/estado final coerente/Material inalterado. Nao e trafego de rede/socket nem prova de tempo especifico de espera no lock.

Limpeza emfinally porUUIDs proprios das fixtures, filhos primeiro: historico/denuncia/Magnet do Material sintetico e seus registros sinteticos Material/Disciplina/Categoria/Curso/Instituicao/usuarios. Sem deleteAll/cascatas/catalogo real. Demais testes de API usam rollback. Nenhum conteudo BitTorrent ou arquivo academico e acessado.

## Limites e proxima entrega

Sem atribuicao exclusiva, reabertura, recurso/contestacao, notificacoes, cancelamento, auditoria de bloqueio de Material, retencao/anonimizacao ou rate limiting. Esta auditoria nao substitui revisao de seguranca de producao. Proximo passo recomendado: revisar a cobertura das permissoes/contratos HTTP implementados e planejar a integracao com o frontend em seu repositorio separado; ampliar o dominio somente apos nova decisao.

## Verificacao executada

Em21/10/2026, os31testes especificos iniciais passaram. Apos acrescentar o caso de paginacao/escopo do historico, `./mvnw clean verify` concluiu com **543 testes**, zero falhas/erros/ignorados e JAR executavel gerado. Novos:20API,8unitarios,3rollback real aposUPDATE+INSERT e1concorrente. As tres falhas aposflush restauraram status/relato/UUID/data originais e nao deixaram eventos, confirmados em novas transacoes. Conclusoes opostas simultaneas de agentes diferentes resultaram200+409/uma decisao/um evento coerente e Material inalterado. Fixtures confirmadas foram limpas porUUIDs proprios emfinally; dados reais preservados.

Nova tabela de auditoria validada no PostgreSQL pelo Hibernate, sem limpeza/recriacao do banco. Backend anterior encerrado e atualizado iniciado em localhost:8080, context-path `/api/v1`, com bootstrap administrativo desativado e zero novas disciplinas criadas. GETfila/GETdetalhe/GEThistorico/POSTanalise/POSTprocedencia/POSTimprocedencia semJWT retornaram401 emHTTP real, sem gravacoes. Acoes autenticadas verificadas na suite com JWTs exclusivos de teste, sem credenciais reais. `git diff --check` passou; JAR inclui novas classes de producao, sem classes/configuracao de teste. Nenhum comandoDocker/acesso a conteudoBitTorrent/alteracao do.envreal ocorreu.
