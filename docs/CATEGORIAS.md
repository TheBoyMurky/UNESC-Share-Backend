# CRUD de categorias

Este incremento inicia a Etapa 5 pela administracao das classificacoes de materiais. Nao implementa ainda a API de materiais nem compartilhamento por torrent/magnet.

## Regras

- Categorias sao globais, sem vinculo a instituicao, curso ou disciplina.
- Qualquer usuario autenticado pode listar e consultar.
- Somente `ADMINISTRADOR` pode cadastrar, editar e excluir. O perfil `MODERADOR` nao administra categorias.
- Nome obrigatorio, ate 80 caracteres, preservando capitalizacao e removendo espacos externos.
- Descricao opcional, ate 500 caracteres, sem espacos externos; vazia/branca vira `null`.
- Duplicidade de nome e verificada sem distinguir maiusculas de minusculas; o proprio UUID e excluido dessa verificacao na edicao.
- A edicao preserva o UUID e os materiais associados.
- Qualquer material vinculado impede a exclusao, nos estados `PENDENTE`, `PUBLICADO`, `BLOQUEADO` e `REMOVIDO`. Exclusao logica do material nao elimina sua referencia.
- Sem materiais, a categoria pode ser excluida fisicamente. Nenhum material, torrent ou magnet e excluido em cascata.

Essa restricao administrativa diz respeito a classificacao do catalogo; nao restringe o futuro cadastro de materiais pelos estudantes.
Nao foram adicionadas hierarquias de categorias, exclusao logica de categoria ou regras novas de moderacao.

## Arquivos

Caminhos relativos a raiz do repositorio; os arquivos completos estao nesses locais:

```text
src/main/java/br/com/murkyweb/unesc_share/
├── categoria/
│   ├── controller/CategoriaController.java
│   ├── dto/CategoriaRequest.java
│   ├── dto/CategoriaResponse.java
│   ├── domain/Categoria.java
│   ├── repository/CategoriaRepository.java
│   └── service/CategoriaService.java
├── material/repository/MaterialRepository.java
└── security/SecurityConfig.java

src/test/java/br/com/murkyweb/unesc_share/categoria/
├── CategoriaApiTests.java
└── CategoriaServiceTests.java
```

O controller trata HTTP e Bean Validation. O service aplica regras e transacoes e converte entidades para DTOs. Os repositories usam consultas derivadas do Spring Data JPA.
Os matchers nativos restringem escrita antes da leitura do corpo e `@PreAuthorize` no service protege chamadas fora do controller. O mecanismo JWT permanece o existente, sem filtros proprios.
Nenhuma entidade, tabela, migracao ou dependencia foi alterada.

## Contrato HTTP

Todos os endpoints exigem `Authorization: Bearer <token>`. O controller usa `/categorias`, sem duplicar o context-path `/api/v1`.

| Metodo | Endpoint | Sucesso | Acesso |
| --- | --- | --- | --- |
| GET | `/api/v1/categorias` | `200`, pagina | Autenticado |
| GET | `/api/v1/categorias/{id}` | `200`, DTO | Autenticado |
| POST | `/api/v1/categorias` | `201`, DTO e `Location` | Administrador |
| PUT | `/api/v1/categorias/{id}` | `200`, DTO atualizado | Administrador |
| DELETE | `/api/v1/categorias/{id}` | `204`, sem corpo | Administrador |

POST e PUT recebem:

```json
{
  "nome": "Categoria de Exemplo",
  "descricao": "Descricao opcional."
}
```

PUT recebe o corpo completo, nao e atualizacao parcial e nao cria registros quando o UUID e inexistente. Omitir descricao em PUT limpa esse campo.
A resposta contem apenas `id`, `nome` e `descricao`. Campos extras como `id` e `materiais` nao substituem o UUID gerado pelo servidor nem alteram entidades relacionadas.

### Listagem

```text
GET /api/v1/categorias?page=0&size=20
```

`page` inicia em zero, sem valores negativos. `size` vai de 1 a 100, com padrao 20. A ordenacao e fixa por nome e depois UUID, sem propriedades arbitrarias de ordenacao.
A resposta usa `PagedModel` nativo do Spring Data, com `content` e metadados `page` (`size`, `number`, `totalElements`, `totalPages`), seguindo os demais CRUDs.
Pagina alem do total retorna lista vazia com `200`. Nao ha filtros institucionais porque as categorias sao globais.

### Erros e integridade

