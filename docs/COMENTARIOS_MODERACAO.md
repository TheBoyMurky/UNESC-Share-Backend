# Moderacao de comentarios e auditoria minima

## Decisao aprovada

Terceiro incremento de comentarios. O usuario aprovou a opcao com historico persistido, em vez de alterar somente o status. A equipe pode ocultar e reexibir, mas nao editar texto de terceiros nem restaurar comentarios REMOVIDO. Autenticacao e autorizacao continuam usando Resource Server JWT, matchers e @PreAuthorize nativos; nao ha filtros JWT proprios.

| Acao | Permissao e condicao | Resultado |
|---|---|---|
| Listar para moderacao | MODERADOR/ADMINISTRADOR, qualquer estado do Material | Somente ATIVO/OCULTO, nunca texto de REMOVIDO |
| Ocultar | Equipe, comentario ATIVO, qualquer estado do Material | OCULTO, com motivo e historico |
| Reexibir | Equipe, comentario OCULTO, Material PUBLICADO | ATIVO, com motivo e historico |
| Consultar historico | Equipe, comentario pertencente ao Material da rota | Metadados das decisoes, mesmo apos remocao logica |

Ocultacao e reexibicao sao transicoes reais. Repetir a mesma acao retorna409 e nao duplica historico; nao sao endpoints idempotentes. REMOVIDO e terminal para estas acoes (409). A equipe pode moderar tambem o proprio comentario. O autor segue podendo remover seu comentario oculto em qualquer estado do Material; isso nao apaga o historico.

A consulta comum permanece inalterada: somente ATIVO em Material PUBLICADO, sem excecao para dono/equipe. Nenhuma consulta individual do texto de comentarios removidos foi adicionada. A consulta restrita inclui textos ocultos e materiais nao publicados, mas exige perfil de equipe tanto no HTTP quanto no service.

## Contrato HTTP

Prefixo das rotas abaixo: `/api/v1/materiais/{materialId}/comentarios`.

| Metodo | Sufixo | Resposta |
|---|---|---|
| GET | `/moderacao?page=0&size=20` | 200, PagedModel de ComentarioResponse |
| POST | `/{comentarioId}/ocultacao` | 200, ComentarioResponse atualizado |
| POST | `/{comentarioId}/reexibicao` | 200, ComentarioResponse atualizado |
| GET | `/{comentarioId}/historico-moderacao?page=0&size=20` | 200, PagedModel de ComentarioModeracaoResponse |

