# Instrucoes do projeto para agentes Codex

## Premissa central

O UnescShare e um catalogo academico baseado em compartilhamento BitTorrent. Nao e um servico tradicional de hospedagem de conteudos.

- Nunca implementar upload direto de PDF, EPUB, MP4, ZIP ou do conteudo academico.
- Aceitar como mecanismos de compartilhamento somente arquivos `.torrent` e Magnet URIs.
- `Material` representa a catalogacao e os metadados do conteudo.
- `Torrent` e `Magnet` sao entidades independentes e nao devem ser reduzidos a atributos de `Material`.
- Um `Material` pode possuir varios `Torrent` e varios `Magnet`.

## Stack e decisoes vigentes

- Java 21 e Spring Boot.
- API REST com prefixo `/api/v1`.
- Autenticacao JWT via recursos nativos do Spring Security, sem filtros JWT proprios. Tokens Bearer em `Authorization`, validade padrao de 15 minutos (configuravel de 1 a 30), HS256 com chave aleatoria Base64 de pelo menos 32 bytes em `JWT_SECRET`, sem chave padrao, sem refresh token e sem revogacao imediata.
- API stateless, sem cookies de autenticacao, sessao HTTP, form login ou Basic. A desativacao de CSRF depende dessa premissa; reavaliar caso autenticacao por cookie seja introduzida.
- Cadastro publico fixa perfil `USUARIO`, normaliza email e usa `PasswordEncoder` com BCrypt. Nunca retornar senha/hash nem registrar credenciais, tokens, chaves ou valores SQL sensiveis.
- Spring Data JPA/Hibernate.
- PostgreSQL.
- UUID em todas as entidades.
- `spring.jpa.hibernate.ddl-auto=update` durante o prototipo.
- Sem Flyway inicialmente.
- `CommandLineRunner` idempotente e restrito ao perfil `dev` para dados iniciais, quando as entidades necessarias estiverem prontas.
- PostgreSQL local via Docker Compose.
- Configuracao local via `.env`; nunca versionar o `.env` real.
- Frontend Vue 3, Vite e TypeScript mantido em outro repositorio.
- Organizacao Java por funcionalidade.
- Datas persistidas como `Instant`/UTC.
- Enums persistidos como texto.
- Relacionamentos JPA carregados como `LAZY` por padrao.
- Nao expor entidades JPA diretamente pela API; usar DTOs.
- Priorizar recursos nativos do Spring Security e Spring Web para autenticacao, autorizacao, integracao HTTP e tratamento de erros, evitando reimplementar mecanismos ja oferecidos pelo framework.
- Adicionar codigo customizado somente quando necessario para as regras do projeto; documentar a justificativa e acompanhar com testes.
- Evitar `@Data` do Lombok em entidades.
- Evitar cascatas de remocao indiscriminadas.

## Forma de desenvolvimento

- Implementar incrementalmente, sem tentar concluir todo o sistema em uma unica etapa.
- Antes de fixar uma decisao arquitetural ainda indefinida, apresentar opcoes e impactos ao usuario.
- Preservar separacao entre controller, service, repository, DTO e entidade.
- Acompanhar mudancas com testes proporcionais ao risco.
- Nao executar comandos Docker, nem solicitar sudo ou elevacao para acessar o Docker. Sempre que informacoes ou operacoes Docker forem necessarias, enviar o comando ao usuario e aguardar o resultado fornecido por ele.
- O usuario autoriza executar comandos Maven, incluindo iniciar o backend e testar contra o PostgreSQL local. A restricao de execucao pelo agente se aplica somente aos comandos Docker; respeitar as permissoes do ambiente para os demais comandos.
- Manter `README.md` e `docs/CONTEXTO_PROJETO.md` atualizados quando uma decisao relevante mudar.

## Estado atual

A Etapa 1 e as Etapas 2.1, 2.2 e 2.3 foram implementadas. Existem a configuracao inicial e as entidades/repositories de:

- Usuario;
- Instituicao;
- Curso;
- Disciplina;
- Categoria;
- Material;
- Torrent;
- Magnet;
- Comentario;
- Avaliacao;
- Denuncia.

Os estados de `Material` sao `PENDENTE`, `PUBLICADO`, `BLOQUEADO` e `REMOVIDO`.