- `400`: JSON, campos, UUID ou paginacao invalidos.
- `401`: token ausente, invalido ou expirado.
- `403`: perfil sem permissao administrativa tentando escrever.
- `404`: categoria inexistente por UUID.
- `409`: nome duplicado, conflito de constraint ou categoria com materiais vinculados.

Erros MVC reutilizam `ResponseEntityExceptionHandler` e `ProblemDetail`. Falhas de seguranca usam os handlers Bearer nativos, sem garantir corpo JSON.
A exclusao consulta materiais sem filtro de status; a FK e o `flush` dentro da transacao protegem tambem contra um material vinculado concorrentemente depois dessa consulta.
As falhas esperadas sao traduzidas sem expor SQL, hash, senha ou token.

Limite do prototipo: a constraint textual unica de nome distingue caixa no PostgreSQL. A verificacao previa ignora caixa, mas nao garante unicidade case-insensitive contra duas escritas simultaneas com grafias diferentes. Nenhum indice funcional foi criado nesta entrega.

## Dados iniciais

As nove categorias iniciais continuam sob responsabilidade do `CommandLineRunner` de catalogo, em `dev`, controlado por `SEED_ENABLED`.
Esse populador identifica categorias pelo nome. Se uma categoria inicial for renomeada ou excluida e o runner continuar habilitado, outra inicializacao pode recriar o nome de referencia com um novo UUID. Editar somente a descricao nao altera a identificacao.
Para administrar definitivamente esses nomes sem reposicao automatica, desative `SEED_ENABLED`. Nao foi alterado o comportamento do populador nem criado um identificador imutavel adicional para categorias iniciais.
Os testes usam categorias novas e rollback, sem renomear ou excluir as nove categorias do catalogo existente.

## Executar e testar

Com PostgreSQL disponivel, execute na raiz do repositorio:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Mantenha `ADMIN_SEED_ENABLED=false`. Nao e necessario reintroduzir credenciais de bootstrap para testes.
Em outro terminal, com as variaveis de conexao exportadas:

```bash
./mvnw -Dtest=CategoriaApiTests,CategoriaServiceTests test
./mvnw clean verify
```

Os testes HTTP usam a cadeia real de filtros, JWT assinado/validado pelos componentes nativos e PostgreSQL local. Cobrem todos os perfis, CRUD, normalizacao, limites, descricao opcional, duplicidade, paginacao/ordenacao, UUIDs, DTOs e preservacao dos materiais na edicao.
A exclusao e testada nos quatro estados de material, verificando a preservacao do material e de seus registros independentes de `Torrent` e `Magnet`, sem criar arquivos ou transferir conteudo academico.
Os testes unitarios cobrem conflitos de constraints/FK, consulta previa antes de excluir, conversao de pagina e normalizacao. O conflito de FK concorrente e simulado em teste unitario, nao em duas transacoes reais simultaneas.
Os runners ficam desativados em `test` e os registros de integracao sao revertidos. O schema continua gerenciado por `ddl-auto=update`, podendo ser atualizado pelo Hibernate.

Verificacao em 20/10/2026: os 28 testes novos passaram isoladamente e `./mvnw clean verify` passou com 166 testes no total, zero falhas, erros ou testes ignorados, gerando o JAR executavel.
O backend atualizado foi reiniciado em localhost:8080, com bootstrap administrativo desativado e credenciais de bootstrap ausentes/vazias, sem exibir valores. A carga academica informou zero novas disciplinas. Nenhuma categoria real foi editada ou excluida pelo agente, nenhum comando Docker foi executado e o `.env` real permaneceu inalterado.

### Exemplo manual

Obtenha um token pelo login normal, sem enviar credenciais ao agente. Para consulta, qualquer perfil autenticado pode usar:

```bash
curl 'http://localhost:8080/api/v1/categorias?page=0&size=20' \
  -H "Authorization: Bearer $TOKEN"
```

Para criar, use um token de administrador:

```bash
curl -i -X POST 'http://localhost:8080/api/v1/categorias' \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Categoria de Exemplo","descricao":"Criada para teste manual."}'
```

Use registros criados intencionalmente para teste, sem modificar as categorias iniciais ou as disciplinas reais da Matriz 6.
Nenhum comando Docker e executado pelo agente; quando necessario, os comandos devem ser enviados ao usuario.

## Proxima entrega

O fluxo foi aprovado e o incremento seguinte foi implementado: material e 1..10 Magnets na mesma transacao, pendencia/publicacao, controle de propriedade e remocao logica. Consulte [MATERIAIS.md](MATERIAIS.md). A API de .torrent e a moderacao completa permanecem para entregas futuras, sem hospedagem de conteudo academico.
