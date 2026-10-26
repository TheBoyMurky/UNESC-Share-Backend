# CRUD de cursos

Frontend em26/10/2026: consulta paginada para os três perfis, cadastro/edição/exclusão somente ADMINISTRADOR. Edição fixa o vínculo; exclusão por UUID em tela dedicada, confirmação explícita e sucesso estrito204. API preserva409 quando há disciplinas vinculadas, sem cascata. **1260 testes frontend**, TypeScript/build e **27 testes Maven de Curso/PostgreSQL** aprovados. HTTP sintético do frontend não integra Vue/Spring. Guias: [Consulta](../../UNESC-Share-Frontend/docs/CURSOS_CONSULTA_FRONTEND.md), [cadastro](../../UNESC-Share-Frontend/docs/CURSOS_CADASTRO_FRONTEND.md), [edição](../../UNESC-Share-Frontend/docs/CURSOS_EDICAO_FRONTEND.md), [exclusão](../../UNESC-Share-Frontend/docs/CURSOS_EXCLUSAO_FRONTEND.md). Próximo: validação visual/manual no navegador quando disponível, depois CRUD administrativo de disciplinas.

Este incremento continua a Etapa 4, adicionando cursos vinculados a instituicoes. Nao implementa ainda o CRUD de disciplinas, materiais ou mecanismos BitTorrent.

## Regras e escopo

- Qualquer usuario autenticado pode listar e consultar cursos.
- Somente `ADMINISTRADOR` pode cadastrar, editar e excluir; usuarios e moderadores recebem `403` nas escritas.
- O cadastro exige uma instituicao existente.
- Nomes de cursos sao verificados sem distinguir caixa dentro da mesma instituicao. O mesmo nome e permitido em instituicoes diferentes, conforme a constraint ja existente.
- Nesta entrega, a instituicao do curso e fixa: PUT pode editar nome/descricao, mas nao transferir o curso para outra instituicao.
- Curso com disciplinas nao pode ser excluido: retorna `409`, sem cascatas ou remocao de registros vinculados.
- A exclusao de curso sem disciplinas preserva a instituicao.

A escolha inicial de manter o vinculo fixo evita que uma edicao simples mova implicitamente o contexto de disciplinas e materiais. A alternativa de transferencia exigira uma regra explicita e verificacoes proprias em outra entrega; nao esta implementada.
Essa administracao da estrutura academica nao restringe o futuro cadastro de materiais pelos estudantes.

## Arquivos

Caminhos relativos a raiz do repositorio; os arquivos completos estao nesses locais:

```text
src/main/java/br/com/murkyweb/unesc_share/
├── academico/curso/
│   ├── controller/CursoController.java
│   ├── dto/CursoRequest.java
│   ├── dto/CursoResponse.java
│   ├── domain/Curso.java
│   ├── repository/CursoRepository.java
│   └── service/CursoService.java
├── academico/disciplina/repository/DisciplinaRepository.java
└── security/SecurityConfig.java

src/test/java/br/com/murkyweb/unesc_share/academico/curso/
├── CursoApiTests.java
└── CursoServiceTests.java
```

O controller trata HTTP, parametros e Bean Validation. O service aplica regras e transacoes, convertendo entidades para DTOs dentro da transacao. Os repositories usam consultas derivadas do Spring Data JPA.
Nao ha alteracoes de entidades, tabelas, migracoes ou dependencias. O relacionamento com instituicao continua `LAZY`; o DTO retorna apenas seu UUID, sem serializar a entidade ou a lista de disciplinas.
Matchers nativos protegem as escritas antes da leitura do corpo; `@PreAuthorize` no service tambem protege chamadas fora do controller.

## Endpoints

Todos exigem `Authorization: Bearer <token>`. O controller usa `/cursos`, sem repetir o context-path `/api/v1`.

| Metodo | Endpoint | Sucesso | Acesso |
| --- | --- | --- | --- |
| GET | `/api/v1/cursos` | `200`, pagina | Autenticado |
| GET | `/api/v1/cursos/{id}` | `200`, DTO | Autenticado |
| POST | `/api/v1/cursos` | `201`, DTO e `Location` | Administrador |
| PUT | `/api/v1/cursos/{id}` | `200`, DTO atualizado | Administrador |
| DELETE | `/api/v1/cursos/{id}` | `204`, sem corpo | Administrador |

### Corpo de cadastro e edicao

```json
{
  "nome": "Curso Exemplo",
  "descricao": "Descricao opcional do curso.",
  "instituicaoId": "00000000-0000-0000-0000-000000000001"
}
```

Use um UUID real de instituicao, obtido pelo CRUD de instituicoes. Os campos sao:

- `nome`: obrigatorio, ate 150 caracteres, sem espacos externos.
- `descricao`: opcional, ate 1000 caracteres, sem espacos externos; vazia/branca vira `null`.
- `instituicaoId`: UUID obrigatorio; em PUT deve ser o mesmo vinculo atual.

PUT exige o corpo completo e nao e upsert: UUID de curso inexistente retorna `404`. Omitir a descricao em PUT limpa esse campo.
Corpo com outra instituicao retorna `409`, mesmo se o curso ainda nao possuir disciplinas. Nao ha consulta ou atualizacao da instituicao de destino nesse caso.
O UUID do curso e gerado no cadastro e preservado na edicao. Campos extras como `id`, `instituicao` ou `disciplinas` nao permitem alterar entidades relacionadas.