A Etapa 2.4 possui populador transacional, testes de persistencia e de idempotencia.
O runner executa apenas em `dev` e pode ser desativado com `SEED_ENABLED=false`.
O catalogo ativo usa a Matriz 6 de `docs/GRADE 6.pdf`: Universidade do Extremo Sul Catarinense (UNESC), Ciencia da Computacao, 59 disciplinas com codigos oficiais (48 componentes semestrais e 11 eletivas) e nove categorias.
O recurso JSON e lido pelo Jackson e validado por Bean Validation. Nao copiar ementas da Matriz 5: novas disciplinas tem descricao nula, pois a Matriz 6 nao traz ementas.
O populador identifica disciplinas por curso/codigo, preservando UUIDs e edicoes existentes. Nao usar equivalencia por nome para recodificar disciplinas de outra matriz.
O runner bloqueia a carga quando encontrar `TEMP-CC-*` no curso; nao faz exclusoes automaticamente.
Nao foram criados campos de fase ou creditos nem usuarios administrativos nesta etapa.
A Etapa 3 possui o primeiro incremento: cadastro, login JWT e consulta do usuario autenticado via DTOs.
Controllers e matchers sao relativos ao context-path `/api/v1`; nao repetir esse prefixo.
Usar `ResponseEntityExceptionHandler`/`ProblemDetail` para erros MVC e os handlers Bearer nativos para falhas de seguranca.
As permissoes sao convertidas da claim `roles`. O usuario aprovou consultas da estrutura academica para autenticados e escrita apenas para `ADMINISTRADOR`; essa restricao nao se aplica ao futuro cadastro de materiais por estudantes.
O primeiro incremento da Etapa 4 implementa instituicoes em `/instituicoes`: GET paginado/por UUID, POST, PUT por UUID e DELETE por UUID. Escritas protegidas por matchers nativos e `@PreAuthorize` no service.
A exclusao de instituicao com cursos vinculados retorna 409, sem cascata. Nome/sigla possuem verificacao previa de duplicidade sem distinguir caixa; constraints textuais existentes ainda distinguem caixa no banco. Nao alegar garantia de unicidade case-insensitive sob concorrencia para o nome.
Listagem usa `PagedModel` nativo, page >= 0, size entre 1 e 100 (padrao 20) e ordem fixa nome/id. DTOs nao expoem cursos. Detalhes em `docs/INSTITUICOES.md`.
Em 20/10/2026, `./mvnw clean verify` passou com 62 testes, zero falhas/erros/ignorados e geracao do JAR. Os 21 novos testes de instituicoes incluem HTTP com filtros/JWT reais, PostgreSQL e rollback, alem de testes unitarios. Nao foram executados comandos Docker nem criado administrador persistente.
O provisionamento administrativo usa `AdministradorInicialRunner` e service restritos a `dev`, com opt-in `ADMIN_SEED_ENABLED=true`, independente de `SEED_ENABLED`. Credenciais por `ADMIN_NAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, sem defaults e sem editar o `.env` real.
Cria somente nova conta. Se o email ja for administrador, preserva nome/UUID/hash/perfil/data; se for usuario/moderador, falha sem promover ou trocar credenciais. Alterar senha no ambiente nao redefine senha existente.
Reutiliza `CadastroRequest`, Bean Validation e `PasswordEncoder`. Validacao do bootstrap gera mensagem fixa sem valores rejeitados; `toString` das propriedades e redigido. O logger `org.hibernate.orm.jdbc.error` fica OFF para nao expor valores nas mensagens JDBC.
Testes de integracao do administrador usam dev/test com ambos os runners automaticos explicitamente desativados e rollback. Em 20/10/2026, apos o usuario preencher o `.env` e autorizar a inicializacao real pelo Maven, o runner criou o administrador configurado. Login HTTP 200, `/usuarios/me` com perfil ADMINISTRADOR e consulta de instituicoes HTTP 200 foram conferidos sem expor identidade, senha, hash ou token. POST de instituicao com corpo vazio retornou 400 apos autorizacao, sem criar instituicoes. O catalogo criou zero novas disciplinas. O agente nao editou o `.env` e nao executou Docker. Desativar `ADMIN_SEED_ENABLED` e retirar as credenciais de bootstrap antes das proximas inicializacoes, conforme `docs/ADMINISTRADOR_INICIAL.md`.
Validacao mais recente em 20/10/2026: `./mvnw clean verify` passou com 79 testes, zero falhas/erros/ignorados e JAR gerado, incluindo os 17 novos testes de provisionamento administrativo. Nenhum comando Docker foi executado.
Ainda nao existem refresh token, revogacao individual ou rate limiting.
O segundo incremento da Etapa 4 implementa cursos em `/cursos`: consultas para autenticados e escrita apenas para ADMINISTRADOR, com DTOs, PagedModel e filtro opcional `instituicaoId`.
Curso exige instituicao existente no cadastro; nome e unico por instituicao conforme verificacao previa sem distinguir caixa e constraint textual ja existente. Nao alegar garantia case-insensitive sob concorrencia.
PUT exige o mesmo `instituicaoId` atual; transferencia retorna 409 e fica fora deste incremento. DELETE de curso com disciplinas retorna 409, sem cascata. Nenhuma entidade/schema foi alterado. Contrato em `docs/CURSOS.md`.
O usuario informou que desativou o bootstrap e retirou as credenciais locais apos a criacao real. Nao reativar o administrador inicial nem solicitar novamente sua senha para testar CRUDs; usar os testes transacionais e JWTs exclusivos de teste.
Na verificacao de cursos em 20/10/2026, a flag desativada e a ausencia de valores de credenciais de bootstrap foram confirmadas sem exibir segredos. `./mvnw clean verify` passou com 106 testes, zero falhas/erros/ignorados e JAR gerado; os 27 testes novos de cursos passaram tambem isoladamente. A instancia Maven anterior foi encerrada e a versao atualizada iniciou em localhost:8080, sem carga administrativa e com zero novas disciplinas. Nenhum comando Docker foi executado; o `.env` real nao foi alterado pelo agente.
O terceiro incremento da Etapa 4 implementa disciplinas em `/disciplinas`: GETs para autenticados, escritas para ADMINISTRADOR, DTOs, PagedModel e filtro opcional `cursoId`.
Codigo obrigatorio de ate 30 caracteres, normalizado com strip/Locale.ROOT para maiusculas, unico por curso conforme consulta previa sem distinguir caixa e constraint existente. Nao limitar a API aos cinco digitos da Matriz 6 nem alegar garantia case-insensitive para escritas externas concorrentes.
PUT permite corrigir nome/descricao/codigo preservando UUID e materiais, mas exige o curso atual; transferencia retorna 409. DELETE com qualquer material, inclusive REMOVIDO, retorna 409; nunca excluir em cascata. Nome pode repetir quando o codigo for diferente. Nenhuma entidade/schema foi alterado; guia em `docs/DISCIPLINAS.md`.
Na verificacao de disciplinas em 20/10/2026, os 32 novos testes passaram isoladamente e `./mvnw clean verify` passou com 138 testes, zero falhas/erros/ignorados e JAR gerado. Inclui os quatro estados de material e preservacao de Torrent/Magnet. O backend atualizado iniciou em localhost:8080, com bootstrap administrativo desativado, credenciais de bootstrap ausentes/vazias e zero novas disciplinas na carga academica. Nenhum comando Docker foi executado nem registro real da Matriz 6 foi alterado pelo agente.
A proxima entrega e o CRUD de categorias, preparando os materiais. Definir fluxos de material pendente, propriedade e associacao a Torrent/Magnet antes de implementar a API de materiais. Nao usar credenciais padrao nem liberar perfil administrativo no cadastro publico.
O usuario autorizou remover os registros antigos da Matriz 5 e manter apenas as disciplinas da Matriz 6. O codigo/recurso foi atualizado e a limpeza foi confirmada pela saida enviada pelo usuario: 44 removidas, zero temporarias restantes e COMMIT. Em 06/10/2026, o agente iniciou o backend pelo Maven: a primeira carga criou 59 disciplinas e a segunda criou zero, confirmando a idempotencia. `./mvnw clean test` passou com 41 testes, zero falhas, erros ou testes ignorados, contra o PostgreSQL local. A configuracao JWT do `.env` foi aceita sem expor os valores.
Conferir `docs/ANALISE_GRADE_6.md`: 48 componentes semestrais, 11 eletivas e 59 codigos distintos. Nao assumir equivalencias ou substituir codigos/ementas da Matriz 5 automaticamente.
Nao ampliar o modelo para manter duas matrizes nesta entrega. Antes de excluir, identificar os registros exatos e verificar materiais vinculados; nao excluir materiais em cascata nem dados de outros cursos. Preservar instituicao, curso e seus UUIDs.
Os 44 UUIDs antigos foram informados pelo usuario, todos sem materiais vinculados. `docs/sql/remover-matriz5-local.sql` limita a exclusao a esse manifesto e ao curso `78842494-b345-4da7-b483-72727863c327`, revalida vinculos e interrompe a transacao se houver divergencias.
O usuario dispensou expressamente o backup nesta substituicao local, por considerar os dados descartaveis. Nao exigir backup novamente para essa operacao nem ampliar o escopo de exclusao.
Procedimento e verificacao estao em `docs/ATUALIZACAO_MATRIZ_6.md`. Afirmar mudancas no banco somente com evidencia de execucao: resultados enviados pelo usuario ou verificacoes autorizadas pelo Maven, nunca inferindo sucesso apenas da alteracao do codigo.
