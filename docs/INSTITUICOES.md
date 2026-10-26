# CRUD de instituicoes

Atualização frontend26/10/2026: cadastro exclusivo de ADMINISTRADOR integrado, preservando leitura de todos os autenticados. **1166 testes frontend**,21backend focados e2cenários opcionais SpringHTTP/PostgreSQL aprovados, com criação/409/negativos de perfil e limpeza da instituição de teste. Contrato backend inalterado; [código e execução do cadastro](../../UNESC-Share-Frontend/docs/INSTITUICAO_CADASTRO_FRONTEND.md). As entregas anteriores registradas abaixo são históricas.

Frontend integrado em26/10/2026: consulta para os três perfis e cadastro/edição/exclusão para ADMINISTRADOR. O detalhe tem link para consulta paginada dos cursos da instituição. A API/contrato não foram alterados. **1208 testes frontend**, TypeScript/build e **27 Maven de cursos focados no PostgreSQL local** aprovados; frontend HTTP sintético não integra Vue/Spring. Guias: [cursos](../../UNESC-Share-Frontend/docs/CURSOS_CONSULTA_FRONTEND.md), [exclusão](../../UNESC-Share-Frontend/docs/INSTITUICAO_EXCLUSAO_FRONTEND.md), [edição](../../UNESC-Share-Frontend/docs/INSTITUICAO_EDICAO_FRONTEND.md) e [consulta](../../UNESC-Share-Frontend/docs/INSTITUICOES_CONSULTA_FRONTEND.md). Visual/DOM/E2E pendentes sem navegador.

Este incremento inicia a Etapa 4 somente com instituicoes. Nao implementa CRUDs de cursos/disciplinas, cadastro de administrador nem compartilhamento de materiais.

## Regras aprovadas

- Usuarios autenticados, de qualquer perfil, podem listar e consultar instituicoes.
- Somente `ADMINISTRADOR` pode cadastrar, editar e excluir instituicoes.
- A exclusao e fisica apenas quando nao existem cursos vinculados; caso contrario, retorna `409 Conflict`, sem cascata.
- Essa restricao administrativa se refere a estrutura academica. Nao restringe o futuro cadastro de materiais a administradores: usuarios poderao cadastrar materiais com `.torrent` e/ou Magnet URI e gerenciar os proprios registros.
- O perfil `MODERADOR` nao pode alterar instituicoes. A moderacao de materiais e comentarios sera implementada em outra entrega.

## Estrutura e responsabilidades

Os caminhos abaixo sao relativos a raiz do repositorio. Os arquivos estao completos nesses locais:

```text
src/main/java/br/com/murkyweb/unesc_share/
├── academico/instituicao/
│   ├── controller/InstituicaoController.java
│   ├── dto/InstituicaoRequest.java
│   ├── dto/InstituicaoResponse.java
│   ├── domain/Instituicao.java
│   ├── repository/InstituicaoRepository.java
│   └── service/InstituicaoService.java
├── academico/curso/repository/CursoRepository.java
└── security/SecurityConfig.java

src/test/java/br/com/murkyweb/unesc_share/academico/instituicao/
├── InstituicaoApiTests.java
└── InstituicaoServiceTests.java
```

O controller trata HTTP e Bean Validation. O service organiza regras e transacoes e converte entidades para DTOs dentro da transacao. Os repositories usam consultas derivadas do Spring Data JPA.
As entidades nao sao expostas nem recebem objetos de cursos enviados no JSON. Nenhum campo ou relacionamento JPA foi adicionado.

O filtro nativo do Spring Security exige perfil administrativo nas rotas de escrita, antes de ler o corpo. `@PreAuthorize` no service tambem protege chamadas fora do controller.
As consultas do service exigem autenticacao. Nao ha filtro de seguranca customizado.

## Contrato HTTP

Todos os endpoints usam `Authorization: Bearer <token>`. O prefixo `/api/v1` e o context-path, nao parte do mapeamento do controller.

| Metodo | Endpoint | Acesso | Sucesso |
| --- | --- | --- | --- |
| GET | `/api/v1/instituicoes?page=0&size=20` | Autenticado | `200` |
| GET | `/api/v1/instituicoes/{id}` | Autenticado | `200` |
| POST | `/api/v1/instituicoes` | Administrador | `201`, DTO e header `Location` |
| PUT | `/api/v1/instituicoes/{id}` | Administrador | `200`, DTO atualizado |
| DELETE | `/api/v1/instituicoes/{id}` | Administrador | `204`, sem corpo |

POST e PUT recebem o mesmo corpo completo. PUT nao e atualizacao parcial e nao cria registros quando o UUID e inexistente:

```json
{
  "nome": "Universidade Exemplo",
  "sigla": "UEX"
}
```

`nome` e obrigatorio e tem limite de 180 caracteres. `sigla` e obrigatoria e tem limite de 30. Espacos externos sao removidos; a sigla e convertida para maiusculas com `Locale.ROOT`, antes da validacao.
O nome conserva a capitalizacao informada. O UUID e gerado pelo servidor e preservado na edicao.
O DTO de resposta contem somente `id`, `nome` e `sigla`.

