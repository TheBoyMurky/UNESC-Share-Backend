# Validação integrada de comentários e moderação

Atualização mais recente26/10/2026: roteiro também testa cadastro de instituição com o novo service/modelo frontend. Java reserva nome/sigla aleatórios com ausência prévia; Node valida403 paraUSUARIO/MODERADOR e criação+consulta+409 paraADMINISTRADOR. A instituição adicional é limpa pelo **par exclusivo reservado**, sem confiar no UUID de resposta; ausência conferida, além da limpeza anterior porUUID/files/root. **1166 testes frontend**,21backend focados e2ITopcionais aprovados. [Guia do cadastro administrativo](../../UNESC-Share-Frontend/docs/INSTITUICAO_CADASTRO_FRONTEND.md). Contagens e escopos anteriores abaixo são históricos.

Atualização mais recente26/10/2026: roteiro recebe o UUID da instituição da fixture existente e a consulta com o novo service/modelo frontend, como USUARIO e equipe MODERADOR/ADMINISTRADOR; confirma401 semJWT, sem percorrer catálogo real nesse trecho. **1135 testes frontend**,21backend focados e2cenários opcionais aprovados; limpeza de registros/arquivos/diretório conferida. [Guia das consultas](../../UNESC-Share-Frontend/docs/INSTITUICOES_CONSULTA_FRONTEND.md). Contagens e próximos passos anteriores abaixo são históricos.

Atualização mais recente26/10/2026: roteiro também adiciona `.torrent` sintético pelo novo modelo frontend, consulta PENDENTE, baixa e compara metainfo e publica apenas a fixture antes dos comentários. **1110 testes frontend**,79 backend focados e2cenários opcionais aprovados. IT Java configura storage temporário exclusivo, limpa registros Torrent/arquivos das fixtures porUUID, exige root vazio emAfterAll e remove o diretório; não usa storage real. Guia [da adição .torrent](../../UNESC-Share-Frontend/docs/ADICAO_TORRENT_FRONTEND.md). Contagens anteriores abaixo são históricas.

Entrega de 26/10/2026. Verificação opcional conecta o cliente HTTP, services e modelo de decisão atuais do frontend ao **Spring HTTP real e PostgreSQL**. Não usa servidor Node simulado nem navegador e não altera código de produção.

Atualização posterior26/10/2026: roteiro ampliado com o modelo de adição de Magnets. Autor adiciona uma referência a Material PUBLICADO, confere PENDENTE e descarte do draft; equipe publica somente a fixture para continuar os cenários de comentários. Dois cenários opcionais passaram novamente, junto com1077 testes frontend e54 testes backend focados em Magnets. Guia [da nova tela](../../UNESC-Share-Frontend/docs/ADICAO_MAGNETS_FRONTEND.md). Contagens da tabela abaixo registram a validação original anterior à ampliação.

## Estrutura e código completo

```text
UNESC-Share-Backend/
  src/test/java/br/com/murkyweb/unesc_share/material/ComentarioFrontendIT.java
  docs/VALIDACAO_INTEGRADA_COMENTARIOS.md
UNESC-Share-Frontend/
  tests/integration/comentarios-spring.mjs
```

O código completo está nesses dois arquivos de teste. Java reutiliza `MaterialApiSupport`, perfil `test`, `JwtEncoder`, transações e servidor nativo do Spring em `127.0.0.1` com porta aleatória. Node importa o código existente do frontend, incluindo `createHttpClient`, `createAuthenticatedClient`, `createComentarioService`, `createModeracaoComentarioService` e `createComentarioDecisaoModel`. Nenhuma biblioteca ou dependência foi acrescentada.

O nome `ComentarioFrontendIT` deixa a integração fora da seleção padrão de testes do Surefire; precisa ser escolhida por `-Dtest=ComentarioFrontendIT`. O roteiro Node fica fora de `tests/*.test.mjs` e não entra no `npm run verify`. Assim, as suítes normais de cada repositório continuam independentes. Não executar o script Node sozinho: recebe fixtures exclusivas por stdin do teste Java.

## Executar

Pré-requisitos: Java21, Node compatível com o frontend (`^22.18.0 || >=24.12.0`), os dois repositórios e PostgreSQL local já disponível. Não precisa iniciar backend ou Vite manualmente. O teste usa a configuração PostgreSQL existente e confirma fixtures temporárias para que requisições HTTP em outras threads consigam acessá-las.

```bash
cd /home/murky/Desenvolvimento/UNESC-Share-Backend
set -a
source .env
set +a

ADMIN_SEED_ENABLED=false SEED_ENABLED=false SPRING_PROFILES_ACTIVE=test \
  ./mvnw -Dtest=ComentarioFrontendIT \
  -Dspring.jpa.show-sql=false -Dlogging.level.org.hibernate.SQL=OFF test
```

Por padrão espera o frontend em `../UNESC-Share-Frontend`. Se necessário, acrescente `-Dunescshare.frontend-dir=/caminho/do/UNESC-Share-Frontend`. Essa propriedade define somente onde está o código de teste do frontend; não configura URL ou acesso a um servidor externo.

As suítes padrão continuam disponíveis separadamente:

```bash
# Backend, com ambiente exportado e PostgreSQL disponível:
ADMIN_SEED_ENABLED=false SEED_ENABLED=false SPRING_PROFILES_ACTIVE=test ./mvnw clean verify

# Frontend, no outro repositório:
cd /home/murky/Desenvolvimento/UNESC-Share-Frontend
npm run verify
```

