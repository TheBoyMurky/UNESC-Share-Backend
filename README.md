# UnescShare — Backend

API REST do UnescShare, uma plataforma para catalogar materiais academicos compartilhados por arquivos `.torrent` e Magnet URIs.

O backend armazena metadados, arquivos `.torrent` e referencias Magnet. Ele nao hospeda o conteudo academico distribuido pela rede BitTorrent.

## Requisitos

- Java 21
- Docker com Docker Compose

## Ambiente local

Crie o arquivo local de configuracao:

```bash
cp .env.example .env
```

Troque o valor de `POSTGRES_PASSWORD` no `.env`. Gere uma chave JWT exclusiva:

```bash
openssl rand -base64 32
```

Inclua o resultado em `JWT_SECRET` no `.env`, sem versionar ou compartilhar a chave.
O backend nao inicia sem uma chave valida. Para o primeiro incremento, use `JWT_TTL=15m`.
O `.env` existente nao foi alterado automaticamente; acrescente as variaveis JWT indicadas em `.env.example`.

Inicie o PostgreSQL:

```bash
docker compose up -d
docker compose ps
```

O Docker Compose le o `.env` automaticamente. Para disponibilizar as mesmas variaveis ao Spring Boot executado fora do Docker, exporte-as no terminal:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

A aplicacao usa o prefixo `/api/v1` para os endpoints REST.

## Autenticacao JWT

O primeiro incremento disponibiliza:

| Metodo | Endpoint | Acesso |
| --- | --- | --- |
| POST | `/api/v1/auth/cadastro` | Publico; cria apenas perfil `USUARIO` |
| POST | `/api/v1/auth/login` | Publico; retorna JWT Bearer |
| GET | `/api/v1/usuarios/me` | Token Bearer valido |
| PUT | `/api/v1/usuarios/me` | Atualiza nome/e-mail do usuario do JWT; troca de e-mail exige senha atual |
| PUT | `/api/v1/usuarios/me/senha` | Troca senha com reautenticacao; sucesso sem corpo (`204`) |

O token expira em 15 minutos por padrao, sem refresh token e sem revogacao imediata.
A validade e configuravel entre 1 e 30 minutos. A assinatura utiliza HS256 com chave Base64 de pelo menos 32 bytes aleatorios.
O servidor utiliza os recursos nativos do Spring Security para autenticacao de senha, emissao e validacao JWT e conversao de permissoes.
Nao ha filtro JWT proprio, login por formulario, autenticacao Basic ou sessao HTTP.

Consulte [docs/AUTENTICACAO.md](docs/AUTENTICACAO.md) e [docs/PERFIL_USUARIO.md](docs/PERFIL_USUARIO.md) para estrutura, configuracao, exemplos, testes e limitacoes. A troca de e-mail e imediata e a troca de senha nao revoga JWTs antes da expiracao; sem verificacao de e-mail, recuperacao de senha ou revogacao individual.

No perfil local `dev`, a API tambem oferece [OpenAPI/Swagger UI](docs/SWAGGER.md) em `http://localhost:8080/api/v1/swagger-ui.html` e a especificacao JSON em `http://localhost:8080/api/v1/v3/api-docs`. A interface e a especificacao ficam desabilitadas fora de `dev`; o esquema Bearer permite testar rotas protegidas sem mudar suas autorizacoes.

Validado em 26/10/2026 com `./mvnw clean verify`: 558 testes passaram sem falhas, erros ou ignorados e o JAR foi gerado. Os testes confirmam as URLs, o Bearer nas rotas protegidas, as rotas publicas de autenticacao e o bloqueio da documentacao fora de `dev`.

## Integracao com o frontend separado

O guia consolidado [docs/INTEGRACAO_FRONTEND.md](docs/INTEGRACAO_FRONTEND.md) descreve rotas, permissoes, DTOs, filtros, paginacao, erros e exemplos TypeScript de multipart/download autenticado para Vue 3/Vite.
O CORS nativo do Spring expoe apenas `Location` e `Content-Disposition` como cabecalhos adicionais de resposta, sem ampliar origens, metodos, cabecalhos de requisicao ou permissoes, e sem credenciais por cookie.

Em26/10/2026, o frontend implementou edição de nome/e-mail e troca de senha em `/conta`; guia [perfil no frontend](../UNESC-Share-Frontend/docs/PERFIL_FRONTEND.md). `npm run verify`:1.408 testes, TypeScript/build aprovados. Backend `./mvnw clean verify`:555 testes e JAR aprovados, incluindo quatro testes de perfil integrados com PostgreSQL. E-mail muda imediatamente após senha atual; senha também exige senha atual e encerra sessão local no frontend. JWTs já emitidos não são revogados. Sem navegador para inspeção visual/E2E e sem uso de Docker.
Respostas Bearer podem ter corpo vazio: tratar o status antes de tentar ler JSON; em 204 nao ler corpo e no download bem-sucedido ler bytes/blob. Configuracao publica do frontend nunca deve conter `JWT_SECRET` ou credenciais do banco.
Em 22/10/2026, o usuario aprovou Vue Router e escolheu sessionStorage para o JWT no frontend, apos comparar riscos/opcoes; cliente usa fetch nativo, sem Axios/Pinia. Nao houve mudanca em Bearer, cookies ou CSRF do backend. Em25/10/2026 aprovou manter a autoaprovação de Material para MODERADOR e ADMINISTRADOR, inclusive material próprio.
O frontend na pasta irma `../UNESC-Share-Frontend` possui /login, /conta guardada, consulta do usuario, restauracao na mesma aba e logout local. Salva somente token/expiracao, sem senha/perfil/dados pessoais. sessionStorage continua acessivel ao JavaScript/XSS; logout nao revoga JWT. Seus 68 testes, type-check e build passaram; inclui HTTP sintetico e Router em memoria, nao login real em navegador (indisponivel). Backend iniciou pelo Maven com bootstrap desativado/zero disciplinas novas; smoke semJWT401/preflight200, sem dados de negocio ou credenciais reais. Guia em `../UNESC-Share-Frontend/docs/AUTENTICACAO_FRONTEND.md`. Java nao alterado e suite Maven nao reexecutada nesta entrega; Git separado sem remoto/commit/push.
Incremento de catalogo em22/10/2026: frontend com /materiais protegida, seletores academicos dependentes/paginados e busca de PUBLICADO por texto/filtros, pagina20/50/100, tratamento de erros/vazio e respostas atrasadas.115 testes frontend, type-check/build aprovados (47novos), sem dependencias novas ou Java/schema alterados. Guia ../UNESC-Share-Frontend/docs/CATALOGO_FRONTEND.md.

Incremento frontend de detalhes em22/10/2026: /materiais/:id, nomes academicos, download Bearer limitado a2MiB com nome UUID.torrent e copia/abertura explicita de Magnet, sem acessar automaticamente a rede BitTorrent.191 testes frontend/type-check/build aprovados (76novos); HTTP sintetico/guardas em memoria/acoes de navegador simuladas e Vite200/fallback/componente compilado. Guia ../UNESC-Share-Frontend/docs/MATERIAL_DETALHE_FRONTEND.md.

