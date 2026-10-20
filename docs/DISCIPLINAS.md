# CRUD de disciplinas

Este incremento conclui o nucleo da Etapa 4: instituicao, curso e disciplina possuem CRUDs. Nao implementa ainda a API de categorias, materiais, torrents ou magnets.

## Regras deste incremento

- Qualquer usuario autenticado pode listar e consultar disciplinas.
- Somente `ADMINISTRADOR` pode cadastrar, editar e excluir; usuarios e moderadores nao alteram a estrutura academica.
- O cadastro exige um curso existente.
- O codigo identifica a disciplina dentro do curso. O mesmo codigo pode existir em cursos diferentes; nomes iguais com codigos diferentes tambem sao permitidos.
- Nome, descricao e codigo podem ser corrigidos preservando o UUID e os vinculos dos materiais. O curso permanece fixo em PUT.
- Qualquer material vinculado impede a exclusao, inclusive materiais em `REMOVIDO`. A remocao logica de um material nao elimina suas referencias nem autoriza excluir sua disciplina.
- Sem materiais, a disciplina pode ser excluida fisicamente, preservando o curso e a instituicao.

A alternativa de transferir uma disciplina para outro curso moveria o contexto dos materiais associados. Por isso, a transferencia fica fora desta entrega, seguindo o criterio ja adotado para cursos/instituicoes.
O codigo nao e limitado aos cinco digitos da Matriz 6: a plataforma admite outros cursos/instituicoes. Ele e uma string obrigatoria de ate 30 caracteres, normalizada para maiusculas com `Locale.ROOT`, sem remover zeros a esquerda.
O modelo continua com `Material` central e `Torrent`/`Magnet` independentes. Nao existe upload de conteudo academico nesta API.

## Arquivos

Os arquivos completos estao nestes caminhos, relativos a raiz do repositorio:

```text
src/main/java/br/com/murkyweb/unesc_share/
├── academico/disciplina/
│   ├── controller/DisciplinaController.java
│   ├── dto/DisciplinaRequest.java
│   ├── dto/DisciplinaResponse.java
│   ├── domain/Disciplina.java
│   ├── repository/DisciplinaRepository.java
│   └── service/DisciplinaService.java
├── material/repository/MaterialRepository.java
└── security/SecurityConfig.java

src/test/java/br/com/murkyweb/unesc_share/academico/disciplina/
├── DisciplinaApiTests.java
└── DisciplinaServiceTests.java
```

O controller trata HTTP e Bean Validation. O service aplica regras e transacoes e converte entidades para DTOs dentro da transacao. Os repositories usam consultas derivadas do Spring Data JPA.
Nao foram alteradas entidades, tabelas, migracoes ou dependencias. O DTO usa apenas o UUID do curso e nao expoe entidades JPA ou colecoes de materiais.
Os matchers nativos restringem escrita antes de ler o corpo. `@PreAuthorize` no service protege tambem chamadas fora do controller. Nao ha filtro JWT ou tratamento HTTP paralelo aos recursos nativos ja existentes.

## Contrato HTTP

Todos os endpoints exigem `Authorization: Bearer <token>`. O controller usa `/disciplinas`, sem duplicar o context-path `/api/v1`.

| Metodo | Endpoint | Sucesso | Acesso |
| --- | --- | --- | --- |
| GET | `/api/v1/disciplinas` | `200`, pagina | Autenticado |
| GET | `/api/v1/disciplinas/{id}` | `200`, DTO | Autenticado |
| POST | `/api/v1/disciplinas` | `201`, DTO e `Location` | Administrador |
| PUT | `/api/v1/disciplinas/{id}` | `200`, DTO atualizado | Administrador |
| DELETE | `/api/v1/disciplinas/{id}` | `204`, sem corpo | Administrador |

### Cadastro e edicao

```json
{
  "nome": "Disciplina Exemplo",
  "descricao": "Descricao opcional.",
  "codigo": "EX-001",
  "cursoId": "00000000-0000-0000-0000-000000000001"
}
```

Use um UUID real de curso, obtido em [CURSOS.md](CURSOS.md):

- `nome`: obrigatorio, ate 150 caracteres, sem espacos externos.
- `descricao`: opcional, ate 1000 caracteres; espacos externos sao retirados e branco/vazio vira `null`.
- `codigo`: obrigatorio, ate 30 caracteres apos normalizacao, sem espacos externos e em maiusculas. Envie como string para preservar zeros iniciais.
- `cursoId`: UUID obrigatorio; em PUT deve ser o mesmo vinculo atual.

PUT exige o corpo completo e nao e upsert. Omitir a descricao limpa esse campo. Outro `cursoId` retorna `409`, mesmo quando a disciplina ainda nao possui materiais; nenhum curso de destino e consultado ou modificado nessa tentativa.
Campos extras como `id`, `curso` ou `materiais` nao alteram entidades relacionadas nem substituem o UUID gerado pelo servidor.
A resposta contem somente `id`, `nome`, `descricao`, `codigo` e `cursoId`.

### Listagem e filtro

```text
GET /api/v1/disciplinas?page=0&size=20
GET /api/v1/disciplinas?cursoId={uuid}&page=0&size=20
```

