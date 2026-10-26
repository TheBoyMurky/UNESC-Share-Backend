# Integracao HTTP com o frontend

Contrato consolidado do backend existente para o frontend Vue 3/Vite/TypeScript, mantido em outro repositorio. Este guia nao cria telas, endpoints, cookies de autenticacao ou um novo cliente HTTP no backend. Os contratos detalhados de cada funcionalidade continuam nos documentos referenciados abaixo.

Durante desenvolvimento local, a especificacao OpenAPI e Swagger UI ficam disponiveis apenas com o perfil `dev`. Ver [OpenAPI e Swagger UI](SWAGGER.md) para URLs, autenticacao Bearer e configuracao.

O UnescShare cataloga conteudos distribuidos pela rede BitTorrent. O servidor recebe somente metadados, Magnet URIs e arquivos `.torrent`, nunca PDF, EPUB, MP4, ZIP ou outros conteudos academicos.

## 1. Ambiente e CORS

No `.env` local do repositorio do frontend, pode-se configurar a URL publica da API:

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

No `.env` local do backend, `CORS_ALLOWED_ORIGINS` aceita as origens autorizadas separadas por virgula. O padrao e `http://localhost:5173`. Origem inclui protocolo, host e porta, sem caminho ou barra final; `http://127.0.0.1:5173` e outra origem e precisa ser autorizada explicitamente se utilizada. Reexporte as variaveis e reinicie o backend depois de alterar sua configuracao.

Variaveis `VITE_*` sao publicas no aplicativo compilado. Nunca copiar `JWT_SECRET`, credenciais PostgreSQL ou credenciais administrativas para o frontend, seus logs ou seu repositorio.

Configuracao CORS atual, pelo recurso nativo do Spring:

| Configuracao | Valor |
| --- | --- |
| Origens | Somente as configuradas em `CORS_ALLOWED_ORIGINS` |
| Metodos | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| Cabecalhos de requisicao autorizados | Authorization, Content-Type |
| Cabecalhos de resposta expostos ao JavaScript | Location, Content-Disposition |
| Credenciais CORS | Desativadas; nao enviar cookies de autenticacao |
| Cache do preflight | 3600 segundos |

Permitir um metodo no CORS nao cria uma rota com esse metodo: por exemplo, nao existe PATCH nas funcionalidades atuais. O navegador realiza o preflight automaticamente; o cliente nao precisa enviar OPTIONS manualmente.

`Access-Control-Expose-Headers` permite ler os dois cabecalhos de resposta com `response.headers.get(...)`. Nao envia esses cabecalhos quando a operacao nao os produz, nao libera download privado e nao substitui autenticacao/autorizacao. Nao confundir com `Access-Control-Allow-Headers`, que trata cabecalhos da requisicao. Origens nao autorizadas continuam bloqueadas, inclusive a origem literal `null`.

## 2. Autenticacao

Todas as rotas abaixo sao relativas a `/api/v1`. Nao duplicar esse prefixo ao compor a URL.

| Metodo e rota | Corpo / resultado |
| --- | --- |
| POST `/auth/cadastro` | `{ "nome": "Estudante", "email": "aluno@example.com", "senha": "senha-escolhida" }`; 201 com UsuarioResponse, perfil sempre USUARIO |
| POST `/auth/login` | `{ "email": "aluno@example.com", "senha": "senha-escolhida" }`; 200 com TokenResponse |
| GET `/usuarios/me` | Autenticado; 200 com UsuarioResponse |
| PUT `/usuarios/me` | Autenticado; `{ "nome": "Nome", "email": "novo@example.com", "senhaAtual": "..." }`; atualiza nome/e-mail; `senhaAtual` obrigatoria se o e-mail mudar; 200 UsuarioResponse |
| PUT `/usuarios/me/senha` | Autenticado; `{ "senhaAtual": "...", "novaSenha": "..." }`; troca hash; 204 sem corpo |

TokenResponse possui `accessToken`, `tokenType` (`Bearer`), `expiresIn` em segundos e `expiresAt` em ISO-8601/UTC. UsuarioResponse possui `id`, `nome`, `email`, `perfil` e `dataCadastro`; nunca senha/hash.

O e-mail e normalizado e muda imediatamente neste prototipo, sem verificacao por mensagem. Troca de senha exige senha atual e nova senha conforme os limites do cadastro. JWTs ja emitidos nao sao revogados; apos trocar senha, o frontend deve limpar a sessao local e pedir novo login. Consulte [Perfil do usuario](PERFIL_USUARIO.md) para erros, validacoes e detalhes.

Enviar o token nas requisicoes protegidas:

```http
Authorization: Bearer <accessToken>
```

Nao enviar tokens por query string, corpo ou cookie. O token dura 15 minutos por padrao, configuravel no backend entre 1 e 30 minutos, sem refresh token nem revogacao individual imediata. Ao expirar, solicitar novo login; logout no cliente descarta sua copia, mas nao invalida uma copia ja emitida. Evitar repeticao automatica infinita de requisicoes apos 401.

Em 22/10/2026, o usuario escolheu sessionStorage para manter acesso ao atualizar a aba e aprovou Vue Router no frontend separado. Salvar somente token/expiracao, nunca senha/perfil/dados pessoais; validar novamente em /usuarios/me ao restaurar. sessionStorage continua exposto a JavaScript/XSS, sem garantia de isolamento forte entre abas duplicadas/restauradas. Nao confundir logout local com revogacao do token. Os exemplos abaixo recebem token como argumento e nao implementam esse armazenamento. Nunca registrar tokens, senhas ou respostas de login. Permissoes da interface sao conveniencia; backend continua autoridade. Guia do incremento em ../UNESC-Share-Frontend/docs/AUTENTICACAO_FRONTEND.md (relativo a raiz do backend).

## 3. Permissoes e visibilidade

Salvo cadastro/login, as rotas de negocio exigem autenticacao, inclusive a busca de materiais PUBLICADO. Nao existe busca anonima neste incremento.

| Recurso | Leitura | Escrita |
| --- | --- | --- |
| Instituicoes, cursos, disciplinas, categorias | Qualquer autenticado | Somente ADMINISTRADOR |
| Material | Busca geral somente PUBLICADO; detalhe privado apenas autor/equipe | Cadastro por autenticados; edicao/remocao apenas autor; publicacao/bloqueio pela equipe |
| Torrent / Magnet | Mecanismos no detalhe; download com a visibilidade do Material | Autor adiciona apenas em PENDENTE/PUBLICADO; adicao retorna a PENDENTE |
| Comentario comum | Somente ATIVO em Material PUBLICADO, sem excecao para equipe/autor | Cadastro em PUBLICADO; edicao pelo autor apenas ATIVO/PUBLICADO; remocao logica pelo autor em qualquer estado |
| Avaliacao | Propria nota/resumo apenas em PUBLICADO | Criar/editar propria nota em PUBLICADO de outro usuario; excluir fisicamente propria nota em qualquer estado do Material |
| Denuncia propria | Somente denunciante, mesmo apos Material deixar de PUBLICADO | Criar em PUBLICADO; sem edicao/exclusao/cancelamento |
| Moderacao de comentarios/denuncias | MODERADOR ou ADMINISTRADOR | Acoes especificas com motivo/justificativa e historico |

Equipe significa MODERADOR ou ADMINISTRADOR. Ela nao pode impersonar o autor para editar/remover materiais ou comentarios de terceiros pelas rotas do autor, nem escolher a avaliacao de outro usuario.

Nas acoes de moderacao de denuncia, denunciante e autor do Material sao impedidos, inclusive ADMINISTRADOR (403). A equipe ainda pode consultar a denuncia quando impedida de decidir. Concluir denuncia nao bloqueia Material automaticamente.