Incremento frontend de cadastro em22/10/2026: /cadastro publica, POST apenas nome/email/senha sem Bearer/cookies/confirmacao/perfil; servidor continua fixando USUARIO. Validacao dos limites existentes, inclusive72bytesUTF8, senha sem alteracao e limpeza dos campos sensiveis. Sucesso com link para login, sem autenticar automaticamente; erros/resultados incertos tratados sem retry automatico.239 testes frontend/type-check/build aprovados (48novos). Guia ../UNESC-Share-Frontend/docs/CADASTRO_FRONTEND.md.

Incremento frontend Meus materiais em22/10/2026: /meus-materiais protegida, menu/atalho conta, GET /materiais/meus com autorJWT no servidor e quatro estados, texto/estado e pagina20/50/100. Somente consulta/links detalhe, sem escritas/restauracao. PagedModel compartilhado preserva catalogo PUBLICADO-only.292 testes frontend/type-check/build aprovados (53novos). Guia ../UNESC-Share-Frontend/docs/MEUS_MATERIAIS_FRONTEND.md.

Incremento frontend de Magnets em22/10/2026: /materiais/novo protegida, formulario de metadados/seletores/1..10Magnets e POSTJSON atomico existente, autorJWT/PENDENTE servidor. Sucesso com links/limpeza de draft, sem publicacao automatica; resultado incerto pede conferir Meus materiais antes de reenviar, sem retry.342 testes frontend/type-check/build aprovados. Guia ../UNESC-Share-Frontend/docs/CADASTRO_MATERIAL_MAGNET_FRONTEND.md.

Incremento frontend com .torrent em23/10/2026: mesmo formulario oferece alternativamente um .torrent v1 de1byte a2MiB. POSTmultipart atomico com materialBlobJSON/arquivoFile, boundary nativo, Bearer/401 compartilhado, sem Material vazio/cadastro misto/conteudo academico.385 testes frontend/type-check/build aprovados. Guia ../UNESC-Share-Frontend/docs/CADASTRO_MATERIAL_TORRENT_FRONTEND.md.

Incremento frontend de edicao em23/10/2026: /materiais/:id/editar protegida, links autor em PENDENTE/PUBLICADO, referencias porUUID fora da primeira100, PUTso quatro metadados/retornoPENDENTE/mecanismos preservados. SemPUTsemalteracao/ETag/versao/retryautomatico.426 testes frontend/type-check/build aprovados. Guia ../UNESC-Share-Frontend/docs/EDICAO_MATERIAL_FRONTEND.md.

Incremento frontend mais recente em23/10/2026: /materiais/:id/remover protegida, GETalvo+checkbox/botaoexplicito soautor PENDENTE/PUBLICADO/BLOQUEADO, semDELETEaoabrir/cancelar.204estrito semJSON, confirmacaoconsumida porUUID/tentativa, jaREMOVIDOobservado informa semnovoDELETE. Incerto bloqueianovarequisicao ateGET+novaconfirmacao; semretry/restauracao/expurgo fisico, registro/mecanismos/hashes preservados.466 testes frontend/type-check/build aprovados (40novos), HTTPsinteticoemRAM/confirmacao/DELETEBearer204/erros/reconsulta eguardas memory history, Vite200/fallback/componente compilado. Semnavegador: visual/mobile/DOM/E2E Spring pendentes. NenhumDELETEcontraSpring/bancoreal, Java/schema/dependencia/Maven/Docker/backend iniciado/.env real/credencial/dado real alterado/publicacao/commit/remoto/push. Guia ../UNESC-Share-Frontend/docs/REMOCAO_MATERIAL_FRONTEND.md; proximorecomendado comentariosPUBLICADO/listagem-cadastro.

Incremento frontend de comentarios em23/10/2026: detalhes PUBLICADO incluem listagem paginada20/50/100/ASC e cadastro de texto simples<=4000, autorJWT/ATIVO servidor. Sem excecao para dono/equipe por visibilidade privada. Sucesso limpa texto/recarrega pagina0; resultado incerto exige GETnovo e acao explicita com aviso de duplicidade antes de outro POST. 509 testes frontend aprovados (43novos), type-check/build aprovados; HTTP sinteticoRAM/Vite200/componentes compilados, sem navegador para visual/mobile/DOM/E2E. Sem Java/schema/dependencia/credencial/dado real/Maven/Docker/.env real/publicacao/Git nesta entrega; Maven registrado549 nao reexecutado. Guia [Comentarios no frontend](../UNESC-Share-Frontend/docs/COMENTARIOS_FRONTEND.md). Proximo recomendado edicao/remocao de comentarios proprios.

Incremento frontend mais recente23/10/2026: comentarios proprios carregados oferecem edicao e revisao de remocao logica. Sem excecao ao donoMaterial/equipe; PUTso conteudo/ATIVO/PUBLICADO, DELETEconhecido comcheckbox/botaoexplicito e204semcorpo. Preserva registro/texto semrestauracao; nao cria consulta privada. Umaacao porvez, novoDraftpreservado,409/incerto exigem fechar/atualizar antesdenovaselecao, semretry/versao. npm run verify com555 testes frontend (46novos), type-check/build aprovados; HTTPsinteticoRAM/Vite200componentes, semnavegador visual/mobile/DOM/foco/E2Ependentes. SemJava/schema/dependencia/Maven/Docker/.envreal/credencial/dado real/publicacao/Git; Maven549 nao reexecutado. Guia [Edicao/remocao de comentarios no frontend](../UNESC-Share-Frontend/docs/COMENTARIOS_EDICAO_REMOCAO_FRONTEND.md). Proximo recomendado avaliacoes: resumo/cadastro/consulta propria.

Incremento frontend vigente23/10/2026: avaliacoes em detalhes PUBLICADO com resumo/cadastro/consulta propria, nota0..5/ate2casas e semautoavaliacao inclusive equipe. Inputvirgula-ponto/centesimosinteiros semarredondar/JSONsomente nota numerica/exibicao pt-BR2casas; media soAPI/zero!=ausencia.404/minha exige/resumovalido antesdeoferecercadastro; doisGETs nao snapshot, APIrevalidaPOST.409/incerto exigem consultar+preparar explicito paraoutratentativa, semretry/PUTautomatico. npm run verify com612 testes frontend (57novos), type-check/build aprovados; HTTPRAM/Vite200componentes, semnavegador visual/mobile/decimal/teclado/E2Ependentes. SemJava/schema/dependencia/Maven/Docker/.envreal/credencial/dadoreal/publicacao/Git; Maven549 nao reexecutado. Guia [Avaliacoes no frontend](../UNESC-Share-Frontend/docs/AVALIACOES_FRONTEND.md). Proximo recomendado edicao/remocao da propria avaliacao.

