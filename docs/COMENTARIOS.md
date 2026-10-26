# Comentarios — cadastro e listagem

## Escopo aprovado

Primeiro incremento da Etapa 8: usuarios autenticados podem cadastrar e listar comentarios em materiais PUBLICADO, com publicacao imediata como ATIVO. Autor vem exclusivamente do JWT. A alternativa de aprovacao previa nao foi adotada nesta entrega.

- Texto obrigatorio, ate 4000 caracteres, com espacos externos removidos.
- Autor do Material e usuarios de qualquer perfil podem comentar; nao e preciso ser dono do Material.
- PENDENTE/BLOQUEADO/REMOVIDO nao permitem cadastro nem listagem comum, inclusive para o dono e a equipe. O contrato de detalhes privados de Material permanece separado.
- Listagem retorna somente ATIVO do Material da rota; OCULTO/REMOVIDO nao aparecem, nem por parametro de consulta ou perfil de moderador.
- Um usuario pode comentar mais de uma vez; nao existe a restricao de avaliacao unica nos comentarios.
- Comentar nao altera status, metadados, mecanismos ou visualizacoes do Material. Um comentario nao devolve Material a PENDENTE.
- Quando o Material deixa de ser publicado, seus comentarios ficam indisponiveis na listagem comum, mas nao sao apagados ou reclassificados.

Edicao e remocao pelo autor foram acrescentadas no [segundo incremento](COMENTARIOS_EDICAO_REMOCAO.md), com regras aprovadas separadamente. O [terceiro incremento](COMENTARIOS_MODERACAO.md) acrescenta ocultacao/reexibicao com auditoria e consultas restritas a equipe; a consulta comum nao permite ocultos. Nao ha endpoint generico que aceite status escolhido pelo cliente. Consulta individual do texto, respostas encadeadas, nomes de exibicao e rate limiting continuam pendentes; nao tratar o prototipo como pronto para publicacao irrestrita.

## API

Endpoints completos, ambos com Bearer JWT:

| Metodo | Caminho | Resultado |
| --- | --- | --- |
| POST | `/api/v1/materiais/{materialId}/comentarios` | JSON, cria ATIVO e retorna DTO com 201 |
| GET | `/api/v1/materiais/{materialId}/comentarios?page=0&size=20` | PagedModel com comentarios ATIVO, 200 |
| PUT | `/api/v1/materiais/{materialId}/comentarios/{comentarioId}` | Autor, ATIVO/PUBLICADO; edita somente texto, 200 |
| DELETE | `/api/v1/materiais/{materialId}/comentarios/{comentarioId}` | Autor, qualquer estado; remocao logica/idempotente204 |

Cadastro:

```json
{
  "conteudo": "O exemplo da segunda parte ajudou a entender o assunto."
}
```