O agente não executa comandos Docker. Se o PostgreSQL não estiver disponível, operações Docker devem ser feitas pelo usuário; não reativar bootstrap administrativo ou usar credenciais de conta real para substituir as fixtures.

## Cenários e proteção das fixtures

Executa dois cenários independentes: um com MODERADOR e outro com ADMINISTRADOR. Cada cenário cria apenas autor USUARIO, membro da equipe, estrutura acadêmica, categoria e Material PUBLICADO próprios, identificados por UUIDs gerados pelo teste.

O cliente frontend faz cadastro de comentário e verifica:

- 401 sem Bearer e403 para usuário comum na leitura/escrita da equipe, sem encerrar sessão por403.
- Preflight permitido para `http://localhost:5173`, Authorization/Content-Type e sem credenciais/cookie; origem `http://localhost:5174` negada.
- Ocultação com motivo, confirmação e nova consulta pelo modelo do frontend; texto some da consulta comum.
- Reexibição e retorno à consulta comum; eventos ATIVO→OCULTO e OCULTO→ATIVO com motivo e responsável corretos, sem texto do comentário no DTO de histórico.
- Repetição inválida retorna409 e não acrescenta evento.
- Equipe pode ocultar/reexibir seu próprio comentário, nos dois perfis.
- Material bloqueado após a leitura de PUBLICADO: backend retorna409 para reexibição, modelo descarta alvo/motivo e exige nova preparação, sem atribuir sucesso.
- Remoção lógica pelo autor de comentário conhecido em Material privado; histórico selecionado continua sem texto, lista da equipe deixa de mostrar o removido e não há restauração por reexibição.

JWTs são assinados pela configuração **exclusiva de teste**, com usuários temporários existentes no PostgreSQL. Não são tokens falsos aceitos por um servidor simulado e não usam a chave real do `.env`. São enviados ao subprocesso por stdin, sem argumentos de linha de comando ou arquivos. O ambiente do Node é reduzido a PATH, sem herdar `.env`, credenciais PostgreSQL ou JWT_SECRET reais. Payloads, tokens e respostas não são impressos; o roteiro emite apenas sucesso ou um marcador fixo de etapa.

O script restringe a URL recebida a `http://127.0.0.1:<porta>/api/v1`. Requisições têm timeout; Java limita o subprocesso e o encerra se necessário. `finally` remove somente histórico/comentários/mecanismos/Material/estrutura/contas das fixtures, filhos primeiro e por UUID exato. A limpeza também é tentada se o encerramento do processo falhar; confirma ausência de Material, contas, comentários e eventos. Não há deleteAll, cascata de remoção ou uso de dados reais nesse roteiro.

Não torna o teste resistente a queda do computador ou encerramento forçado da JVM; `finally` não executa nessas situações. Uma falha de limpeza é falha do teste. Usar a configuração local de desenvolvimento, não apontar esse comando para produção.

## Resultados executados

Em26/10/2026:

| Verificação | Resultado |
| --- | --- |
| Backend `./mvnw clean verify`, antes de acrescentar o IT | 551 testes, zero falhas/erros/ignorados, JAR gerado |
| `ComentarioFrontendIT`, após ajuste final da limpeza | 2 cenários, zero falhas/erros/ignorados |
| Frontend `npm run verify` | 1044 testes, zero falhas/cancelados/ignorados, TypeScript/build aprovados |
| Configuração local, sem expor valores | URL pública padrão `/api/v1`, origem5173 autorizada, bootstrap administrativo desativado |

Os dois cenários opcionais são adicionais aos551 da suíte padrão, não incluídos nessa contagem. As validações backend existentes incluem rollback real de status e auditoria após falhas de persistência; passaram na suíte completa. O novo roteiro verifica HTTP entre runtimes e decisões sequenciais confirmadas no PostgreSQL; não prova duas requisições concorrentes, ausência de races ou revisão da mesma versão.

Servidores de teste e subprocessos foram encerrados. Sem alterações em Java de produção, schema, dependências, permissões, `.env` real, dados reais, Docker, deploy ou operações Git. Foi iniciado apenas o servidor embutido dos testes, sem nova instância dev persistente.

## Limites e próxima entrega

Node não monta componentes Vue nem aplica as regras CORS de um navegador. O teste confere a resposta do preflight, mas não substitui abrir a aplicação. Inventário da sessão sem navegador: visual desktop/mobile, DOM, teclado/foco, sessionStorage, expiração durante interação e E2E Vue continuam pendentes. Login com senha e renderização escapada de HTML também não são exercitados pelo roteiro, que usa JWTs de teste e compara texto JSON.

O roteiro manual de comentários está no frontend em `docs/REEXIBICAO_COMENTARIO_FRONTEND.md`. Deve ser executado quando houver navegador disponível; não marcar essa pendência como concluída por causa dos testes Node/Spring.

Adições de Magnets/`.torrent`, consulta, cadastro e edição de instituições foram implementados no frontend. A edição de instituição usa GET/PUT existente e não alterou contrato backend; a suíte frontend sintética passou, mas Spring/PostgreSQL não foi repetido nesta entrega. Próximo recomendado: exclusão de instituições somente ADMINISTRADOR, preservando409 para cursos vinculados e sem cascata. Leitura permanece para os três perfis. Edição/exclusão de mecanismos exigem discussão de contrato antes de implementação.