Incremento frontend vigente23/10/2026: edicao/exclusao fisica da propria avaliacao por/minha, sem escolher nota de terceiro. PUTso nota/semupsert/invariantesUUID-data, DELETE204semcorpo comcaixa/botaoexplicito econfirmacao consumida; pode retirar seMaterialficarprivadoaposselecao, semabrirGETprivado. /minha atingenota atual, recriadaemoutraaba pode ser afetada, semETag/versao/retry;409/incerto exigem consultar antesdenovaacao. Exclusaofisica somenteAvaliacao/semrestauracao, Material/Comentario/mecanismos preservados. npm run verify com659 testes frontend (47novos), type-check/build aprovados; HTTPRAM/Vite200componentes/servidorencerrado, semnavegador visual/mobile/DOM/foco/E2Ependentes. SemJava/schema/dependencia/Maven/Docker/.envreal/credencial/dadoreal/publicacao/Git; Maven549 nao reexecutado. Guia [Edicao/exclusao da avaliacao no frontend](../UNESC-Share-Frontend/docs/AVALIACOES_EDICAO_REMOCAO_FRONTEND.md). Proximo recomendado denuncias cadastro/acompanhamento proprio.

Incremento frontend vigente23/10/2026: denuncias comcadastroemMaterialPUBLICADO e acompanhamento proprio, rotas /materiais/:id/denunciar,/minhas-denuncias,/denuncias/:id. POSTso motivo-descricao<=2000/autorJWT/PENDENTE/naoalteraMaterial; todosautenticadospodemrelatarinclusiveproprioMaterial. Lista20/50/100/todosestados/detalheproprio inclusive aposMaterialprivado, semN+1/dadosprivadosMaterial/excecaoadmin-dono.409/incerto bloqueiamreenvio nainstancia e orientamconferirrelatos antesreabrir, semretryautomatico/edicao-exclusao-cancelamento-anexos-moderacao. npm run verify com719 testes frontend (60novos), type-check/build aprovados; HTTPRAM/Routermemoria/Vite200componentes/servidorencerrado, semnavegador visual/mobile/DOM/foco/E2Ependentes. SemJava/schema/dependencia/Maven/Docker/.envreal/credencial/dadoreal/publicacao/Git; Maven549 nao reexecutado. Guia [Denuncias no frontend](../UNESC-Share-Frontend/docs/DENUNCIAS_FRONTEND.md). Proximo recomendado area de moderacao, primeiro fila de materiais pendentes para equipe.

Incremento frontend vigente25/10/2026: fila so-leitura /moderacao/materiais para MODERADOR/ADMINISTRADOR, perfilde/usuarios/me/guardasVueRouter/atalhosdaequipe. GET/materiais/moderacao explicitoPENDENTE/page20-50-100/dataCadastro-idDESC/linksdetalheexistente, semmutacoes/N+1/filtros extras. Trocaobservadaconta-perfil/401-403retiramfila, APIcontinuaautoridade peloBearer; semrefresh/revogacaoimediata. npm run verify com759testes frontend (40novos), type-check/build aprovados; HTTPRAM/Routermemoria/Vite200componentes/servidorencerrado, semnavegador visual-mobile-DOM-teclado-E2Ependentes. SemJava/schema/permissaoAPI/dependencia/Maven/Docker/.envreal/credencial/dadoreal/publicacao/Git; Maven549 nao reexecutado. Guia [Fila de moderacao no frontend](../UNESC-Share-Frontend/docs/MODERACAO_MATERIAIS_FRONTEND.md). Proximo prepararrevisaoindividual/definirautoaprovacao antespublicacao-bloqueio.

Entrega frontend mais recente (25/10/2026): revisão individual somente de leitura em `/moderacao/materiais/:id` para equipe, aberta pela fila. Reutiliza o leitor de Material com descarte de dados/URIs/tarefas ao trocar conta/perfil ou perder acesso; oculta ações de autor/interações nesta rota e preserva o detalhe comum. `npm run verify`: **796 testes**, TypeScript e build aprovados. Validação em navegador/E2E permanece pendente. Publicação/bloqueio ainda não integrados: a escolha de autoaprovação foi solicitada (proibir ambos, permitir só administrador ou manter ambos) e aguarda resposta; a API atual permanece permitindo ambos. Sem mudanças Java/banco ou execução Maven/Docker. Guia: [Revisão individual no frontend](../UNESC-Share-Frontend/docs/REVISAO_MATERIAL_FRONTEND.md).

Entrega vigente em25/10/2026: publicação/bloqueio com confirmação explícita na revisão individual do frontend. **Autoaprovação mantida para ambos os perfis**, conforme escolha do usuário; autorização existente da API preservada. Confirmação consumida por tentativa, resultado incerto exige nova consulta, sucesso faz GET sem repetir POST. `npm run verify`: **828 testes frontend**, TypeScript/build aprovados. `./mvnw -Dtest=MaterialFluxoApiTests test`: **20 testes backend** aprovados, incluindo dois novos casos de autoaprovação, com PostgreSQL e rollback. Última suíte Maven completa549 permanece histórica. Sem Java de produção/schema/dependências novos, decisões em dados reais ou Docker. Validação visual/E2E pendente por indisponibilidade de navegador. Estrutura, código e execução: [Decisões de Material no frontend](../UNESC-Share-Frontend/docs/DECISOES_MATERIAL_FRONTEND.md). Próximo recomendado: consulta da fila de denúncias da equipe.

Entrega frontend mais recente em25/10/2026: **fila de denúncias da equipe** em `/moderacao/denuncias`, somente leitura para MODERADOR/ADMINISTRADOR. Filtros pelos quatro estados, paginação20/50/100, mais antigas primeiro; resumo sem descrição/identidade do denunciante ou chamadas adicionais por Material. Atalho na conta e entre filas. Reutiliza paginação da equipe com descarte de dados/respostas antigas; regras de conflito nas futuras ações de denúncia permanecem distintas da autoaprovação de Material. `npm run verify`: **856 testes**, TypeScript/build aprovados. Visual/teclado/E2E pendentes por falta de navegador. Sem mudanças Java/banco, Maven ou Docker nesta entrega. Guia: [Fila de denúncias da equipe no frontend](../UNESC-Share-Frontend/docs/FILA_DENUNCIAS_EQUIPE_FRONTEND.md). Próximo recomendado: detalhe e histórico da equipe, somente leitura.

Entrega frontend mais recente em25/10/2026: **detalhes e histórico de denúncia da equipe** em `/moderacao/denuncias/:id`, acessível pela fila. Relato completo e eventos com justificativa/transição/data/UUID do responsável, sem consultas adicionais a contas ou Material. Somente leitura; consultas independentes, paginação20/50/100, descarte conjunto após401/403/404 e proteção contra respostas antigas. `npm run verify`: **890 testes**, TypeScript/build aprovados. Sem mudanças Java/banco, Maven ou Docker nesta entrega; visual/teclado/E2E pendentes por falta de navegador. Guia: [Detalhe e histórico no frontend](../UNESC-Share-Frontend/docs/DENUNCIA_HISTORICO_EQUIPE_FRONTEND.md). Próximo recomendado: iniciar análise com justificativa e confirmação explícita; conclusões depois.