Duplicidades de nome ou sigla sao consultadas sem distinguir maiusculas de minusculas, excluindo o proprio UUID na edicao.
As constraints unicas existentes continuam protegendo valores iguais no PostgreSQL e erros de integridade sao traduzidos para `409`, sem expor SQL.
Limitacao do prototipo: as constraints textuais atuais distinguem maiusculas de minusculas. A verificacao previa de nomes nao garante unicidade sem distinguir caixa em duas escritas concorrentes com grafias diferentes; um indice unico em `lower(nome)` exigira uma futura alteracao controlada do schema. Nenhum indice funcional ou migracao foi adicionado nesta entrega.

Na exclusao, a consulta previa verifica cursos e a FK permanece como protecao final se um curso for vinculado concorrentemente. O service executa `flush` ainda na transacao para traduzir a violacao de integridade para `409`.

### Paginacao

`page` inicia em zero e nao aceita negativos. `size` aceita de 1 a 100, com padrao 20. A ordenacao e fixa por `nome` e depois `id`, sem aceitar propriedades arbitrarias para ordenacao.
A resposta usa o [PagedModel nativo do Spring Data](https://docs.spring.io/spring-data/commons/docs/4.1.x/api/org/springframework/data/web/PagedModel.html), evitando serializar entidades ou a implementacao `PageImpl` diretamente:

```json
{
  "content": [
    { "id": "00000000-0000-0000-0000-000000000001", "nome": "Universidade Exemplo", "sigla": "UEX" }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

Pagina alem do total retorna lista vazia. Parametros invalidos retornam `400` pela validacao nativa do Spring MVC, reaproveitando `ResponseEntityExceptionHandler` e `ProblemDetail`.

### Erros

- `400`: JSON invalido, campos invalidos, UUID malformado ou paginacao invalida.
- `401`: token ausente, invalido ou expirado.
- `403`: usuario autenticado sem perfil administrativo tentando escrever.
- `404`: UUID valido, mas instituicao inexistente.
- `409`: nome/sigla duplicados ou tentativa de excluir instituicao com cursos.

Erros MVC usam `application/problem+json`. Falhas de autenticacao/autorizacao usam os handlers Bearer nativos; nao presumir corpo JSON nessas respostas.

## Executar e testar

Com PostgreSQL disponivel, execute na raiz do repositorio:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Em outro terminal, com variaveis de conexao exportadas:

```bash
./mvnw -Dtest=InstituicaoApiTests,InstituicaoServiceTests test
./mvnw clean test
```

Os testes HTTP usam PostgreSQL e a cadeia real de filtros, com tokens assinados pelo encoder nativo e validados pelo decoder nativo. Um teste percorre login administrativo, cadastro, consulta, edicao e exclusao. Os demais cobrem perfis, validacao, paginacao, duplicidades, DTOs, UUIDs e preservacao de cursos.
Os testes unitarios verificam tambem a traducao de violacoes de constraints/FK e o mapeamento da pagina. A simulacao de FK concorrente e unitaria, nao um teste de duas transacoes simultaneas.
O perfil `test` desativa o populador. Dados dos testes HTTP sao transacionais e revertidos; o administrador de teste nao permanece cadastrado. O schema ainda usa `ddl-auto=update`, portanto PostgreSQL local e necessario e sua estrutura pode ser atualizada.

Verificacao concluida em 20/10/2026: os 21 novos testes passaram isoladamente; `./mvnw clean verify` passou com 62 testes no total, sem falhas, erros ou testes ignorados, e gerou `target/unesc-share-0.0.1-SNAPSHOT.jar`.

### Teste manual

Obtenha um token pelo login documentado em [AUTENTICACAO.md](AUTENTICACAO.md). Um usuario comum pode consultar:

```bash
curl 'http://localhost:8080/api/v1/instituicoes?page=0&size=20' \
  -H "Authorization: Bearer $TOKEN"
```

As chamadas abaixo exigem token de uma conta que ja tenha perfil `ADMINISTRADOR`:

```bash
curl -i -X POST 'http://localhost:8080/api/v1/instituicoes' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Universidade Exemplo","sigla":"UEX"}'

curl -i -X PUT "http://localhost:8080/api/v1/instituicoes/$INSTITUICAO_ID" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Universidade Exemplo Atualizada","sigla":"UEXA"}'

curl -i -X DELETE "http://localhost:8080/api/v1/instituicoes/$INSTITUICAO_ID" \
  -H "Authorization: Bearer $TOKEN_ADMIN"
```

Use em `INSTITUICAO_ID` o UUID do cadastro de teste, nao registros do catalogo inicial.
O incremento seguinte adicionou o provisionamento opt-in do administrador somente em `dev`, descrito em [ADMINISTRADOR_INICIAL.md](ADMINISTRADOR_INICIAL.md). O cadastro publico continua fixando `USUARIO`; nao alterar isso para facilitar testes.
Se alterar os identificadores do catalogo inicial, considere desativar `SEED_ENABLED` em `dev`: o runner usa sigla da instituicao e nome do curso para identifica-los e pode recriar os registros de referencia em outra inicializacao.

Nenhum comando Docker e executado pelo agente; se necessario, as operacoes Docker devem ser solicitadas ao usuario.