Aceita application/json e application/*+json. Campos JSON extras como usuarioId, materialId, id, dataCadastro e status sao ignorados; nao sobrepoem a identidade do JWT, a rota, os campos gerados ou o estado inicial.

DTO de resposta: `id`, `conteudo`, `dataCadastro`, `status`, `usuarioId`, `materialId`. UUIDs e Instant/UTC, sem entidades JPA, nome/email/senha/hash/perfil do usuario ou relacionamentos completos. UUID do autor continua sendo um identificador persistente, nao garantia de anonimato. O status retornado e ATIVO.

POST retorna201 sem Location nesta entrega: nao ha rota GET individual de Comentario para anunciar. O DTO informa o UUID gerado e a colecao paginada permite consultar os comentarios. Nao anunciar URL individual inexistente.

Listagem tem page >=0 e size entre1 e100 (padrao20). Ordem fixa dataCadastro ASC, id ASC: cronologica, com desempate estavel. Parametros extras status/usuarioId/materialId/sort nao substituem os predicados/ordenacao do servidor. Material publicado sem comentarios retorna pagina vazia; Material inexistente ou nao publicado retorna404, nao pagina vazia.

## Arquivos e responsabilidades

Os arquivos completos estao no workspace, nos caminhos relativos a raiz:

```text
src/main/java/br/com/murkyweb/unesc_share/
├── interacao/comentario/
│   ├── controller/ComentarioController.java
│   ├── dto/ComentarioRequest.java
│   ├── dto/ComentarioResponse.java
│   ├── repository/ComentarioRepository.java
│   └── service/ComentarioService.java
└── material/repository/MaterialRepository.java

src/test/java/br/com/murkyweb/unesc_share/
├── interacao/comentario/ComentarioServiceTests.java
└── material/
    ├── ComentarioApiTests.java
    └── ComentarioTransacaoTests.java
```

Entidade Comentario e enum ATIVO/OCULTO/REMOVIDO existentes foram preservados. Nenhum campo/schema/dependencia/migration/variavel nova. Testes de API ficam no pacote material para reutilizar o suporte transacional/JWT ja existente, sem duplicar fixtures.

Controller usa @RequestBody, @Valid e constraints dos parametros com [validacao nativa do Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html). Request valida @NotBlank/@Size e @Pattern para rejeitar U+0000, que nao pode ser persistido em texto PostgreSQL. Nao ha validator/filtro HTTP proprio, nem regex para transformar comentarios em HTML seguro.

Service usa @PreAuthorize(isAuthenticated) tambem nas chamadas fora do controller. No POST, resolve usuario pelo UUID do JWT ja validado pelo Resource Server e usa o mesmo lock JPA PESSIMISTIC_WRITE das mutacoes de Material. Verifica PUBLICADO dentro da transacao de escrita, depois insere e da flush. Isso coordena cadastro com mudancas de estado; nao foi criado outro mecanismo de autenticacao ou lock em memoria.

No GET, existe verificacao de existencia/PUBLICADO para o404. O predicado PUBLICADO integra tambem a propria consulta e sua contagem, junto de materialId e status ATIVO, para nao depender exclusivamente do pre-check. Nao reutiliza a visibilidade privada de detalhes do Material para liberar comentarios de nao publicados. Como em qualquer leitura HTTP, dados ja devolvidos nao sao revogados quando uma mudanca ocorre depois; nao ha snapshot/ETag de revisao nesta entrega.

DTOs sao montados dentro da transacao, sem open-in-view. A listagem nao carrega colecoes de Material ou objetos completos de Usuario; usa os UUIDs dos relacionamentos. Datas/PK/FKs, LAZY, ausencia de cascatas destrutivas e integridade existentes permanecem.

## Texto e seguranca

Conteudo e texto simples retornado como JSON, nao HTML renderizado. Tags literais sao preservadas como texto; nao ha Markdown/HTML habilitado, interpretacao de templates, auto-abertura de links ou consulta de recursos externos. No frontend Vue, usar interpolacao/v-text e nao v-html/innerHTML com comentarios nao confiaveis, conforme a [documentacao oficial de seguranca do Vue](https://vuejs.org/guide/best-practices/security.html). O frontend continua em outro repositorio; nenhuma tela foi implementada aqui.

O limite e em caracteres Java, nao um limite global de bytes para qualquer JSON. Unicode e quebras de linha sao aceitos, U+0000 e rejeitado, e nenhum arquivo academico pode ser enviado a esta rota. Spring Security mantem nosniff e os demais headers existentes.

toString dos DTOs redige conteudo; a serializacao JSON autorizada continua retornando o texto. Nao registrar corpos de comentarios, senhas, tokens, headers Authorization ou valores SQL. Erros de validacao/integridade nao ecoam texto rejeitado nem causas JDBC. Nao solicitar credenciais reais para testes.

Publicacao imediata nao significa ausencia de necessidade de moderacao. Spam/abuso, quotas, rate limiting, denuncias, auditoria e retencao precisam de regras nas proximas entregas. Edicao/remocao pelo autor seguem o documento do segundo incremento; remocao e logica, nao apagamento do texto. O incremento nao altera Material/Torrent/Magnet nem a premissa de catalogo BitTorrent.

## Erros

- 400: conteudo/JSON/UUID/paginacao invalidos.
- 401: JWT ausente/invalido/expirado; no cadastro, usuario do subject nao existe.
- 404: Material inexistente ou nao PUBLICADO, sem excecao para autor/equipe.
- 409: conflito de integridade ao persistir, com mensagem fixa.
- 415: tipo de requisicao nao JSON, inclusive PDF direto.

Bean Validation pode rejeitar dados antes de consultar o estado do Material. ProblemDetail/ResponseEntityExceptionHandler e handlers Bearer nativos sao os mesmos do restante da API. PUT/DELETE individuais e seus erros estao detalhados em COMENTARIOS_EDICAO_REMOCAO.md; nao ha bypass de moderacao por campos extras ou metodo generico de atualizacao.

## Executar e testar

Com PostgreSQL local disponivel e bootstrap administrativo desativado:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Exemplo manual com Material publicado e TOKEN/MATERIAL_ID definidos no terminal, sem compartilhar tokens no chat/Git:

```bash
curl --fail-with-body \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  --data '{"conteudo":"Este material ajudou no estudo."}' \
  "http://localhost:8080/api/v1/materiais/${MATERIAL_ID}/comentarios"

curl --fail-with-body \
  -H "Authorization: Bearer ${TOKEN}" \
  "http://localhost:8080/api/v1/materiais/${MATERIAL_ID}/comentarios?page=0&size=20"
```

POST manual bem-sucedido cria registro real. O segundo incremento permite DELETE logico pelo autor, sem apagar o texto do banco nem oferecer restauracao. Evite dados pessoais nos comentarios. Tokens em linha de comando podem aparecer na lista de processos; prefira um cliente HTTP que proteja segredos em uso real.

Testes automatizados, com ambiente carregado silenciosamente:

```bash
./mvnw -Dtest=ComentarioApiTests,ComentarioServiceTests,ComentarioTransacaoTests test
./mvnw clean verify
```

Os testes iniciais usam PostgreSQL, JWT/filters/encoder/decoder nativos, dados sinteticos separados e transacoes revertidas. Cobrem perfis/autoria, PUBLICADO e tres estados privados, ATIVO/OCULTO/REMOVIDO, predicado no banco, ordem/paginacao/desempate, payloads/injecao de campos, privacidade dos DTOs/erros, limite/Unicode/NUL e protecao do service. PUT/DELETE na colecao e GET individual seguem sem suporte. O teste de falha insere/da flush, injeta conflito, encerra com rollback e confere a ausencia do registro em outra transacao. As fixtures tambem sao revertidas. Testes de mutacao e fixtures temporariamente confirmadas/limpas do segundo incremento estao descritos em COMENTARIOS_EDICAO_REMOCAO.md.

Nao ha teste end-to-end de corrida entre cadastro de comentario e bloqueio HTTP; o fluxo reutiliza o lock de Material e verifica o predicado SQL. Hibernate continua ddl-auto=update no prototipo. Nenhum registro real da Matriz6 e usado para mutacoes de teste, nenhum conteudo BitTorrent e baixado e nenhum comando Docker e executado pelo agente.

Edicao/remocao pelo autor implementadas no segundo incremento e moderacao com auditoria minima no terceiro, com documentos proprios. A API de avaliacoes foi implementada posteriormente em [AVALIACOES.md](AVALIACOES.md), com edicao/exclusao propria no [segundo incremento](AVALIACOES_EDICAO_REMOCAO.md). Avaliacoes permanecem notas decimais de0,00a5,00 com duas casas e uma por usuario/Material. Proxima entrega: definir denuncias.

## Verificacao executada

Em21/10/2026, `./mvnw clean verify` passou com **382 testes**, zero falhas/erros/ignorados e JAR executavel gerado. Sao26casos novos:20de API/persistencia/seguranca,5unitarios e1de rollback apos insercao. O conjunto selecionado inicial de24passou antes dos dois casos adicionais de predicado no banco/protecao do service. `git diff --check` passou; JAR contem as classes de producao e nao inclui classes/configuracao de teste.

O backend atualizado iniciou em localhost:8080 com context-path `/api/v1`, bootstrap administrativo desativado e carga academica criando zero disciplinas. GET/POST locais das novas rotas sem JWT retornaram401, sem gravarem dados. A primeira tentativa de encerrar a instancia anterior teve timeout na revisao automatica de permissao; a repeticao foi autorizada e a reinicializacao foi confirmada pelos logs.

Nao foram solicitadas credenciais reais, nenhum comentario/material de teste permaneceu no banco, nenhum conteudo BitTorrent foi acessado, nenhum comando Docker foi executado e o `.env` real nao foi alterado pelo agente.