Entrega frontend vigente25/10/2026: **iniciar análise de denúncia** com justificativa e confirmação, endpoint existente.921 testes frontend/TypeScript/build aprovados.403 de mutação bloqueia ação e preserva leitura autorizada; conflito/incerteza exige nova consulta. Sem alterações Java/banco, Maven ou Docker. Visual/DOM/foco/E2E pendentes. Guia: [Início de análise](../UNESC-Share-Frontend/docs/INICIO_ANALISE_DENUNCIA_FRONTEND.md). Próximo: conclusões procedente/improcedente.

Entrega frontend vigente25/10/2026: **conclusão procedente/improcedente de denúncias EM_ANALISE**, com justificativa e confirmação específica, sem alterar Material automaticamente. Ciclo compartilhado com início; impedimentos e tratamento de conflito/incerteza preservados. **945 testes frontend**, TypeScript/build aprovados. Sem Java/banco/Maven/Docker nesta entrega; visual/DOM/foco/E2E pendentes. Guia: [Conclusão de denúncias](../UNESC-Share-Frontend/docs/CONCLUSAO_DENUNCIA_FRONTEND.md). Próximo: consulta de comentários para moderação por Material.

Entrega frontend vigente25/10/2026: **consulta de comentários da equipe por Material**, somente leitura de ATIVO/OCULTO em qualquer estado do Material, sem textoREMOVIDO. Seção comum preservada em ATIVO/PUBLICADO; paginação e descarte de textos por contexto/acesso. **968 testes frontend**, TypeScript/build aprovados. Sem Java/banco/Maven/Docker nesta entrega; visual/DOM/E2E pendentes. Guia: [Comentários da equipe](../UNESC-Share-Frontend/docs/COMENTARIOS_EQUIPE_FRONTEND.md). Próximo: histórico de comentário selecionado.

Entrega frontend vigente25/10/2026: **histórico de moderação de comentário selecionado**, somente leitura. Abrir descarta a página textual e mantém UUID/eventos; histórico continua após remoção lógica sem recuperar texto. Voltar consulta a lista atual, com descarte de respostas antigas e erros de acesso. **993 testes frontend**, TypeScript/build aprovados. Sem Java/banco/Maven/Docker; visual/DOM/foco/E2E pendentes. Guia: [Histórico de comentário](../UNESC-Share-Frontend/docs/HISTORICO_COMENTARIO_EQUIPE_FRONTEND.md). Próximo: ocultação com motivo/confirmação.

Entrega frontend de26/10/2026: **ocultação de comentário ATIVO pela equipe**, com motivo e confirmação explícita. Reutiliza o endpoint existente, aceita comentário próprio da equipe em qualquer estado do Material e preserva a consulta comum ATIVO/PUBLICADO. Sucesso reconsulta a lista; conflito/resultado incerto exige descarte e nova consulta, sem repetição automática. **1021 testes frontend**, TypeScript/build aprovados; build final repetido após ajustes de formulário/foco. Sem mudanças Java/banco, Maven, Docker ou dados reais. Visual/DOM/E2E pendentes por falta de navegador. Estrutura, código e roteiro: [Ocultação de comentário no frontend](../UNESC-Share-Frontend/docs/OCULTACAO_COMENTARIO_FRONTEND.md). Próximo: reexibição de OCULTO somente em Material PUBLICADO. Os registros anteriores de entregas frontend são históricos.

Entrega frontend mais recente26/10/2026: **reexibição de comentário OCULTO em Material PUBLICADO**, com motivo e confirmação específicos. Ciclo/formulário compartilhados com ocultação, sem modificar permissões ou o Material; estado vem do detalhe já consultado e a API revalida cada POST. **1044 testes frontend** (23 novos), TypeScript/build aprovados;1021 regressões preservadas. Sem Java/banco/Maven/Docker ou dados reais nesta entrega; visual/DOM/E2E pendentes por falta de navegador. Estrutura, código e roteiro: [Reexibição de comentário](../UNESC-Share-Frontend/docs/REEXIBICAO_COMENTARIO_FRONTEND.md). Próximo recomendado: validar integração real de comentários/moderação e o roteiro de navegador quando disponível.

Validação integrada em26/10/2026: **551 testes backend**, **1044 testes frontend**/TypeScript/build e **2 cenários opcionais frontend→Spring HTTP real→PostgreSQL** aprovados. Os cenários usam services/modelo reais do frontend, JWTs e fixtures exclusivos de teste, com limpeza porUUID conferida; cobrem comentários, decisões, histórico, permissões, CORS e conflito após mudança do Material. Não montam Vue/DOM: visual/teclado/E2E em navegador continuam pendentes. Sem Java de produção/schema/dependências/Docker ou uso de dados reais no roteiro. Comandos, arquivos e limites: [Validação integrada](docs/VALIDACAO_INTEGRADA_COMENTARIOS.md). Próximo incremento recomendado: adição de Magnets aos próprios materiais no frontend, usando a API existente e retorno a PENDENTE.

Entrega frontend26/10/2026: **adicionar Magnets aos próprios materiais**, por tela com saldo e confirmação do retorno a PENDENTE. Só autoria em PENDENTE/PUBLICADO, lote dentro do limite10, sem exceção de equipe; conflito/incerteza exige nova consulta, sem retry. **1077 testes frontend**, TypeScript/build, **54 testes backend focados** e **2 cenários opcionais SpringHTTP/PostgreSQL reais** aprovados; fixtures limpas. Sem Java de produção/schema/dependências/Docker ou uso de dados reais. Visual/DOM/E2E pendentes sem navegador. Estrutura, código e execução: [Adição de Magnets no frontend](../UNESC-Share-Frontend/docs/ADICAO_MAGNETS_FRONTEND.md). Próximo: adição de `.torrent` próprio via API existente. Registros anteriores históricos.

Entrega frontend26/10/2026: **adição de .torrent próprio**, um metainfo v1 de até2MiB, saldo10 e confirmação do retorno a PENDENTE. Multipart sóarquivo e recibo de Torrent validado sem seguir Location; conflito/incerteza exige reconsulta, sem retry. **1110 testes frontend**/TypeScript/build, **79 backend focados** e **2 cenários opcionais upload/download SpringHTTP/PostgreSQL reais** aprovados, com limpeza de registros/arquivos/diretório exclusivos de teste. Sem Java de produção/schema/dependências/Docker ou dados reais. Visual/DOM/FilePicker/E2E pendentes sem navegador. Código/execução: [Adição de .torrent no frontend](../UNESC-Share-Frontend/docs/ADICAO_TORRENT_FRONTEND.md). Próximo módulo sugerido: consulta de instituições em área administrativa acadêmica, preservando escrita exclusiva de ADMINISTRADOR. Registros anteriores históricos.