Em25/10/2026 o usuário escolheu manter a autoaprovação de Material para **MODERADOR e ADMINISTRADOR**, inclusive material próprio. A regra existente da API foi preservada e recebeu regressão explícita para ambos. Não estender a regra específica de conflito de denúncia para Material. Não há desbloqueio/restauração ou controle de versão/ETag de aprovação.

## 4. Paginas, filtros e DTOs

Listagens paginadas usam `page` a partir de zero e `size` de 1 a 100 (padrao 20). Resposta com `PagedModel` nativo, por exemplo uma consulta sem resultados:

```json
{
  "content": [],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

Nao esperar `items`, `data`, `results` ou metadados no nivel raiz. A ordenacao e fixa: nome/id ASC na estrutura academica/categorias; dataCadastro/id DESC nos materiais; dataCadastro/id ASC em comentarios, denuncias e historicos. O parametro `sort` nao escolhe uma ordenacao diferente.

Filtros existentes:

- `/cursos`: `instituicaoId` opcional.
- `/disciplinas`: `cursoId` opcional.
- `/materiais`, `/materiais/meus`, `/materiais/moderacao`: `q` (ate 200 caracteres), `instituicaoId`, `cursoId`, `disciplinaId`, `categoriaId`. Filtros combinados, nao uma busca automatica por Info Hash.
- `/materiais/meus`: `status` opcional; sem ele, todos os estados do proprio usuario.
- `/materiais/moderacao`: `status`, padrao PENDENTE.
- `/denuncias/moderacao`: `status`, padrao PENDENTE; um estado por consulta.

UUIDs sao strings; enums sao os nomes em maiusculas; datas sao strings ISO-8601/UTC. MaterialResumoResponse possui `id`, `titulo`, `status`, `dataCadastro`, `visualizacoes`, `usuarioId`, `disciplinaId`, `categoriaId`. O detalhe acrescenta `descricao`, `magnets` e `torrents`, sem objetos aninhados de usuario/disciplina/categoria. Buscar nomes nos endpoints correspondentes; nao esperar nome/email do autor no Material.

MagnetResponse: `id`, `magnetUri`, `infoHash`, `dataCadastro`. TorrentResponse: `id`, `nomeArquivo`, `infoHash`, `tamanho`, `dataCadastro`. `tamanho` e o tamanho do metainfo `.torrent` em bytes, nao o tamanho do conteudo academico. Nenhum DTO expoe o caminho privado de armazenamento.

## 5. Materiais e mecanismos

| Metodo e rota | Contrato |
| --- | --- |
| GET `/materiais` | Pagina de PUBLICADO |
| GET `/materiais/meus` | Pagina do proprio autor |
| GET `/materiais/moderacao` | Fila da equipe |
| GET `/materiais/{id}` | Detalhe conforme visibilidade |
| POST `/materiais` | JSON com Magnets ou multipart com um Torrent; 201, detalhe e Location |
| PUT `/materiais/{id}` | JSON com titulo/descricao/disciplinaId/categoriaId; 200, detalhe em PENDENTE |
| DELETE `/materiais/{id}` | Remocao logica; 204, inclusive repeticao |
| POST `/materiais/{id}/publicacao` ou `/bloqueio` | Sem corpo obrigatorio; 200, detalhe atualizado |
| POST `/materiais/{id}/magnets` | JSON `{ "magnetUris": [...] }`; 200, detalhe em PENDENTE |
| POST `/materiais/{id}/torrents` | Multipart, somente campo arquivo; 201, TorrentResponse e Location do download |
| GET `/materiais/{id}/torrents/{torrentId}` | Download autenticado; bytes application/x-bittorrent |

Cadastro JSON completo, usando UUIDs existentes em lugar dos placeholders:

```json
{
  "titulo": "Lista de exercicios autorizada",
  "descricao": "Material compartilhado com autorizacao do titular.",
  "disciplinaId": "<UUID da disciplina>",
  "categoriaId": "<UUID da categoria>",
  "magnetUris": ["magnet:?xt=urn:btih:<40 caracteres hexadecimais do hash v1>"]
}
```

Titulo obrigatorio ate 200 caracteres; descricao obrigatoria ate 4000. Cadastro exige 1..10 Magnets ou um `.torrent`: nao existe cadastro HTTP de Material sem mecanismo. Autor/estado/data/contador sao definidos pelo backend, nao pelo formulario. Limites acumulados independentes: 10 Magnets e 10 Torrents por Material. Hash repetido no mesmo tipo retorna409; Torrent e Magnet de igual hash somente no mesmo Material. Material REMOVIDO preserva referencias e continua participando da verificacao de duplicidade.

Cadastro com `.torrent` no navegador, com metadados tipados como JSON:

```typescript
async function cadastrarComTorrent(
  baseUrl: string,
  token: string,
  material: { titulo: string; descricao: string; disciplinaId: string; categoriaId: string },
  arquivo: File,
): Promise<Response> {
  const body = new FormData();
  body.append('material', new Blob([JSON.stringify(material)], { type: 'application/json' }));
  body.append('arquivo', arquivo);
  return fetch(`${baseUrl}/materiais`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    credentials: 'omit',
    body,
  });
}
```

Nao definir `Content-Type` manualmente ao enviar FormData: o navegador precisa gerar o boundary. Sao exatamente duas partes no cadastro (`material` e `arquivo`), uma de cada. Para adicionar a Material existente, enviar apenas `arquivo` para a rota de Torrents. JSON/mecanismos mistos/lotes de arquivos nao fazem parte do cadastro multipart atual. O exemplo retorna Response para o chamador tratar status/erros antes de consumir JSON ou Location.

Somente `.torrent` v1, ate 2 MiB por arquivo e 3 MiB por requisicao multipart; v2/hibrido em arquivo e rejeitado. Magnets aceitam v1 ou v2 SHA-256, ate 4000 caracteres/64 parametros; multiplos `xt` nao sao suportados. Arquivo academico disfarcado de `.torrent` nao e permitido; extensao/MIME nao substituem validacao do backend.

Download precisa de fetch com Authorization (um link comum nao acrescenta esse header). Exemplo orientativo com nome seguro derivado do UUID, mesmo que um cabecalho esteja ausente:

```typescript
async function baixarTorrent(baseUrl: string, token: string, materialId: string, torrentId: string): Promise<void> {
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!uuid.test(materialId) || !uuid.test(torrentId)) throw new Error('UUID invalido.');
  const response = await fetch(`${baseUrl}/materiais/${materialId}/torrents/${torrentId}`, {
    headers: { Authorization: `Bearer ${token}` },
    credentials: 'omit',
  });
  if (!response.ok) throw new Error(`Download nao realizado (HTTP ${response.status}).`);
  // Disponivel ao JavaScript via CORS; o nome atual e sempre <torrentId>.torrent.
  const disposition = response.headers.get('Content-Disposition');
  const nome = disposition?.match(/^attachment; filename="([0-9a-f-]+\.torrent)"$/i)?.[1];
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = nome === `${torrentId}.torrent` ? nome : `${torrentId}.torrent`;
  document.body.append(link);
  try { link.click(); }
  finally {
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
```

Resposta de download inclui Content-Disposition attachment, Cache-Control no-store e X-Content-Type-Options nosniff. Nao colocar JWT na URL para contornar o fetch. A abertura/copia de Magnet e uma acao explicita do usuario no cliente; suporte ao protocolo depende do navegador/cliente BitTorrent instalado. O backend nao baixa conteudo nem verifica disponibilidade de seeds.

## 6. Interacoes e moderacao

Prefixo `M = /materiais/{materialId}` na tabela:

| Metodo e rota | Corpo / sucesso |
| --- | --- |
| GET/POST `M/comentarios` | GET pagina; POST `{ "conteudo": "texto" }`, 201 sem Location individual |
| PUT/DELETE `M/comentarios/{comentarioId}` | PUT mesmo corpo, 200; DELETE logico, 204 |
| GET `M/comentarios/moderacao` | Pagina ATIVO/OCULTO; equipe |
| POST `M/comentarios/{comentarioId}/ocultacao` ou `/reexibicao` | `{ "motivo": "texto" }`, 200; equipe |
| GET `M/comentarios/{comentarioId}/historico-moderacao` | Pagina de decisoes; equipe |
| POST `M/avaliacoes` | `{ "nota": 4.75 }`, 201 e Location de /minha |
| GET/PUT/DELETE `M/avaliacoes/minha` | GET/PUT 200; PUT mesmo corpo de nota; DELETE fisico 204, inclusive ausencia |
| GET `M/avaliacoes/resumo` | 200, `{ "media": 4.75, "quantidade": 1 }`; sem notas: media null, quantidade 0 |
| POST `M/denuncias` | `{ "motivo": "SPAM", "descricao": "texto" }`, 201 e Location de /denuncias/{id} |
| GET `/denuncias/minhas` ou `/denuncias/{id}` | Pagina propria ou detalhe proprio |
| GET `/denuncias/moderacao` ou `/{id}` ou `/{id}/historico` | Fila, detalhe ou pagina de eventos; equipe |
| POST `/denuncias/moderacao/{id}/analise`, `/procedencia` ou `/improcedencia` | `{ "justificativa": "texto" }`, 200; equipe elegivel |

ComentarioResponse tem id/conteudo/dataCadastro/status/usuarioId/materialId. AvaliacaoResponse tem id/nota/dataCadastro/usuarioId/materialId. DenunciaResponse tem id/motivo/descricao/status/dataCadastro/materialId, sem identidade do denunciante. Historicos internos sao DTOs de decisoes, nao copias do conteudo original removido.

Nota e numero JSON (ponto decimal), de 0.00 a 5.00 com ate duas casas; nao enviar `"4,75"`. O backend normaliza a escala para duas casas sem arredondar entradas com precisao excedente. O frontend deve formatar a exibicao com duas casas e virgula no locale pt-BR, sem exigir zeros finais na representacao do numero JSON. Cada usuario tem no maximo uma nota por Material; POST duplicado409, PUT nao faz upsert e DELETE nao guarda historico. Nova nota apos exclusao tem outro UUID/data. Sem ETag, repetir DELETE apos novo cadastro pode excluir a nova nota.

Comentarios aceitam texto ate4000, descricao de denuncia ate2000 e motivo/justificativa de moderacao ate1000 caracteres, obrigatorios e sem U+0000. Motivos de denuncia: DIREITOS_AUTORAIS, CONTEUDO_INADEQUADO, LINK_INVALIDO, SPAM, OUTRO. Uma denuncia aberta (PENDENTE/EM_ANALISE) por usuario/Material; outra retorna409. Fluxo da equipe: PENDENTE -> EM_ANALISE -> PROCEDENTE ou IMPROCEDENTE, sem reabertura. Repetir acoes de moderacao retorna409, nao repetir POST automaticamente apos timeout sem consultar o estado.

Textos de comentarios, materiais, relatos e decisoes sao texto simples nao confiavel; usar interpolacao escapada do Vue, nunca v-html/innerHTML com esses campos. UUID de autor nao implica existir consulta publica de perfil por UUID.

## 7. Erros e consumo de respostas

| Status | Tratamento esperado |
| --- | --- |
| 400 | Entrada/UUID/enum/paginacao/JSON/multipart invalidos; corrigir formulario |
| 401 | Token ausente/invalido/expirado, credenciais invalidas ou usuario indisponivel; tratar conforme a operacao |
| 403 | Perfil/acao nao permitido ou conflito de interesse; nao assumir que novo login resolve |
| 404 | Registro inexistente ou ocultado pela politica de visibilidade; nao distinguir no cliente |
| 405 | Metodo nao implementado naquela rota |
| 409 | Duplicidade, vinculos que impedem exclusao ou estado/transicao invalido |
| 413 | Limite multipart excedido |
| 415 | Tipo de corpo/parte nao suportado |
| 503 | Arquivo .torrent indisponivel/inconsistente; nao oferecer conteudo corrompido |

Erros MVC usam ProblemDetail nativo com campos como type/title/status/detail/instance e Content-Type application/problem+json. Erros de Bean Validation podem acrescentar `errors: [{ "field": "titulo", "message": "..." }]`; essa lista nao existe em todos os erros.

Falhas Bearer nativas de seguranca podem ter corpo vazio: o GET protegido sem token foi confirmado como401, WWW-Authenticate Bearer e corpo vazio. Login invalido, por outro lado, retorna401 com ProblemDetail. Nao chamar response.json() incondicionalmente nem substituir handlers de seguranca para obrigar um formato unico.

No cliente: verificar response.ok/status; em204 nao ler JSON; em download bem-sucedido ler blob; nos demais casos ler corpo somente quando existir e conferir Content-Type antes de interpretar JSON. Usar mensagem generica por status quando nao houver ProblemDetail. Falha CORS/rede pode impedir acesso ao status/corpo; nao confundir com resposta JSON de negocio. Evitar incluir corpo cru/credenciais/URIs privadas em logs ou mensagens tecnicas.

## 8. Verificacao e proxima integracao

Entrega vigente26/10/2026: consulta paginada de cursos em `/instituicoes/:id/cursos`, autenticada para os três perfis. GET da instituição valida o pai antes do GET `/cursos?instituicaoId&page&size`; validação de DTO/vínculo/página e paginação20/50/100. **1208 testes frontend**/TypeScript/build e **27 testes Maven focados PostgreSQL local** (21 API+6 serviço) aprovados. Sem mutações ou alteração backend nesta etapa; HTTP sintético não integra Vue/Spring. Sem Docker; visual/DOM/E2E pendentes. Guia nofrontend docs/CURSOS_CONSULTA_FRONTEND.md. Próximo cadastro de curso para ADMINISTRADOR. Registros anteriores históricos.

Entrega vigente26/10/2026: consultaInstituições nofrontend em `/instituicoes` e `/:id`, requiresAuth/todosperfis, menu/conta, semmutaçõesourestriçãodeleituraaADMIN. Service sóGETlista page-size/detailUUID, projeta3campos id-nome-sigla/validaDTO-página-IDsúnicos-vínculo/semfilhosN+1/prefetch/sortclient. Lista20/50/100/correçãounicainclusivezero/reloadpreserva/tamanhozera0/volta nova20/0, detalheindependente. Contexto-conta-perfil-rota-query-dispose cancela/ignora/limpa;401/403retiramdados/bloqueiam,detalhe404indisponível,redefalha!=vazio/retryGETexplícito. **1135 testes frontend**/TypeScript/build, **21backend focados** e **2ITopcionaisSpringHTTP/PG** ampliados cominstituiçãofixture aprovados/cleanupDB-files-root. Guia nofrontend docs/INSTITUICOES_CONSULTA_FRONTEND.md. SemJava produção/schema/dependências/Docker/.envreal/dadosreais/Git, Mavencompleto551 anterior não repetido. Vite2002rotas/lista-detalhe-menu/servidoresencerrados; visual/DOM/foco/E2Ependentes semnavegador. Próximo cadastroInstituições sóADMIN, depoisedição/exclusão, preservando leiturasautenticadas. Registros abaixo históricos.

Entrega vigente26/10/2026: frontend adiciona .torrent próprio via rota `/materiais/:id/torrents/adicionar`, requiresAuth/GETdetail eautoria-estado-saldo conferidos; semprivilégioequipe/revisãoequipe semformautor. Multipart sóarquivo/Fileúnico v1 até2MiB/ContentTypeautomático, limite10Torrents independenteMagnets. Confirmação retornoPENDENTE por seleção-limpeza-tentativa, emvoo travaações. DTO Torrent5campos validaidnovo/nome-tamanho/hashv1/data, ignoraLocation/extras/sucesso sóUUIDs/File-snapshotliberados; nãoéMaterial e nãoinferir estadoatual, consultar detalhe explicitamente.400/413/415 liberaFile/reselecionar;401/403/404limpa/bloqueia;409/incerto descartaFile-snapshot/exigeGETnovo/novaseleção-confirmação semretry/atribuição porcontagem. **1110 testes frontend**/TypeScript/build, **79backend focados** e **2ITopcionais upload-downloadSpringHTTP/PG** aprovados/cleanupregistros-files-rootexclusivo. Sharedsnapshot/parserextraídos comregressõesMagnets preservadas. Guia nofrontend docs/ADICAO_TORRENT_FRONTEND.md. SemJava produção/schema/dependências/Docker/.envreal/dadosreais/Git, Mavencompleto551 anterior não repetido. Vite200rota/form/detalhe/lista/servidoresencerrados; visual/DOM/foco/FilePicker/E2Ependentes semnavegador. Próximo sugerido consultaInstituições emáreaadministrativa acadêmica/escritaADMIN; discutircontratoPUT/DELETE mecanismos. Registros abaixo históricos.

Entrega vigente26/10/2026: frontend adiciona Magnets próprios em `/materiais/:id/magnets/adicionar`, requiresAuth, GETdetail único e autoria/estado/saldo conferidos sem privilégioequipe. POST existente somente magnetUris, lote1..saldo10/trim/até4000, validação local de formato/duplicataexata e definitiva de hash no backend. Confirmação retornoPENDENTE por edição/tentativa, envio trava formulário/recarga/duplicidade. Resposta PENDENTE/autor-data/mecanismos antigos/mínimoacréscimo conferidos, semsnapshot rígido de metadados ou acréscimosconcorrentes; sucesso sóUUID.400preserva draft/reconfirma;401/403/404 retiramtudo;409/incerto descarta snapshot-URIs/exigeGETnovo/novo lote-confirmação, semretry/atribuição porcontagem. **1077 testes frontend** (33novos)/TypeScript/build, **54 testes backend focados** e **2IT opcionais SpringHTTP/PG reais** ampliados com novo modelo aprovados/fixtureslimpas. Guia nofrontend docs/ADICAO_MAGNETS_FRONTEND.md; semJava produção/schema/dependências/Docker/.envreal/dadosreais/Git, Mavencompleto551 anterior não repetido. Vite200rota/formulário/detalhe/lista, servidoresencerrados; semnavegador visual/DOM/foco/E2Ependentes. Próximo adição .torrent próprio viaAPIexistente, semPUT/DELETE mecanismos. Registros abaixo históricos.

Validação vigente26/10/2026: **551 testes backend** clean verify/JAR, **1044 testes frontend** verify/TypeScript/build e **2 cenários opcionais ComentarioFrontendIT** aprovados. O IT liga código real de cliente/services/modelo frontend ao SpringHTTP real/porta aleatória/PostgreSQL, em ambos os perfis da equipe. JWTs exclusivos de teste por stdin/Node sem ambiente real herdado; fixtures próprias confirmadas/limpas emfinally porUUID com ausência conferida. Verifica cadastro, ocultação/reexibição, próprios comentários da equipe, histórico sem texto após remoção,401/403, preflight5173 permitido5174 negado,409 sem evento extra e Material privado após leitura. Fora das suítes padrão, sem nova dependência; guia e comandos em [VALIDACAO_INTEGRADA_COMENTARIOS.md](VALIDACAO_INTEGRADA_COMENTARIOS.md). Configuração local conferida sem valores: API pública padrão/api/v1, origem5173 autorizada, bootstrap desativado. Servidores/subprocessos encerrados; sem Java produção/schema/Docker/.env real/dados reais/deploy/Git. Não é Vue/DOM/navegador nem prova concorrência; visual/teclado/foco/E2E pendentes sem navegador. Próximo código: adição de Magnets a Material próprio no frontend, respeitando API/lote/limite10/retornoPENDENTE. Registros abaixo históricos.

Entrega vigente frontend26/10/2026: reexibição de OCULTO em Material PUBLICADO observado no detalhe. POST existente `/materiais/{materialId}/comentarios/{comentarioId}/reexibicao` com JSON somente motivo, trim obrigatório até1000 sem U+0000; resposta ATIVO/par/autor/data conferidos. Ciclo/formulário compartilhados com ocultação, adaptadores anteriores preservados. Prop de estado evita GET adicional e integra contexto/watch; API revalida estado vigente, sem ETag/snapshot. Um painel sem ação padrão, troca exige cancelar/descartar/lista nova; confirmação por edição/tentativa e bloqueio em voo. Sucesso faz GET lista20/0, sem evento otimista;400 mantém motivo,401/403/404 retiram tudo,409/incerto descarta texto-motivo/exige conferir revisão/reconsulta/nova preparação sem retry/atribuição por estado. Contexto/geração/dispose abortam/ignoram, sem garantia rollback. Equipe pode moderar próprio comentário sem conflito Denuncia; não altera Material/restaura REMOVIDO/inferir estado por histórico. Comum PUBLICADO/ATIVO e histórico sem texto preservados. **1044 testes** (23 novos), TypeScript/build aprovados;1021 regressões preservadas. HTTP RAM não Spring/DB/DOM/prova autorização. Vite200 rota/seção/painel/Material/servidor encerrado; visual/DOM/foco/E2E pendentes. Sem Java/schema/dependências/Maven/Docker/.env real/dados reais/Git. Guia no frontend docs/REEXIBICAO_COMENTARIO_FRONTEND.md. Próximo validar integração real comentários/moderação e roteiro de navegador quando disponível. Registros abaixo históricos.

Entrega vigente frontend26/10/2026: ocultação de comentário ATIVO selecionado na página da equipe. POST existente `/materiais/{materialId}/comentarios/{comentarioId}/ocultacao` com JSON somente `motivo`, trim obrigatório até1000/sem U+0000; valida resposta OCULTO/par/autor/data. Formulário único descarta a página, mantém só alvo, exige confirmação por tentativa e bloqueia edição/reenvio/consultas durante POST. Sucesso faz GET lista nova20/0, sem evento otimista.400 preserva motivo/reconfirma;401/403/404 retiram tudo,409/incerto descarta texto-motivo e exige fechar/GET novo/nova seleção, sem retry ou atribuição de evento por estado. Contexto/dispose abortam/ignoram; sem ETag/garantia de rollback. Equipe pode moderar comentário próprio em qualquer estado do Material, sem aplicar conflito de Denuncia; comum ATIVO/PUBLICADO e histórico sem texto preservados. **1021 testes**, TypeScript/build aprovados; build final repetido após ajustes de formulário/foco. HTTP RAM não é Spring/DB/DOM/prova autorização. Vite200 rota/seção/painel/servidor encerrado; visual/DOM/foco/E2E pendentes. Sem Java/schema/dependências/Maven/Docker/.env real/dados reais/Git. Guia no frontend docs/OCULTACAO_COMENTARIO_FRONTEND.md. Próximo reexibição OCULTO somente Material PUBLICADO. Registros abaixo históricos.

Entrega vigente frontend25/10/2026: histórico de comentário selecionado na lista da equipe. Abrir descarta lista/texto/autor e guarda sóUUID; GETparMaterial-comentário/historico-moderacao, parser7campos/motivo/transições/IDs/página e projeçãoextras. Um painel20/50/100/ASC, voltar fazlistanova20/0; continua apósremoçãológica sem recuperar texto, seminferir estadoatual oureconstruireventos.401/403/404 retiramalvo-eventos; voltar revalida listaexplicitamente. Contexto/query/dispose descarta/aborta/ignora; semmutações/N+1/nomes oumudança comumATIVO/PUBLICADO.993 testes/TypeScript/build aprovados; HTTPRAM nãoSpring/DB/DOM/provaautorização; Vite200/servidorencerrado, visual/DOM/foco/E2E pendentes. SemJava/schema/dependências/Maven/Docker/.envreal/dadosreais/Git. Guia nofrontend docs/HISTORICO_COMENTARIO_EQUIPE_FRONTEND.md; próximo ocultação com motivo/confirmação, reexibição depois. Registros abaixo históricos.

Entrega vigente frontend25/10/2026: comentários da equipe na revisão de Material, somente leitura. GET /materiais/{id}/comentarios/moderacao sópage-size20/50/100/ASC, ATIVO/OCULTO em qualquer estado de Material, sem textoREMOVIDO. DTO6campos/Material/texto/página validados; comum continua ATIVO/PUBLICADO. Modelo paginado compartilhado com política401/403/404 específica, padrão anterior preservado; contexto/query/dispose cancela/ignora/limpa, indisponibilidade bloqueia até nova carga. Sem N+1/nomes/formulários de autor/histórico/mutações, não aplicar conflitoDenuncia a comentários.968 testes/TypeScript/build aprovados; HTTPRAM nãoSpring/DB/DOM/provaautorização, Vite200/servidorencerrado, visual/DOM/teclado/E2E pendentes. SemJava/schema/dependências/Maven/Docker/.envreal/dadosreais/Git. Guia nofrontend docs/COMENTARIOS_EQUIPE_FRONTEND.md; próximo histórico de comentário selecionado. Registros abaixo históricos.

Entrega vigente frontend25/10/2026: conclusões procedente/improcedente de EM_ANALISE, endpoints existentes com JSONsójustificativa<=1000semU+0000 e confirmação específica. Ciclo de três ações compartilhado, início/adaptadores/regressões preservados. Um formulário/sem escolha padrão; troca descarta draft/checkbox. Sucesso reconsultaGET semevento otimista; finais semnova ação.403POST bloqueiaambas/preserva leitura autorizada;401/404 retiramtudo;400conservadraft/consomeconfirmação;409/incerto exige descartar/GETnovo/novapreparação, semretry/atribuição por texto ouestado. Outra equipe elegível podeconcluir, impedimentos inclusiveadmin preservados e semMaterialautomático.945 testes/TypeScript/build aprovados; HTTPRAM nãoSpring/DB/DOM/provaautorização. Vite200rota/painel/adaptador, servidorencerrado; visual/DOM/foco/E2E pendentes. SemJava/schema/dependências/Maven/Docker/.envreal/dadosreais/Git. Guia nofrontend docs/CONCLUSAO_DENUNCIA_FRONTEND.md; próximo consulta de comentários para moderação por Material. Registros abaixo históricos.

Entrega vigente frontend25/10/2026: início de análise com justificativa/confirmação na revisão. POST/denuncias/moderacao/{id}/analise sójustificativa<=1000/semU+0000, sóPENDENTE apósrelato/históricoválidos. Sucesso reconsultaGET, semevento otimista.403POST bloqueiaação/preserva leitura autorizada semclassificar causa;401/404 retiramtudo;400conservadraft/orienta conferir;409/incerto exige descartar/GETnovo/novapreparação, semretry. Contexto/rascunhoRAM porconta-perfil-rota/geração e confirmaçãoconsumida. Semconclusões/Materialautomático; impedimentos inclusiveadmin preservados.921 testes/TypeScript/build aprovados; HTTPRAM nãoSpring/DB/DOM. Vite200/servidorencerrado; visual/DOM/foco/E2E pendentes. SemJava/schema/dependências/Maven/Docker/.envreal/dadosreais/Git. Guia nofrontend docs/INICIO_ANALISE_DENUNCIA_FRONTEND.md; próximo conclusões procedente/improcedente. Registros abaixo históricos.

Entrega vigente frontend em25/10/2026: detalhe/histórico de denúncia da equipe em `/moderacao/denuncias/:id`, aberto pela fila e protegido por equipe. GETs no namespace `/denuncias/moderacao`: detalhe6campos, histórico7campos com justificativa/transição/data/UUID do responsável. Parser confere vínculo/UUID/estados/página e projeta extras. TextoVue escapado, sem consultas adicionais a nomes/denunciante/Material ou mutações. Relato próprio segue separado e sem histórico interno.

Modelo consulta relato anteshistórico;20/50/100/ASC/correção única inclusivezero. Consultas independentes semsnapshot/ETag; parcial/vazio legado não contradiz nem altera estado do relato. Rede-protocolo-5xx histórico retira página/preserva relato;401/403/404 qualquerGET retiramtudo/bloqueiamretry até nova carga,403não desloga/401helper tokenusado. Conta/perfil/rota/novaquery/dispose limpam/abortam/ignoram;RAM semURL-storage-logs privados. Leitura da equipe mesmo impedida, sem inferir elegibilidade; conflitoDenuncia inclusiveadmin e autoaprovaçãoMaterial ambos preservados.

`npm run verify`:890 testes (34novos:30service-modelo,3guardas,1HTTP), TypeScript/build aprovados. HTTPRAM/Routermemória nãoSpring/DB/DOM/provaautorização; Vite200rota/detalhe/fila, servidor encerrado; sem navegador visual/mobile/DOM/teclado/E2E pendentes. SemJava/schema/dependências/Maven/Docker/backendiniciado/.envreal/credenciais/dadosreais/deploy/Git. Maven549completo/20focado materiais históricos não reexecutados. Guia nofrontend docs/DENUNCIA_HISTORICO_EQUIPE_FRONTEND.md; próximo recomendado iniciar análise comjustificativa/confirmação, conclusões depois. Registros abaixo históricos.

Entrega vigente frontend em25/10/2026: fila somente leitura /moderacao/denuncias, equipe por guardas/perfilMe/contexto UUID-perfil, API segue autoridade pelas rolesBearer. GET/denuncias/moderacao sópage-size-status, PENDENTE inicial/quatro estados/20-50-100/ASC; filtro volta0/preserva tamanho, reload mantém página/filtro, correção única inclusivezero. Service reutiliza parser de seis campos/projeta cinco sem descrição/denunciante/extras e confere filtro/IDs únicos. Sem N+1 ou ações de denúncia; link explícito à revisão Material não a conclui/altera. Não usar rota de relato próprio para consultas da equipe.

Modelo paginado extraído de Material/compartilhado; regressões anteriores aprovadas. Conta-perfil-query-dispose descartam/abortam/ignoram;401/403 limpam/bloqueiam até reinicializar,403não desloga/401helper tokenusado, rede-protocolo-5xx erro!=vazio/retryGETexplícito. Filtros/dadosRAM semURL/storage/logs privados. Leitura permitida mesmo impedido de decidir, sem inferir elegibilidade ou buscar denunciante. Conflito nas mutações de denúncia segue inclusiveadministrador; autoaprovação de Material ambos preservada.

npm run verify:856 testes frontend (28novos:24 service-modelo,3 guardas,1HTTP), TypeScript/build aprovados. HTTPRAM/Routermemória não Spring/DB/DOM/provaautorização; Vite200rota/filas/conta, servidor encerrado; sem navegador visual/mobile/DOM/teclado/E2E pendentes. SemJava/schema/dependências/Maven/Docker/backendiniciado/.envreal/credenciais/dadosreais/deploy/Git. Maven completo549 e focado20 de materiais históricos não reexecutados. Guia nofrontend docs/FILA_DENUNCIAS_EQUIPE_FRONTEND.md; próximo recomendado detalhe/histórico somente leitura. Registros abaixo históricos.

Entrega vigente em25/10/2026: publicação/bloqueio integrados na revisão /moderacao/materiais/:id; decisão explícita de manter autoaprovação de ambos os perfis. Service POST sem corpo/identidade/motivo, validaDTO/UUID/estado e usa Bearer existente. Painel exige escolha/checkbox/botão; confirmação consumida por tentativa, bloqueio de duplo envio/troca/recarga emvoo. Sucesso reconsultaGET e preserva aviso se leitura falha;409/incerto rede-protocolo-408-5xx exigemGETnovo/novaescolha/confirmacão semretryPOST.401/403/404mutação retiram revisão/URIs/confirmacão;403não desloga/401helper tokenusado. Conta/perfil/rota/geração/dispose abortam/ignoram semgarantia rollback. Sem ETag/histórico/motivo/desbloqueio/restauração/impersonação; detalhe comum preservado.

npm run verify:828 testes frontend (32novos:31 service-modelo+1HTTP), TypeScript/build aprovados. MaterialFluxoApiTests:20 testes backend aprovados (2novos de autoaprovação), PostgreSQL/rollback. Maven completo549 histórico não repetido; Java de produção/schema/dependências inalterados. HTTPfrontend RAM não é Spring/DB/DOM; Vite200 rota/painel/leitor/wrapper, servidor encerrado; navegador indisponível visual/mobile/DOM/foco/E2E pendentes. .env carregado paraMaven semexpor/alterar; semDocker/bootstrap/login-real/decisão-real/deploy/Git. Guia nofrontend docs/DECISOES_MATERIAL_FRONTEND.md; próximo recomendado fila de denúncias da equipe somente consulta. Registros abaixo são históricos e as perguntas antigas de autoaprovação já foram respondidas.

Entrega vigente em 25/10/2026: revisão individual somente de leitura /moderacao/materiais/:id para equipe. A fila abre wrapper próprio de MaterialView; composição reutiliza leitor/service e limita dados/tarefas por UUID da conta, perfil e geração. Quatro estados, metadados, nomes e mecanismos disponíveis conforme API; ações de autor/interações ocultas nesta rota inclusive em material próprio. Rota comum preservada. Recarga/troca de conta ou perfil/dispose/401/403 retiram toda a revisão; respostas antigas não repõem URIs nem entregam download. Nenhuma mutação ou autorização API alterada.

Escolha de autoaprovação solicitada, sem resposta: proibir ambos (recomendado), permitir só ADMINISTRADOR ou manter ambos. API atual permite autoaprovação de ambos; não assumir opção. Questão somente de publicação, sem estender automaticamente ao bloqueio ou copiar conflito de Denuncia. Próxima entrega depende da escolha para política backend e ações explícitas frontend; sem ETag/histórico/motivo no contrato atual.

npm run verify: 796 testes frontend (37 novos: 33 modelo, 3 guardas, 1 HTTP sintético), TypeScript/build aprovados. HTTP RAM/Router memória não são Spring/PostgreSQL/DOM; Vite200 rota/wrapper/leitor/fila, servidor encerrado. Navegador indisponível: visual/mobile/DOM/instâncias Vue/Clipboard/download real/E2E pendentes. Sem Java/schema/dependências/Maven/Docker/.env real/credenciais/dados reais/publicação/Git; Maven549 histórico não reexecutado. Guia no frontend docs/REVISAO_MATERIAL_FRONTEND.md. Registros abaixo são históricos.

Entrega vigente25/10/2026: fila somenteleitura em /moderacao/materiais para MODERADOR/ADMINISTRADOR, perfilde/usuarios/me/canModerate emmenu-conta-guardas-modelo. Meta requiresAuth-requiresModeration, anônimo/login/autenticadosemequipe/conta comavisofixo acesso=moderacao (nãoconcedepermissão), guardareageaperdadeperfilobservada. ModelovalidaUUID-perfil antesGET/descarta dados-respostasvelhos naconta-perfil/novaquery/dispose. GET/materiais/moderacao explícitoPENDENTE/page20-50-100/dataCadastroDESC-idDESC/semcorpo-ContentType-usuarioId-sort-filtros extras; correçãoúnica inclusivezero/semN+1/snapshot/polling, DTO8camposvalidado/projeta5campossemrefsautor-acadêmico-extras. Linksdetalheexistente, semnovasmutacoes/impersonação; açõesdeautor preexistentes nãoalteradas.401/403retiramfila/bloqueiamconsultas nainstância,401helperdotokenusado/403nãoencerra sessão/semperfilinferido, rede-protocolo-5xxretryexplícito/erro!=vazio. EstadoRAM semURL-storage-log privado. AutorizaçãoAPI segueBearerroles, perfil/me pode divergir apósmudançaDB/semrefresh-revogaçãoimediata-pollingperfil.

npm run verify com759testes frontend (40novos), type-check/build aprovados. HTTPRAM/Routermemória não Spring/banco/DOM/provaautorização real; Vite200rota/fila-menu-conta compilados/servidorencerrado, semnavegador visual-mobile-DOM-teclado-E2Ependentes. SemJava/schema/permissãoAPI/dependência/Maven/Docker/.envreal/credencial/dadoreal/publicação/Git; Maven549 não reexecutado. Guia nofrontend docs/MODERACAO_MATERIAIS_FRONTEND.md; próximo prepararrevisãoindividual/definirautoaprovação antespublicação-bloqueio. Registros abaixo são históricos.

Entrega vigente23/10/2026: frontenddenúncias cadastro/acompanhamentopróprio em /materiais/:id/denunciar,/minhas-denuncias,/denuncias/:id comrotasprotegidas/menu-conta-linkPUBLICADO. GETMaterial guarda sóUUID-título-status/exigePUBLICADO inclusive dono/equipe, qualquerautenticadopoderelatarinclusivepróprio. POSTsó motivo-descricao<=2000/trim/semU+0000/enum5semdefault/descriçãoobrigatória/autorJWT-PENDENTEservidor, DTO6camposconferido/sucessosóUUID/ignoraLocation/nãoalteraMaterial. ListaGETminhas sópage-size20/50/100/ASC/todosestados/semfiltros/correçãoúnica/projeta5campos semdescrição; detalhe6camposVueescapado, semusuarioIdDTO/nomes-títulos/N+1/dadosMaterialextra. AutoriaéAPI/semexceçãoadmin-dono; própriosseguemapósMaterialprivado/linkMaterialexplicito não liberaacesso. UmaabertaPENDENTE-EM_ANALISE porpar/concluídaspermitemnovo.409/incerto bloqueiamreenvio-edição-retry nainstância/orientamconferirMinhasantesreabrir, semvarrerpáginas/associarportexto/páginavazianãoprovaausência.400corrigir,401/403/404cadastro retiramalvo-draft; consultalimpaanteriores antesquery/emfalha/UUID-conta-modo-dispose/aborta-ignora/draftRAM/abortnãogarante rollback. Semedição-exclusão-cancelamento-anexos-moderação.

npm run verify com719 testes frontend (60novos), type-check/build aprovados. HTTPRAM/Routermemória não Spring/banco/DOM/provaautorização-locks-rollback; Vite200trêsrotas/componentescompilados/servidorencerrado, semnavegador visual/mobile/DOM/foco/E2Ependentes. SemJava/schema/dependência/Maven/Docker/.envreal/credencial/dadoreal/publicação/Git; Maven549 não reexecutado. Guia nofrontend docs/DENUNCIAS_FRONTEND.md; próximo recomendado área de moderação, primeiro fila de materiais pendentes para equipe. Registros abaixo são históricos.

Entrega vigente23/10/2026: frontend permite editar/excluir fisicamente própria Avaliacao carregada por/minha, semescolher terceiros/exceção de donoMaterial/equipe. PUTsónota numérica0..5/até2casas/centésimosinteiros/inalteradonãoenvia/semupsert, respostaUUID-data-autor-Material-notaconferidos. DELETE204semcorpo/ContentType/identidadequery comcheckbox+botãoexplícito/confirmacãoconsumida porconta-Material-painel-tentativa. Exclusão física sóAvaliacao/semhistórico-restauração, demaisentidadespreservadas. /minha atingenota atual: recriadaemoutraaba pode ser alterada/removida, UUIDconsultado nãoé precondição, checagempósPUT nãoimpedegravar; semETag-versão-retry. Painelúnico bloqueia recarga-cadastro-trocatipo/emvoo fechar-editar-duploenvio. SucessoPUT guardaDTO/GETresumo; DELETElimpaantiga/GETprópria-resumo antesnovoPOST (novoUUID/data). Materialprivadoapósseleção nãoimpedeDELETE, GET404retiradados/preservasóconfirmaçãofixa; semGETprivado/formulárioUUIDarbitrário.409/incerto rede-protocolo-408-5xx exigem fechar/descartar/GETnovo/novaconfirmação.403PUTbloqueiaeditar/permiteretiradaapósreconsulta,401/404mutaçãoou403DELETE retiramacesso. DraftRAM e UUID-autor-conta-dispose limpam/abortam/ignoram, abortnãogarante rollback.

npm run verify com659 testes frontend (47novos), type-check/build aprovados. HTTPRAM não Spring/banco/DOM/provaautorização-locks-rollback; Vite200detalhe/componentescompilados/servidorencerrado, semnavegador visual/mobile/DOM/foco/decimal/E2Ependentes. SemJava/schema/dependência/Maven/Docker/.envreal/credencial/dadoreal/publicação/Git; Maven549 não reexecutado. Guia nofrontend docs/AVALIACOES_EDICAO_REMOCAO_FRONTEND.md; próximo recomendado denúncias cadastro/acompanhamento próprio. Registros abaixo são históricos.

Entrega vigente23/10/2026: avaliações resumo/cadastro/consulta própria na tela de Material PUBLICADO. features/avaliacao service/model/section, authenticatedJson/Bearer, sem exceção por visibilidade privada; autor consulta mas não avalia próprio Material, inclusive equipe. Entrada vírgula/ponto0..5/até2casas, centésimos inteiros sem arredondar/truncar/JSONsomente nota numérica/exibição pt-BR2casas. DTOpróprio5campos/UUIDconta-rota/nota/data; resumo media-quantidade somente servidor, null/zero distingue ausência. read/minhaprimeiro,404candidato só vira ausência após/resumoválido; não classificar mensagem/doisGETs não snapshot/POSTrevalida. Sucesso guardaDTO/limpanota/GETresumo, semtotais otimistas, erroredeGETmantém nota/sucesso.401/403/404leituraou401/404escrita limpamacesso,403escrita bloqueiaPOST seminvalidarsessão.409/incerto rede-protocolo-408-5xx exigem consultar atual e preparar explícito seausente, semretry/PUTautomático. Nota encontrada na reconciliação não é atribuída necessariamente aPOSTperdido. DraftRAM e UUID-autor-conta-dispose limpam/abortam/ignoram; abortnão garante rollback. SemPUT/DELETEavaliacao/consultaterceiros/UIprivada nesteincremento.

npm run verify com612 testes frontend (57novos), type-check/build aprovados,501valores emcentésimos verificados numteste. HTTPRAM não Spring/banco/DOM/provaautorização-locks-rollback; Vite200detalhe/componentescompilados/servidorencerrado, semnavegador visual/mobile/decimal/teclado/E2Ependentes. SemJava/schema/dependência/Maven/Docker/.envreal/credencial/dadoreal/publicação/Git; Maven549 não reexecutado. Guia nofrontend docs/AVALIACOES_FRONTEND.md; próximo recomendado edição/remoção da própria avaliação. Registros abaixo são históricos.

Última entrega23/10/2026: frontend oferece edição/remoção lógica dos comentários próprios carregados na página. Autoria doComentário verificada nohelper/service/model, donoMaterial/equipe semexceção. Painelúnico/novoDraftpreservado/bloqueia páginas-recarga-POST-seleção, mutação emvoo também impede fechar/editar/duploenvio. PUTsóconteudo trim<=4000/semU+0000, semPUTinalterado, resposta preservaUUID/autor/Material/data/ATIVO/texto. DELETEconhecido checkbox+botãoexplícito/confirmacãoconsumida porUUID-tentativa/204estrito semcorpo/ContentType/identidadequery. Remoção lógica preservaregistro/texto semrestauração; semGETindividual/listaprivada/formulárioUUIDarbitrário. Estado pode mudar apósseleção: servidor permiteDELETEpróprio conhecido mesmoOCULTO/Materialprivado, semampliarGET.

Sucesso recarregapáginaatual/correçãoúnica/preserva novoDraft, erroredeGET mantémsucesso;401/403/404 retiramlista/alvo/drafts.409/incerto rede-protocolo-408-5xx exigem fechar/descartar/GETnovo antesdenovaseleção; ausência não prova remoção/semretry. SemETag/versão, avisooutraaba; UUID/conta/dispose abortam/ignoram, abortnãogarante rollback. npm run verify com555 testes frontend (46novos), type-check/build aprovados. HTTPRAM não Spring/banco/DOM/provaautorização/rollback; Vite200detalhe/componentescompilados/servidorencerrado, semnavegador visual/mobile/DOM/foco/E2Ependentes. SemJava/schema/dependência/Maven/Docker/.envreal/credencial/dadoreal/publicação/Git; Maven549 não reexecutado. Guia nofrontend docs/COMENTARIOS_EDICAO_REMOCAO_FRONTEND.md; próximo recomendado avaliações resumo/cadastro/consulta própria. Registros abaixo são históricos.

Atualizacao mais recente23/10/2026: frontend integra comentarios listagem/cadastro aos detalhes somente PUBLICADO. GET/POST existentes, DTO6campos/ATIVO/Material da rota/autorPOST conferidos, sem N+1nomes/perfis; texto interpolado Vue<=4000/semU+0000, paginas20/50/100 ASC/Ultima pagina/Atualizar/correcao unica. Nao ampliar pela visibilidade privada de dono/equipe. POSTsomenteconteudo; sucesso limpa draft/recarrega pagina0 semprepend, erroGETposterior mantem confirmacao.401/403/404 retiram lista/formulario. RascunhoRAM, envio duplicado/edicao/consultas bloqueados durantePOST, respostas antigas descartadas aposUUID/conta/dispose; abortnaogarante rollback. Resultado incerto rede/protocolo/408/5xx exige GETbem-sucedido+acaoexplicita preparar tentativa comaviso duplicidade; semretry/idempotencia/prova deausencia porpagina. SemPUT/DELETE/moderacaofrontend nesta entrega.

509 testes frontend aprovados (43novos), type-check/build aprovados; verify508 antesultimoHTTP e testfinal509/codigoaplicacao inalterado aposbuild. HTTPsintetico emRAM valida transporte/estados, nao autorizacao Spring/banco/DOM/rollback. Vite200detalhe/componentes compilados, servidorencerrado; navegador indisponivel para visual/mobile/E2E. SemJava/schema/dependencia/Maven/Docker/backendiniciado/.envreal/credencial/dado real/publicacao/Git; Maven registrado549 nao reexecutado. Guia nofrontend docs/COMENTARIOS_FRONTEND.md. Proxima entrega recomendada edicao/remocao de comentarios proprios; registros abaixo descrevem entregas anteriores.

Arquivos com o codigo completo deste incremento, nos caminhos relativos a raiz do repositorio:

- [SecurityConfig.java](../src/main/java/br/com/murkyweb/unesc_share/security/SecurityConfig.java): configuracao CORS nativa, sem novo filtro ou mudanca de autorizacao.
- [AutenticacaoTests.java](../src/test/java/br/com/murkyweb/unesc_share/security/AutenticacaoTests.java): quatro casos novos de CORS e protecao de rotas.
- [MaterialCadastroApiTests.java](../src/test/java/br/com/murkyweb/unesc_share/material/MaterialCadastroApiTests.java): um caso novo de criacao com Location exposto.
- [TorrentApiTests.java](../src/test/java/br/com/murkyweb/unesc_share/material/TorrentApiTests.java): um caso novo de download com Content-Disposition exposto.

Nenhuma nova dependencia, entidade, migration, variavel ou alteracao de schema foi necessaria. O ajuste usa setExposedHeaders na CorsConfiguration existente; justificativa: o frontend autorizado precisa ler os cabecalhos de resposta sem ampliar as regras de acesso.

Com PostgreSQL local ativo e variaveis do backend exportadas:

```bash
./mvnw '-Dtest=AutenticacaoTests,MaterialCadastroApiTests,TorrentApiTests' test
./mvnw clean verify
```

Os testes usam JWTs exclusivos de test, PostgreSQL e fixtures sinteticas com rollback, sem credenciais reais ou alteracao do catalogo Matriz6. A regressao CORS verifica criacao201/Location, download200/Content-Disposition/bytes, respostas401/403, ausencia de sessao/cookies CORS e bloqueio de origens/metodos/cabecalhos nao autorizados. MockMvc verifica o contrato HTTP produzido pelo Spring; nao e teste de leitura de headers em navegador ou teste E2E do Vue.

No repositorio separado, URL/cliente fetch/login/usuario atual/sessionStorage/Router e seletores academicos/busca paginada de PUBLICADO estao implementados. Catalogo em /materiais protegida, com paginacao de opcoes e sem consultas N+1 por cartao. /materiais/:id tambem implementada: metadados/nomes, download Bearer por clique com stream limitado a2MiB, MIME verificado e nome fixo UUID.torrent, copy explicita com fallback manual e link magnet seguro. Nao confundir MIME/limite com validacao estrutural do Torrent, que segue no backend. Sem consultas automaticas a trackers/peers.

Cadastro de conta tambem implementado no frontend em /cadastro publica, com nome/email/senha/confirmacao. POST /auth/cadastro projeta somente nome/email/senha, sem Bearer/cookies; normalizacao definitiva e perfil USUARIO fixos continuam no servidor. Confirma sucesso sem login automatico nem alterar sessao/storage existente. Valida limites e72bytesUTF8, preserva senha, trata409/400 e resultados incertos de rede/5xx sem retry automatico; cancelamento da espera nao garante rollback. Guia nofrontend docs/CADASTRO_FRONTEND.md.

Meus materiais implementado em /meus-materiais protegida: GET /materiais/meus por Bearer, sem usuarioId em query, todos os quatro estados por padrao, q/estado e pagina20/50/100. Mesmo equipe consulta somente proprios nesta rota; nao confundir com moderacao. Consulta/links de detalhes apenas, sem POST/PUT/DELETE/restauracao. Parser PagedModel compartilhado no frontend preserva PUBLICADO-only na busca geral.

CadastroMaterial comMagnets implementado nofrontend /materiais/novo protegida, metadados/seletores dependentes e1..10URIs, POSTJSON apenas titulo/descricao/disciplinaId/categoriaId/magnetUris por Bearer. Autor/estadoPENDENTE no servidor, sem auto-publicacao/Material vazio. Parser de detalhe reutilizado na resposta, seletores compartilhados com catalogo. Sucesso limpa draft/oferece consultas; rede/protocolo/5xx incerto pede conferir Meus materiais sem retry automatico. Guia nofrontend docs/CADASTRO_MATERIAL_MAGNET_FRONTEND.md.

Desde23/10/2026, mesmo formulario oferece .torrent v1 alternativo, File1byte..2MiB, POSTmultipart com apenas materialBlobapplication/json(quatro campos)/arquivoFile, sem Content-Type global. authenticatedMultipart compartilha token/401/politicasHTTP e parserJSON, sem cookies/refresh/Material vazio/cadastro misto. FileRAM limpo ao trocar/sucesso/sair, erros preservam para correcao; extensao/MIME nao validam metainfo. Servidor continua parser/hash/v1/locks/storage/rollback, abort do cliente nao garante cancelamento. Guia nofrontend docs/CADASTRO_MATERIAL_TORRENT_FRONTEND.md.

Edicao de metadados implementada em /materiais/:id/editar protegida, links soautor PENDENTE/PUBLICADO. GETelegivel e4GETs referenciasUUID antesde listas100/Carregar mais, preserva selecionado fora dapagina. PUT apenas titulo/descricao/disciplinaId/categoriaId porBearer, mesmaidentidade/PENDENTE/refscorretas naresposta; naoenvia mecanismos. SemPUTsemalteracao, semETag/versao: outraedicao pode ser sobrescrita, avisoPublicado->PENDENTE/fora catalogo. Resultado incerto pede conferir antesdereenviar, semretry; abortnaogarante cancelamento servidor. Guia nofrontend docs/EDICAO_MATERIAL_FRONTEND.md.

Remocao logica implementada nofrontend /materiais/:id/remover protegida, links soautor PENDENTE/PUBLICADO/BLOQUEADO. GETprojeta resumoalvo antesde checkboxobrigatorio/botaoexplicito; abrir/cancelar/reload naoremove. authenticatedDelete usaBearer/401/politicasHTTP, semcorpo/ContentType/identidadenaquery e204estrito semJSON,2xxinesperadoincerto. ConfirmacaoporUUID/tentativa econsumida, nunca transferida aoutroalvo;incerto bloqueiaconfirmar/DELETE ateGETnovo+novaconfirmacao. REMOVIDOobservado informa semnovoDELETE; idempotencia204 existente cobre alteracaoentreGET/DELETE. Semrestauracao/expurgofisico/liberacaohash/autorizacaonova; cancelamento naogarante rollback. Guia nofrontend docs/REMOCAO_MATERIAL_FRONTEND.md.

Verificacao frontend mais recente23/10/2026:466 testes/type-check/build aprovados (40novos), HTTPsinteticoemRAM/GETantesconfirmacao/DELETEBearersemcorpo204/estados/idempotenciasimulada/erros/200inesperado/conexao perdida/reconsulta eRouter memory history, Vite200/fallback/componente compilado. Semnavegador: visual/mobile/DOM/confirmacaoE2E pendentes; nao prova autorizacao/rollback/preservacao Spring/PostgreSQL. NenhumDELETEreal, Java/schema/dependencia/autenticacaobackend alterados ou Mavenreexecutado. Proximo recomendado comentariosPUBLICADO/listagem-cadastro, nao reutilizar visibilidade deMaterialprivado. Sem Pinia/Axios/OpenAPI aprovados.

Contratos completos por funcionalidade: [Autenticacao](AUTENTICACAO.md), [Instituicoes](INSTITUICOES.md), [Cursos](CURSOS.md), [Disciplinas](DISCIPLINAS.md), [Categorias](CATEGORIAS.md), [Materiais](MATERIAIS.md), [Cadastro com Torrent](CADASTRO_MATERIAL_TORRENT.md), [Torrents](TORRENTS.md), [Magnets](MAGNETS.md), [Comentarios](COMENTARIOS.md), [Edicao/remocao de comentarios](COMENTARIOS_EDICAO_REMOCAO.md), [Moderacao de comentarios](COMENTARIOS_MODERACAO.md), [Avaliacoes](AVALIACOES.md), [Edicao/remocao de avaliacoes](AVALIACOES_EDICAO_REMOCAO.md), [Denuncias](DENUNCIAS.md), [Moderacao de denuncias](DENUNCIAS_MODERACAO.md).
