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
As permissoes sao convertidas da claim `roles`; a matriz de acesso das proximas funcionalidades ainda precisa ser aprovada.
Ainda nao existe populador de administrador, refresh token, revogacao individual ou rate limiting.
A proxima entrega e definir permissoes e implementar incrementalmente os CRUDs academicos.
O usuario autorizou remover os registros antigos da Matriz 5 e manter apenas as disciplinas da Matriz 6. O codigo/recurso foi atualizado e a limpeza foi confirmada pela saida enviada pelo usuario: 44 removidas, zero temporarias restantes e COMMIT. Em 06/10/2026, o agente iniciou o backend pelo Maven: a primeira carga criou 59 disciplinas e a segunda criou zero, confirmando a idempotencia. `./mvnw clean test` passou com 41 testes, zero falhas, erros ou testes ignorados, contra o PostgreSQL local. A configuracao JWT do `.env` foi aceita sem expor os valores.
Conferir `docs/ANALISE_GRADE_6.md`: 48 componentes semestrais, 11 eletivas e 59 codigos distintos. Nao assumir equivalencias ou substituir codigos/ementas da Matriz 5 automaticamente.
Nao ampliar o modelo para manter duas matrizes nesta entrega. Antes de excluir, identificar os registros exatos e verificar materiais vinculados; nao excluir materiais em cascata nem dados de outros cursos. Preservar instituicao, curso e seus UUIDs.
Os 44 UUIDs antigos foram informados pelo usuario, todos sem materiais vinculados. `docs/sql/remover-matriz5-local.sql` limita a exclusao a esse manifesto e ao curso `78842494-b345-4da7-b483-72727863c327`, revalida vinculos e interrompe a transacao se houver divergencias.
O usuario dispensou expressamente o backup nesta substituicao local, por considerar os dados descartaveis. Nao exigir backup novamente para essa operacao nem ampliar o escopo de exclusao.
Procedimento e verificacao estao em `docs/ATUALIZACAO_MATRIZ_6.md`. Afirmar mudancas no banco somente com evidencia de execucao: resultados enviados pelo usuario ou verificacoes autorizadas pelo Maven, nunca inferindo sucesso apenas da alteracao do codigo.