Entrega frontend26/10/2026: **consulta de instituições**, lista paginada e detalhe porUUID em `/instituicoes` e `/:id`, disponíveis para autenticados dos três perfis. Sem formulários de escrita; permissão administrativa existente preservada. **1135 testes frontend**/TypeScript/build, **21 backend focados** e **2 cenários opcionais SpringHTTP/PostgreSQL reais** aprovados; fixtures e storage temporário limpos. Sem Java produção/schema/dependências/Docker ou uso de dados reais no trecho novo. Visual/DOM/E2E pendentes sem navegador. Código/execução: [Consulta de instituições no frontend](../UNESC-Share-Frontend/docs/INSTITUICOES_CONSULTA_FRONTEND.md). Próximo: cadastro de instituições sóADMINISTRADOR; edição/exclusão depois. Registros anteriores históricos.

Entrega frontend26/10/2026: **cadastro de instituições por ADMINISTRADOR**, em `/instituicoes/nova`, com nome/sigla, guarda administrativa, validação e tratamento de duplicidade/incerteza sem reenvio automático. Leitura dos três perfis preservada. **1166 testes frontend**/TypeScript/build, **21 backend focados** e **2 cenários opcionais SpringHTTP/PostgreSQL** aprovados; instituição adicional de teste e demais fixtures limpas. Sem Java produção/schema/dependências/Docker ou dados reais. Visual/DOM/E2E pendentes sem navegador. Código/execução: [Cadastro administrativo no frontend](../UNESC-Share-Frontend/docs/INSTITUICAO_CADASTRO_FRONTEND.md). Próximo: edição administrativa com consulta inicial; exclusão depois. Registros anteriores históricos.

Entrega frontend26/10/2026: **edição de instituições por ADMINISTRADOR** em `/instituicoes/:id/editar`. Começa com GET por UUID e envia PUT completo somente nome/sigla normalizados; não envia se inalterado.409 e resultado incerto exigem GET explícito antes de novo PUT; sem retry. **1180 testes frontend**, TypeScript/build aprovados; HTTP sintético, sem validação visual/DOM ou integração Spring/PostgreSQL nesta entrega. Java/schema/dependências não alterados. Guia: [Edição administrativa no frontend](../UNESC-Share-Frontend/docs/INSTITUICAO_EDICAO_FRONTEND.md). Próximo: exclusão ADMIN com409 se cursos vinculados e sem cascata. Registros anteriores históricos.

Entrega frontend26/10/2026: **exclusão física de instituições por ADMINISTRADOR** em `/instituicoes/:id/excluir`, com GET antes de confirmação explícita, DELETE por UUID e sucesso somente em204.409 mantém a regra sem cascata para cursos vinculados; resultado incerto exige nova consulta e confirmação, sem repetição automática. **1197 testes frontend**, TypeScript/build e **21 testes Maven focados com PostgreSQL local** aprovados. O HTTP sintético não integra a tela Vue ao Spring; Java/schema não alterados e sem Docker. Guia: [Exclusão no frontend](../UNESC-Share-Frontend/docs/INSTITUICAO_EXCLUSAO_FRONTEND.md). Próximo: validação visual/manual quando navegador estiver disponível e CRUD administrativo de cursos. Registros anteriores históricos.

Entrega frontend26/10/2026: **consulta de cursos por instituição** em `/instituicoes/:id/cursos`, leitura para os três perfis autenticados, sem mutações. GET da instituição antecede listagem paginada filtrada por UUID. **1208 testes frontend**, TypeScript/build e **27 testes Maven focados no PostgreSQL local** (21 API+6 serviço) aprovados. HTTP frontend sintético, sem integração Vue/Spring ou validação visual. Java/schema de produção não alterados e sem Docker. Guia: [Consulta de cursos no frontend](../UNESC-Share-Frontend/docs/CURSOS_CONSULTA_FRONTEND.md). Próximo: cadastro de cursos apenas ADMINISTRADOR.

## Instituicoes

O primeiro incremento dos CRUDs academicos disponibiliza `/api/v1/instituicoes`:

- GET da lista paginada e GET por UUID para qualquer usuario autenticado.
- POST, PUT por UUID e DELETE por UUID somente para `ADMINISTRADOR`.
- Exclusao bloqueada com `409 Conflict` quando existem cursos vinculados, sem cascata.
- Validacao de nome/sigla, normalizacao de sigla e verificacao de duplicidades.

Essa restricao e para a estrutura academica, nao para o futuro cadastro de materiais pelos estudantes.
O cadastro publico continua gerando somente `USUARIO`; o administrador inicial pode ser provisionado apenas em `dev`, por ativacao explicita e credenciais locais.
Estrutura dos arquivos, contrato, exemplos e testes estao em [docs/INSTITUICOES.md](docs/INSTITUICOES.md).

## Cursos

O segundo incremento academico disponibiliza `/api/v1/cursos`: listagem paginada com filtro opcional por `instituicaoId`, consulta por UUID e CRUD administrativo.
O cadastro exige instituicao existente. Nomes duplicados sao verificados dentro da mesma instituicao; o mesmo nome em outra instituicao e permitido.
A edicao preserva o vinculo institucional. Cursos com disciplinas nao podem ser excluidos (`409`), sem cascatas ou mudancas no schema.
Arquivos, contrato, exemplos e testes estao em [docs/CURSOS.md](docs/CURSOS.md).

## Disciplinas

O terceiro incremento academico disponibiliza `/api/v1/disciplinas`: lista paginada com filtro opcional por `cursoId`, consulta por UUID e CRUD administrativo.
O cadastro exige curso existente e codigo unico dentro dele. A API normaliza codigos para maiusculas, com ate 30 caracteres, sem limitar o formato ao da UNESC.
A edicao preserva o curso e o UUID; a exclusao e bloqueada (`409`) por qualquer material vinculado, inclusive `REMOVIDO`, sem cascatas.
Arquivos, regras, contrato e testes estao em [docs/DISCIPLINAS.md](docs/DISCIPLINAS.md).

## Categorias

O primeiro incremento da Etapa 5 disponibiliza `/api/v1/categorias`, com consulta paginada/por UUID para autenticados e POST, PUT e DELETE apenas para administradores.
Nome obrigatorio de ate 80 caracteres e descricao opcional de ate 500, com verificacao de duplicidade sem distinguir caixa.
Editar preserva UUID e materiais; excluir e bloqueado com `409` quando existe qualquer material vinculado, inclusive `REMOVIDO`.
O populador identifica categorias iniciais pelo nome: desative `SEED_ENABLED` antes de administrar esses nomes definitivamente para evitar reposicao em outra inicializacao.
Estrutura dos arquivos, regras e testes: [docs/CATEGORIAS.md](docs/CATEGORIAS.md).

## Materiais e Magnets