POST aceita application/json e application/*+json. Corpo completo:

```json
{
  "motivo": "Comentario fora do contexto academico do material."
}
```

Motivo obrigatorio, strip, ate1000caracteres apos strip, sem U+0000. Texto simples: frontend deve escapar tanto comentario quanto motivo; nao usar v-html/innerHTML com esses dados. Campos adicionais nao escolhem responsavel/estados/data, nem alteram texto/autor/material. Responsavel vem do subject JWT e precisa existir no banco. Estados sao derivados exclusivamente da acao e do registro bloqueado.

Listagens usam page>=0, size1..100, padrao20, ordenacao fixa dataCadastro/id ASC. Parametros extras status/usuarioId/sort nao ampliam visibilidade nem mudam ordenacao. Material existente sem ATIVO/OCULTO retorna pagina vazia. Historico de comentario existente sem decisoes retorna pagina vazia.

ComentarioResponse continua com seis campos: id, conteudo, dataCadastro, status, usuarioId e materialId. Historico tem exatamente sete campos:

```json
{
  "id": "<UUID da decisao>",
  "comentarioId": "<UUID do comentario>",
  "responsavelId": "<UUID do moderador ou administrador>",
  "motivo": "Comentario fora do contexto academico do material.",
  "estadoAnterior": "ATIVO",
  "estadoNovo": "OCULTO",
  "dataCadastro": "2026-10-21T14:00:00Z"
}
```

UUIDs do exemplo sao placeholders. O historico nao copia o texto do comentario nem expoe email/nome/senha/perfil do responsavel. A equipe pode consultar motivos de decisoes anteriores inclusive apos o autor remover o comentario; usuarios comuns nao podem. PUT/DELETE/POST na rota de consulta do historico retornam405 para equipe: nao existe escrita HTTP arbitraria de auditoria.

Erros: 400 validacao/UUID/JSON/paginacao, 401 JWT ou responsavel ausente na mutacao, 403 perfil sem permissao, 404 Material/Comentario inexistente ou comentario fora do Material da rota, 409 transicao invalida/Material nao publicado na reexibicao/integridade, 415 corpo nao JSON nos POSTs. Spring Web fornece ProblemDetail e handlers Bearer nativos tratam seguranca. Validacao pode ocorrer antes de consultar registros. Erros nao ecoam motivo, conteudo ou causa SQL.

## Estrutura e arquivos completos

Os arquivos abaixo contem o codigo completo no repositorio, relativo a sua raiz:

```text
src/main/java/br/com/murkyweb/unesc_share/
  interacao/comentario/
    controller/ComentarioModeracaoController.java       [novo]
    domain/ModeracaoComentario.java                    [novo]
    dto/ComentarioModeracaoRequest.java                [novo]
    dto/ComentarioModeracaoResponse.java               [novo]
    repository/ModeracaoComentarioRepository.java       [novo]
    repository/ComentarioRepository.java               [alterado]
    service/ComentarioModeracaoService.java             [novo]
  security/SecurityConfig.java                         [alterado]
src/test/java/br/com/murkyweb/unesc_share/
  interacao/comentario/ComentarioModeracaoServiceTests.java [novo]
  material/ComentarioModeracaoApiTests.java                [novo]
  material/ComentarioModeracaoTransacaoTests.java          [novo]
```

Nova tabela `moderacoes_comentarios`: UUID PK, comentario_id e responsavel_id com FKs obrigatorias/LAZY e sem cascatas, motivo varchar1000, estado_anterior/estado_novo textuais e data_cadastro Instant/UTC. Indices por comentario/data/id e responsavel. O schema e atualizado pelo Hibernate com ddl-auto=update do prototipo; nao ha Flyway, dependencia nova ou variavel nova. Nao e preciso apagar/recriar o banco nem executar SQL de limpeza para esta entrega.

ModeracaoComentario e entidade independente somente para decisoes de comentarios. Sem setters/metodos de edicao, @Immutable e colunas updatable=false. Repository expoe somente insercao e consulta, sem metodos de exclusao/atualizacao. Nenhuma colecao/cascata foi adicionada a Usuario/Comentario e as entidades nao sao serializadas pela API.

Essa protecao e da aplicacao/ORM/API, **nao um log inviolavel**: credenciais com permissao SQL ainda podem modificar/apagar dados diretamente. Nao ha assinatura, hash encadeado, trigger ou controle de acesso dedicado no banco nesta entrega. Uma politica de retencao/anonimizacao e protecao contra administracao direta sera necessaria para producao.

## Transacao e concorrencia

Controller somente faz binding/validacao/DTO. ComentarioModeracaoService separa regras da autoria, usa @PreAuthorize de equipe, @Transactional(readOnly=true) nas leituras e @Transactional nas mutacoes publicas. A mutacao adquire PESSIMISTIC_WRITE na ordem Material -> Comentario, a mesma da edicao/remocao pelo autor. O repository exige comentarioId e materialId simultaneamente para evitar agir sobre comentario de outro Material.

Depois de validar estados, altera/flush do Comentario e insere/flush de ModeracaoComentario na **mesma transacao**. Falha na auditoria reverte tambem o status; nao usar REQUIRES_NEW, evento assincrono ou log em memoria para essa garantia. Autor, texto, data original, metadados/estado/views/mecanismos do Material sao preservados.

Sem @Version/ETag: os locks coordenam mutacoes do backend, mas nao comprovam que o moderador leu a ultima versao antes de agir. Esta entrega testa ordem dos locks e rollback, nao duas requisicoes HTTP concorrentes. Material bloqueado/removido impede reexibicao, mas nao impede ocultar ATIVO. Historico pode ter varias decisoes alternadas; nao existe unicidade por comentario/acao.

A auditoria minima registra apenas ocultacao/reexibicao bem-sucedidas. Nao e historico completo de edicoes, exclusao pelo autor, login ou moderacao de Material. Decisoes anteriores nao sao preenchidas retroativamente; comentarios antigos continuam validos sem historico.

## Executar e testar

Com PostgreSQL local disponivel e variaveis do .env exportadas, sem exibir seus valores:

```bash
./mvnw -Dtest=ComentarioModeracaoApiTests,ComentarioModeracaoServiceTests,ComentarioModeracaoTransacaoTests test
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Mantenha o bootstrap administrativo desativado apos o provisionamento inicial. O agente nao executa Docker; operacoes Docker continuam exclusivas do usuario. Use um cliente HTTP com Bearer de equipe, crie um comentario de teste em material publicado e envie o motivo nas rotas acima. Uma acao manual bem-sucedida persiste status/historico reais; testes automatizados usam somente fixtures sinteticas.

Cobertura: perfis/JWT reais, service protegido fora do controller, todos os estados de Material/Comentario, transicoes/repeticoes, listagem restrita/comum, exclusao pelo autor preservando historico, motivos/limites/campos extras, paginacao/ordenacao, rotas cruzadas, ausencia de vazamento e nenhum endpoint de edicao/exclusao da auditoria.

Dois cenarios de rollback real usam fixtures sinteticas temporariamente confirmadas, sem transacao externa na requisicao. Injetam falha apos UPDATE do status e INSERT/flush do historico, conferem ambos no PostgreSQL antes da falha e verificam em nova transacao o status original e a ausencia do evento. A limpeza em finally remove apenas UUIDs gerados pelo teste, filhos primeiro, sem deleteAll/cascatas ou registros reais. Os demais testes de API revertem suas transacoes.

## Limites e proxima entrega

Sem denuncias, quotas/rate limiting, revisoes de texto, auditoria global, retencao ou protecao inviolavel do historico. Autorizacao de equipe vem do JWT e mantem a limitacao existente de token curto sem revogacao imediata. Nenhum upload de conteudo academico ou acesso a rede BitTorrent foi adicionado.

A API de avaliacoes foi aprovada e implementada em [AVALIACOES.md](AVALIACOES.md), mantendo nota decimal0,00a5,00 e uma por usuario/Material, sem autoavaliacao. Edicao e exclusao fisica somente da propria nota foram aprovadas no [segundo incremento](AVALIACOES_EDICAO_REMOCAO.md). Proxima entrega: definir denuncias.

## Verificacao executada

Em21/10/2026, o conjunto inicial de27testes passou. Apos acrescentar a verificacao de perfil insuficiente no service sem controller, `./mvnw clean verify` passou com **429 testes**, zero falhas/erros/ignorados, JAR executavel gerado. Sao28novos:20API,6unitarios,2rollback real. Os dois cenarios de rollback confirmaram UPDATE+INSERT antes da falha e restauracao/ausencia em nova transacao; suas fixtures temporarias foram limpas e os dados reais preservados. A tabela de auditoria foi criada pelo Hibernate, sem recriacao do banco.

O backend anterior foi encerrado e a versao atualizada iniciou em localhost:8080, context-path `/api/v1`, com bootstrap administrativo desativado e zero novas disciplinas. GETmoderacao/GEThistorico/POSTocultacao/POSTreexibicao sem JWT retornaram401 em HTTP real, sem inserir/alterar registros. As acoes autenticadas foram validadas nos testes com JWTs exclusivos de teste, nao com credenciais reais. `git diff --check` passou; JAR contem classes novas de producao e nao contem classes/configuracao de testes. Nenhum comando Docker ou acesso a conteudo BitTorrent ocorreu; o `.env` real nao foi alterado.