`page` inicia em zero e nao aceita negativos; `size` vai de 1 a 100, com padrao 20. A ordenacao e fixa por nome e depois UUID.
Sem filtro, lista disciplinas de todos os cursos. Com `cursoId`, lista apenas as vinculadas ao UUID informado.
Um UUID bem formado sem resultados, inclusive de curso inexistente, retorna pagina vazia com `200`; parametro malformado retorna `400`.
A resposta usa `PagedModel` nativo do Spring Data: `content` e metadados `page` (`size`, `number`, `totalElements`, `totalPages`), seguindo os CRUDs anteriores.

### Erros e integridade

- `400`: JSON, campos, UUID ou paginacao invalidos.
- `401`: token ausente, invalido ou expirado.
- `403`: usuario autenticado sem perfil administrativo tentando escrever.
- `404`: disciplina inexistente por UUID ou curso inexistente no cadastro.
- `409`: codigo duplicado no curso, tentativa de transferencia, materiais vinculados ou conflito de integridade no banco.

Erros MVC reutilizam `ResponseEntityExceptionHandler` e `ProblemDetail`. As falhas de seguranca continuam com os handlers Bearer nativos; nao presumir corpo JSON nessas respostas.
A verificacao previa de duplicidade ignora caixa e exclui o proprio UUID em PUT. A constraint existente `(curso_id, codigo)` permanece como protecao do banco; ela e textual e distingue caixa. A normalizacao das escritas desta API reduz diferencas de grafia, mas nao substitui um indice case-insensitive contra escritas externas/concorrentes com grafias distintas. Nenhum indice funcional foi adicionado.
Se uma FK falhar concorrentemente ao salvar, o erro tardio e uma mensagem generica de integridade, sem afirmar que toda violacao e duplicidade.
Na exclusao, a consulta de materiais nao filtra status. A FK e o `flush` dentro da transacao tambem protegem contra um material vinculado depois dessa consulta. Nao se excluem materiais ou mecanismos de compartilhamento para liberar a exclusao da disciplina.

## Executar e testar

Com PostgreSQL local disponivel, na raiz do repositorio:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

O bootstrap administrativo deve permanecer desativado; nao sao necessarias as credenciais de bootstrap para testar a aplicacao ou executar os testes automatizados.
Em outro terminal com as variaveis de conexao exportadas:

```bash
./mvnw -Dtest=DisciplinaApiTests,DisciplinaServiceTests test
./mvnw clean verify
```

Os testes HTTP usam a cadeia real de filtros, JWTs assinados/validados pelos componentes nativos e PostgreSQL local. Cobrem perfis, CRUD, filtro, paginacao/ordenacao, validacao, duplicidades por curso, repeticao permitida de nomes/codigos nos escopos corretos, UUIDs e preservacao de materiais na edicao.
O teste parametrizado de exclusao percorre os quatro estados de `Material`, inclusive `REMOVIDO`, conferindo a preservacao do material e de seus metadados `Torrent` e `Magnet`. Nao cria arquivos ou transfere conteudo academico.
Os testes unitarios cobrem tambem conflitos de constraints/FK, conversao de paginas, normalizacao e recusa de transferencia antes de modificar campos. O cenario de FK concorrente e simulado em teste unitario, nao em duas transacoes simultaneas reais.
Os runners ficam desativados no perfil `test` e os dados de integracao sao revertidos ao finalizar. O schema ainda e gerenciado por `ddl-auto=update`, portanto pode ser atualizado pelo Hibernate.

Verificacao em 20/10/2026: 32 testes novos aprovados isoladamente e `./mvnw clean verify` com 138 testes no total, zero falhas, erros ou testes ignorados, e JAR executavel gerado.
O backend atualizado foi reiniciado em localhost:8080 com bootstrap administrativo desativado e credenciais de bootstrap ausentes/vazias, sem exibir valores. A carga academica criou zero disciplinas; os testes nao alteraram registros reais da Matriz 6. Nenhum comando Docker foi executado.

### Exemplo manual

Obtenha o token pelo login normal e use um curso de teste em `CURSO_ID`:

```bash
curl "http://localhost:8080/api/v1/disciplinas?cursoId=$CURSO_ID&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN_ADMIN"

curl -i -X POST 'http://localhost:8080/api/v1/disciplinas' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d "{\"nome\":\"Disciplina Exemplo\",\"codigo\":\"EX-001\",\"cursoId\":\"$CURSO_ID\"}"
```

Nao use disciplinas reais da Matriz 6 para validar edicao/exclusao. Seus codigos foram transcritos da fonte oficial; nao recodificar por semelhanca de nomes ou copiar ementas da Matriz 5.
O populador usa curso/codigo como identidade: alterar ou excluir codigos do catalogo com `SEED_ENABLED=true` pode recriar os registros de referencia em outra inicializacao. Use dados separados para teste e mantenha controle explicito do runner.
Nenhum comando Docker e executado pelo agente. Quando necessario, comandos Docker devem ser enviados ao usuario.

## Proxima entrega

CRUD de categorias, preparando o cadastro de materiais. Depois, definir os fluxos de material pendente, propriedade e associacao aos mecanismos de compartilhamento antes de implementar a API de materiais.