O segundo incremento da Etapa 5 implementa `/api/v1/materiais`, iniciando pelos Magnets conforme aprovado:

- Cadastro por usuarios autenticados, com autor do JWT e de 1 a 10 Magnets validos na mesma transacao; estado inicial PENDENTE.
- Pesquisa paginada de PUBLICADO por texto/instituicao/curso/disciplina/categoria, consulta dos proprios materiais e fila restrita a moderadores/administradores.
- Edicao apenas pelo autor em PENDENTE/PUBLICADO; publicado editado volta a PENDENTE. Bloqueados/removidos nao sao editaveis.
- Publicacao/bloqueio pela equipe e remocao logica pelo autor, preservando relacionamentos.
- Validacao de hashes v1 (hex/Base32) e v2 SHA-256; sem consultar trackers ou baixar conteudo. Hibridos com varios xt nao sao suportados ainda.

O cadastro inicial aceita JSON com Magnets ou multipart com metadados JSON e um `.torrent`, conforme a entrega seguinte. Nunca ha upload de conteudo academico.
Arquivos, contrato, transicoes, limites e testes: [docs/MATERIAIS.md](docs/MATERIAIS.md).

## Arquivos .torrent

O primeiro incremento da Etapa 6 permite ao autor adicionar `.torrent` v1 a Material existente em PENDENTE/PUBLICADO:

- POST multipart em `/api/v1/materiais/{materialId}/torrents`, campo unico `arquivo`, ate 2 MiB e 10 Torrents por Material; sucesso retorna o material a PENDENTE.
- GET autenticado em `/api/v1/materiais/{materialId}/torrents/{torrentId}`, com visibilidade igual a dos detalhes do Material e sem caminhos locais na resposta.
- Validacao estrutural de bencoding/metainfo, hash SHA-1 dos bytes originais de info e armazenamento privado com chave UUID. V2/hibridos retornam 400 neste incremento.
- Torrent e Magnet com o mesmo hash sao permitidos no mesmo Material; outro Material recebe 409. Locks transacionais PostgreSQL coordenam os dois fluxos da API.
- `TORRENT_STORAGE_DIR` opcional, padrao `./data/torrents`, ignorado no Git. Nao servir esse diretorio como recurso publico; preserve-o junto do banco no deploy.

Rollback tenta limpar o arquivo criado; filesystem e banco nao sao uma transacao distribuida e falhas abruptas exigem reconciliacao operacional. Suporte v2/hibrido fica para entregas posteriores. Nenhum conteudo academico e baixado, extraido ou hospedado.
Estrutura dos arquivos, configuracao, exemplos, regras de seguranca e testes: [docs/TORRENTS.md](docs/TORRENTS.md).

O segundo incremento permite o cadastro inicial somente com `.torrent` em `POST /api/v1/materiais`, usando multipart com duas partes: `material` (application/json com titulo/descricao/disciplinaId/categoriaId) e `arquivo` (um .torrent v1). O Material nasce PENDENTE, com autor do JWT e sem Magnets; erro reverte Material/Torrent e tenta limpar o arquivo criado. O contrato JSON com 1..10 Magnets permanece compativel.
Usa `@RequestPart`, Bean Validation e resolucao multipart antecipada nativos do Spring Web, preservando 413 para excesso de tamanho. Sem novos campos/schema/dependencias/variaveis. Contrato completo, caminhos dos arquivos, exemplo curl e testes: [docs/CADASTRO_MATERIAL_TORRENT.md](docs/CADASTRO_MATERIAL_TORRENT.md).

## Adicao de Magnets

`POST /api/v1/materiais/{materialId}/magnets` aceita JSON com lista `magnetUris` para o autor adicionar um lote atomico de 1..10 URIs a Material PENDENTE/PUBLICADO, com limite acumulado de 10 Magnets. Sucesso retorna 200 com detalhe atualizado e coloca o Material em PENDENTE; metadados/mecanismos anteriores sao preservados.
Reutiliza parser e lock de hash; Torrent+Magnet podem compartilhar hash no mesmo Material, mas segundo Magnet igual ou hash de outro Material retorna409. Bloqueados/removidos nao aceitam adicao; equipe nao impersona autor. Sem edicao/remocao de mecanismos, novos campos ou uploads. Estrutura, contrato, exemplo e testes em [docs/MAGNETS.md](docs/MAGNETS.md).

## Comentarios

Primeiro incremento da Etapa 8: POST/GET em `/api/v1/materiais/{materialId}/comentarios`, somente para autenticados e materiais PUBLICADO. Cadastro fixa autor pelo JWT, estado ATIVO e texto obrigatorio ate4000caracteres; publicacao imediata, sem alterar o Material. Listagem paginada retorna somente ATIVO, em ordem cronologica/id, size1..100 (padrao20).
Na listagem/cadastro, materiais nao publicados retornam404 tambem para dono/equipe; comentarios anteriores sao preservados no banco. DTOs nao expoem nome/email/senha do autor, somente UUID. Texto e JSON simples, nao HTML a renderizar. Arquivos, regras, exemplos e testes iniciais: [docs/COMENTARIOS.md](docs/COMENTARIOS.md).
Segundo incremento: PUT/DELETE em `/api/v1/materiais/{materialId}/comentarios/{comentarioId}`, somente autor. PUT exige ATIVO/PUBLICADO e preserva UUID/data/estado; DELETE logico funciona em qualquer estado do comentario/material, preserva texto/registro e retorna204 inclusive na repeticao. Dono/equipe nao impersonam autor; GET comum nao foi ampliado. Sem novos campos/schema/dependencias nesse incremento. Contrato, arquivos e testes de rollback real: [docs/COMENTARIOS_EDICAO_REMOCAO.md](docs/COMENTARIOS_EDICAO_REMOCAO.md).

Terceiro incremento aprovado: equipe MODERADOR/ADMINISTRADOR oculta ATIVO e reexibe OCULTO somente em Material PUBLICADO, com motivo obrigatorio <=1000 e auditoria na mesma transacao. GET `/moderacao` na colecao mostra ATIVO/OCULTO de qualquer estado do Material, nunca REMOVIDO; GET `/{comentarioId}/historico-moderacao` retorna apenas metadados das decisoes para equipe, inclusive apos remocao. POST `/{comentarioId}/ocultacao` e `/reexibicao` retornam200, repeticao/transicao invalida409; nao restauram REMOVIDO nem editam texto. Nova entidade/tabela ModeracaoComentario, UUID/FKs/UTC, sem endpoints de edicao/exclusao de historico. Imutabilidade na aplicacao/ORM nao impede SQL administrativo. Estrutura, rotas, exemplos, limites e testes: [docs/COMENTARIOS_MODERACAO.md](docs/COMENTARIOS_MODERACAO.md).

## Avaliacoes