A resposta contem somente `id`, `nome`, `descricao` e `instituicaoId`.

### Listagem e filtro

```text
GET /api/v1/cursos?page=0&size=20
GET /api/v1/cursos?instituicaoId={uuid}&page=0&size=20
```

`page` inicia em zero. `size` vai de 1 a 100, com padrao 20. A ordenacao e fixa por nome e depois UUID.
O parametro `instituicaoId` e opcional. Sem ele, a consulta abrange todas as instituicoes; com ele, filtra somente os cursos vinculados ao UUID informado.
Um UUID bem formado, mas sem cursos correspondentes (inclusive de instituicao inexistente), retorna pagina vazia com `200`; UUID malformado retorna `400`.

A resposta usa `PagedModel` nativo do Spring Data, com `content` e `page` (`size`, `number`, `totalElements`, `totalPages`), seguindo o contrato de [INSTITUICOES.md](INSTITUICOES.md).

### Erros

- `400`: JSON, UUID, campos ou paginacao invalidos.
- `401`: token ausente, invalido ou expirado.
- `403`: perfil sem permissao para escrita.
- `404`: curso inexistente por UUID ou instituicao inexistente no cadastro.
- `409`: nome duplicado na instituicao, tentativa de transferencia, curso com disciplinas ou conflito de integridade no banco.

Os erros MVC reutilizam `ResponseEntityExceptionHandler` e `ProblemDetail`. Falhas de seguranca continuam usando os handlers Bearer nativos, sem exigir um corpo JSON.
Uma FK pode falhar se a instituicao for excluida concorrentemente ao cadastro; por isso o conflito tardio ao salvar retorna mensagem generica de integridade, nao afirma que toda violacao e duplicidade.
Na exclusao, a consulta previa de disciplinas e complementada pela FK e por `flush` dentro da transacao. Nao se excluem disciplinas para liberar a exclusao do curso.

Limite do prototipo: a constraint `(instituicao_id, nome)` distingue caixa no PostgreSQL. As verificacoes previas ignoram caixa, mas nao garantem unicidade case-insensitive contra duas escritas simultaneas com grafias diferentes. Nenhum indice funcional novo foi criado.

## Executar e testar

Com PostgreSQL local ativo e as variaveis do `.env` exportadas:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Depois de criar o administrador inicial, mantenha `ADMIN_SEED_ENABLED=false` e nao mantenha as credenciais de bootstrap no arquivo/ambiente. O login usa a senha ja cadastrada no banco, nao depende dessas variaveis.

Em outro terminal com variaveis de conexao exportadas:

```bash
./mvnw -Dtest=CursoApiTests,CursoServiceTests test
./mvnw clean verify
```

Os testes HTTP usam a cadeia real de filtros, JWT assinado/validado pelos componentes nativos e PostgreSQL local. Cobrem todos os perfis, CRUD, filtro/paginacao/ordenacao, validacao, duplicidades por instituicao, nomes iguais em instituicoes diferentes, DTOs, UUIDs e preservacao dos relacionamentos.
Os testes unitarios cobrem tambem traducao de conflitos de constraints/FK, mapeamento de paginas e recusa de transferencia antes de alterar o objeto. O teste de FK concorrente usa uma simulacao unitaria, nao duas transacoes simultaneas reais.
O perfil `test` desativa ambos os runners. Os registros dos testes HTTP sao revertidos ao final; nao excluir ou alterar a Matriz 6 para testar o CRUD. O schema continua sujeito a `ddl-auto=update`.

Verificacao em 20/10/2026: os 27 testes novos passaram isoladamente e a suite `./mvnw clean verify` passou com 106 testes no total, sem falhas, erros ou testes ignorados, gerando o JAR executavel.
O backend atualizado foi reiniciado em localhost:8080 com `dev`, bootstrap administrativo desativado e credenciais de bootstrap ausentes/vazias, sem exibir valores. A carga academica informou zero novas disciplinas. Os testes de CRUD usaram dados transacionais; nenhum curso real da Matriz 6 foi editado ou excluido pelo agente.

### Exemplo manual

Obtenha um token de administrador pelo login normal e use um UUID real de instituicao em `INSTITUICAO_ID`:

```bash
curl "http://localhost:8080/api/v1/cursos?instituicaoId=$INSTITUICAO_ID&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN_ADMIN"

curl -i -X POST 'http://localhost:8080/api/v1/cursos' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d "{\"nome\":\"Curso Exemplo\",\"descricao\":\"Curso para teste manual\",\"instituicaoId\":\"$INSTITUICAO_ID\"}"
```

Use apenas registros criados intencionalmente para teste, sem alterar os identificadores do curso da Matriz 6. O populador academico usa instituicao/nome do curso para idempotencia e pode recriar referencias se esses identificadores forem modificados com `SEED_ENABLED=true`.
Nenhum comando Docker e executado pelo agente. Se informacoes ou operacoes Docker forem necessarias, os comandos devem ser enviados ao usuario.

## Proximo incremento

O incremento seguinte de disciplinas foi implementado e esta documentado em [DISCIPLINAS.md](DISCIPLINAS.md): codigo unico por curso, vinculo fixo e exclusao bloqueada quando houver materiais. O CRUD de cursos nao implementa materiais ou upload.