POST `/api/v1/materiais/{materialId}/avaliacoes` cadastra nota decimal0,00a5,00 com ate duas casas, autor doJWT e retorno201/Location de `/minha`. Qualquer perfil pode avaliar Material PUBLICADO de outro usuario; autoavaliacao403 e segundo cadastro409, sem editar a nota anterior. GET `/minha` retorna somente a propria nota; GET `/resumo` retorna media/quantidade, sem notas individuais de terceiros. Todas as consultas exigem PUBLICADO, sem excecao para dono/equipe.
Resumo calcula SUM/COUNT em decimal e divide com escala2/HALF_UP; sem notas media=null/quantidade0, nota zero participa normalmente. Notas enviadas com trescasas sao400, nunca arredondadas. Lock de Material e constraint unica coordenam cadastros; sem entidades/schema/dependencias/variaveis novas. Contrato, arquivos, exemplos e testes: [docs/AVALIACOES.md](docs/AVALIACOES.md).
Segundo incremento aprovado: PUT/DELETE em `/api/v1/materiais/{materialId}/avaliacoes/minha`. PUT edita somente propria nota em PUBLICADO, sem autoavaliacao, preservandoUUID/data; DELETE remove fisicamente somente propria nota em qualquer estado do Material,204inclusive ausencia/repeticao. Equipe nao escolhe nota de terceiros. Media/quantidade refletem mudancas; novoPOST apos exclusao cria novoUUID/data. LocksMaterial->Avaliacao, sem historico/restauracao/cascatas/schema novos. Contrato, arquivos, testes e limites: [docs/AVALIACOES_EDICAO_REMOCAO.md](docs/AVALIACOES_EDICAO_REMOCAO.md).

## Denuncias

POST `/api/v1/materiais/{materialId}/denuncias` cadastra relato PENDENTE, motivo e descricao obrigatorios (ate2000), denunciante doJWT e MaterialPUBLICADO, inclusive proprio Material. Uma aberta (PENDENTE/EM_ANALISE) por usuario/Material, independentemente do motivo; duplicidade409, concluida permite novo relato. Denunciar nao altera/bloqueia Material.
GET `/api/v1/denuncias/minhas` paginado e GET `/api/v1/denuncias/{denunciaId}` retornam somente relatos proprios, inclusive depois de Material deixar de ser publicado. Essas rotas nao possuem excecao para dono/equipe; DTO nao expoe dados pessoais nem detalhes do Material privado. Sem edicao/exclusao/cancelamento. Protocolo transacional com lock de Material coordena uma aberta na API; nao foi criado indice unico parcial nem garantia para SQL externo que ignore o protocolo. Sem entidades/schema/dependencias/variaveis novas nesse primeiro incremento. Contrato, estrutura, testes e limites: [docs/DENUNCIAS.md](docs/DENUNCIAS.md).

Segundo incremento aprovado: `/api/v1/denuncias/moderacao` e subrotas somenteMODERADOR/ADMINISTRADOR para fila/detalhes/historico e POSTanalise/procedencia/improcedencia. Fluxo PENDENTE->EM_ANALISE->PROCEDENTEouIMPROCEDENTE, semreabertura/repeticao (409) e comjustificativa obrigatoria<=1000. Denunciante/autor doMaterial nao podem agir, inclusiveADMINISTRADOR403; outra equipe elegivel e necessaria. Nova entidade/tabelaModeracaoDenuncia comresponsavel/estados/justificativa/UTC gravada na mesma transacao, sem escritaHTTP dohistorico. Nenhum efeitoautomatico noMaterial; consultasproprias seguem inalteradas. Guia completo: [docs/DENUNCIAS_MODERACAO.md](docs/DENUNCIAS_MODERACAO.md).

## Administrador inicial

O runner administrativo e independente do catalogo e fica desativado por padrao (`ADMIN_SEED_ENABLED=false`).
Somente com `dev` ativo e a flag true, le `ADMIN_NAME`, `ADMIN_EMAIL` e `ADMIN_PASSWORD` do ambiente, valida e cria uma nova conta com BCrypt.
Se o email ja for de administrador, preserva a conta. Se for de usuario/moderador, interrompe sem promover nem trocar senhas.
Nao existem credenciais padrao, endpoint de promocao ou alteracao automatica do `.env` real.
Depois da primeira carga, desative o runner e retire as credenciais de bootstrap do arquivo/ambiente.
Configuracao completa, estrutura dos arquivos, seguranca e testes estao em [docs/ADMINISTRADOR_INICIAL.md](docs/ADMINISTRADOR_INICIAL.md).
Em 20/10/2026, apos configuracao local e autorizacao do usuario, o backend foi iniciado pelo Maven e o administrador foi criado. Login, perfil administrativo e acesso autenticado foram validados via HTTP, sem exibir credenciais ou tokens.

## Dados iniciais de desenvolvimento

Ao iniciar com o perfil `dev`, um `CommandLineRunner` chama um servico transacional que cadastra:

- Instituicao `Universidade do Extremo Sul Catarinense`, com sigla `UNESC`.
- Curso `Ciência da Computação`, conforme a Matriz Curricular 6 de `docs/GRADE 6.pdf`.
- 59 disciplinas com codigos oficiais: 48 componentes semestrais (incluindo Optativa I e II) e 11 eletivas.
- Nove categorias: Livros, Apostilas, Resumos, Listas de exercícios, Provas, Apresentações, Cursos, Artigos e Outros materiais acadêmicos.

O catalogo fica em `src/main/resources/dados-iniciais/unesc-ciencia-computacao-matriz6.json`, em UTF-8, lido pelo Jackson configurado pelo Spring Boot e validado com Bean Validation.
O JSON preserva os nomes e rotulos NCI/NCC/NCA, codigos oficiais, semestres, creditos e horas do PDF.
Semestre, creditos e horas permanecem dados de referencia no arquivo, sem novas colunas JPA nesta etapa.
Nas eletivas, semestre e creditos sao nulos e as horas mantem o zero listado no documento, sem inferir obrigatoriedade ou equivalencia com as optativas.

O populador verifica instituicao pela sigla, curso pela instituicao/nome e disciplina pelo curso/codigo oficial.
Executa em uma unica transacao, preserva UUIDs e edicoes das disciplinas ja existentes e nao duplica registros na segunda inicializacao.
Somente o nome-placeholder `UNESC` e a descricao gerada da Matriz 5 (ou uma descricao nula do curso) sao atualizados para os metadados confirmados da Matriz 6; edicoes manuais diferentes sao preservadas.

O novo PDF nao traz ementas: novas disciplinas sao criadas com `descricao=null`, sem copiar as ementas da Matriz 5.
ENADE e atividades complementares nao recebem codigos inventados nem registros de disciplina.
O catalogo antigo foi removido dos recursos ativos. A remocao dos registros antigos do banco depende da execucao manual descrita em [docs/ATUALIZACAO_MATRIZ_6.md](docs/ATUALIZACAO_MATRIZ_6.md).
Se o curso ainda tiver codigos `TEMP-CC-*`, o runner interrompe a carga e solicita essa limpeza; nunca exclui registros automaticamente.

Para desativar a carga automatica, defina `SEED_ENABLED=false` no `.env` e exporte novamente as variaveis.
Fora do perfil `dev`, o runner nao e ativado. O administrador inicial possui uma flag separada e nao e criado por `SEED_ENABLED=true`.

## Testes

Com o PostgreSQL local ativo e as variaveis do `.env` exportadas:

```bash
./mvnw test
```

A suite inclui testes de catalogo da Matriz 6, carga idempotente, protecao contra mistura com a Matriz 5, preservacao de codigo/UUID/edicoes, notas decimais, unicidade de avaliacao, CHECK de intervalo via SQL, associacao de multiplos torrents/magnets e autenticacao JWT.
Os testes de integracao da carga devem ser executados apos concluir a limpeza dos registros da Matriz 5 no banco de desenvolvimento.
Os testes de integracao usam o perfil `test`, desativam o runner e os testes transacionais revertem seus dados ao finalizar cada transacao.
O teste de contexto tambem utiliza `test`, sem executar a carga inicial.
Uma chave exclusiva e publica de testes fica em `src/test/resources/application-test.properties`; ela nao faz parte do pacote de producao e nunca deve ser usada em desenvolvimento ou deploy.
O schema e gerenciado por Flyway e Hibernate roda em `ddl-auto=validate`; os testes de integracao precisam de PostgreSQL de desenvolvimento. Consulte [docs/FLYWAY.md](docs/FLYWAY.md) antes da primeira inicializacao em um banco com tabelas existentes.

Ultima verificacao local em 21/10/2026: `./mvnw clean verify` concluiu com 549 testes, sem falhas, erros ou testes ignorados, e gerou o JAR executavel. Sao seis novos casos de regressao CORS sobre os 543 anteriores: criacao201/Location, download200/Content-Disposition/bytes, respostas401/403 e rejeicao de origens/metodos/cabecalhos nao autorizados. Antes da correcao, os quatro casos de exposicao falharam pela ausencia de Access-Control-Expose-Headers; apos o ajuste, a suite completa passou. Nenhuma permissao, entidade, schema ou dependencia foi alterada neste incremento.
Os testes novos usam fixtures sinteticas e rollback, sem credenciais reais ou alteracao do catalogo Matriz6. Casos concorrentes/transacionais anteriores limpam fixtures temporariamente confirmadas por seus UUIDs exatos em finally. O pool de test permanece limitado, sem alterar o pool normal. Nenhum comando Docker foi executado e o `.env` real nao foi alterado pelo agente. A verificacao de headers por MockMvc nao substitui o teste E2E do frontend em navegador.
O backend com o ajuste CORS foi reiniciado pelo Maven em 127.0.0.1:8080, bootstrap administrativo desativado e zero disciplinas novas. HTTP real sem autenticacao confirmou401/corpo vazio com os dois headers expostos para a origem permitida, preflight200 e origem desconhecida403 sem Allow-Origin/Expose-Headers. Nao houve escrita de negocio nessas consultas. JAR sem classes/configuracao de testes e verificacao de whitespace sem erros.
A carga da Matriz 6 foi verificada em 06/10/2026: criou 59 disciplinas e a segunda inicializacao criou zero. A configuracao JWT local foi aceita.
O agente pode executar Maven; comandos Docker devem ser executados pelo usuario.

## Ambiente containerizado para apresentação

A imagem do backend constrói o JAR com Java 21/Maven; PostgreSQL usa o volume nomeado existente `unescshare-postgres-data`. O arquivo `compose.presentation.yaml` acrescenta a imagem estática do frontend, construída a partir do repositório irmão `../UNESC-Share-Frontend`. Os dois diretórios devem ficar lado a lado. O Vite compila a URL pública da API no build e o Nginx serve as rotas do Vue com fallback para `index.html`.

Com o `.env` do backend preenchido (incluindo `JWT_SECRET` válido), a partir desta pasta:

```bash
docker compose -f compose.yaml -f compose.presentation.yaml up --build
```

A apresentação local fica em `http://localhost:5173`, a API em `http://localhost:8080/api/v1` e o Swagger de desenvolvimento em `http://localhost:8080/api/v1/swagger-ui/index.html`. Para subir somente banco e backend, use `docker compose up --build`. As portas do host podem ser alteradas por `FRONTEND_PORT`, `API_PORT` e `POSTGRES_PORT`; ao mudar as portas do frontend/API, o frontend é recompilado e o CORS é ajustado pelo Compose.

Os containers publicam as portas apenas em `127.0.0.1`. O PostgreSQL espera ficar saudável antes de iniciar o backend. A migração Flyway roda na inicialização: volume vazio recebe V1 e o volume local já existente usa o baseline registrado. O `CommandLineRunner` continua separado e idempotente. Metainfo `.torrent` fica em volume próprio; não são armazenados arquivos acadêmicos.

Para encerrar sem apagar os dados, execute `docker compose -f compose.yaml -f compose.presentation.yaml down`. Não use `down -v`, pois isso remove os volumes do PostgreSQL e dos arquivos `.torrent`. Configure credenciais administrativas de bootstrap somente se for necessário criar a conta de apresentação em uma base nova; mantenha o opt-in desativado depois da criação.

Builds dos containers ainda devem ser validados no Docker do usuário; o agente não executou comandos Docker.
Roteiro de fala, sequência de telas e preparação: [docs/ROTEIRO_APRESENTACAO.md](docs/ROTEIRO_APRESENTACAO.md).

## Encerrar o banco local

```bash
docker compose down
```

Os dados permanecem no volume `unescshare-postgres-data`. Para evitar perda acidental, a remocao desse volume deve ser feita somente de forma intencional.

## Estrutura de pacotes

O codigo e organizado por funcionalidade:

```text
br.com.murkyweb.unesc_share
├── config
├── security
├── shared
├── usuario
├── academico
├── categoria
├── material
├── compartilhamento
└── interacao
```

Cada funcionalidade recebera seus proprios controllers, services, repositories, DTOs e entidades conforme for implementada.

## Diretrizes de implementacao

Priorizar recursos nativos do Spring Security e Spring Web para reduzir codigo de infraestrutura proprio e facilitar a manutencao com base na documentacao oficial.
Customizacoes serao adicionadas apenas quando necessarias para as regras do UnescShare, com justificativa documentada e testes.
A autenticacao inicial utiliza JWT de curta duracao (15 minutos), assinatura HS256 e suporte nativo do Spring Security, sem refresh token ou revogacao imediata.

## Flyway e schema do banco

O Flyway gerencia a estrutura do PostgreSQL; Hibernate valida com `ddl-auto=validate`. A migração V1 reflete o schema atual. O banco local atual já foi baselined na versão 1 sem recriar tabelas ou alterar dados. Para outras cópias locais preexistentes, consulte [docs/FLYWAY.md](docs/FLYWAY.md) antes de inicializar.
