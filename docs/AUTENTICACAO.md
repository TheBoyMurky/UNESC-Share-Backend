# Autenticacao — primeiro incremento

Este incremento implementa cadastro, login e consulta do usuario autenticado, sem avancar nos CRUDs academicos ou no compartilhamento de materiais.
O UnescShare continua sendo um catalogo BitTorrent, sem hospedagem de conteudo academico.

## Decisoes e limitacoes

- JWT Bearer com validade padrao de 15 minutos, sem refresh token. Ao expirar, fazer login novamente.
- `JWT_TTL` aceita duracoes entre 1 e 30 minutos. Emissao usa precisao de segundos.
- HS256 simplifica a operacao deste backend unico. RSA seria uma alternativa quando validadores externos precisarem verificar tokens sem poder emiti-los; nao foi adicionada essa infraestrutura neste incremento.
- Chave de assinatura exclusiva, aleatoria e persistente entre inicializacoes, fornecida por `JWT_SECRET` em Base64. O backend nao inicia com chave ausente, invalida ou menor que 32 bytes.
- Validacao nativa de assinatura HS256, emissor, audiencia, expiracao obrigatoria, inicio de validade e identidade UUID. Sem tolerancia adicional de relogio, pois este backend emite e valida os tokens.
- Claims: `iss`, `aud`, `sub` (UUID), `iat`, `nbf`, `exp` e `roles`. Nenhuma senha, hash ou email no token. JWT e assinado, nao criptografado.
- Sem revogacao individual imediata. Logout consiste em descartar o token no frontend, sem endpoint de logout. Uma copia do token permanece valida ate a expiracao.
- Alteracoes de perfil nao atualizam tokens ja emitidos. Os testes comprovam a conversao nativa de roles, nao uma matriz de permissoes completa para funcionalidades ainda inexistentes.
- `STATELESS`, sem sessao HTTP, cookie de autenticacao, Basic ou formulario de login. Tokens na query ou em cookies nao sao aceitos.
- CSRF desativado porque as credenciais nao sao enviadas automaticamente por cookies/Basic. Essa decisao precisa ser revista se o mecanismo mudar. CORS nao substitui autenticacao e autorizacao.
- Frontend deve enviar o token no header `Authorization`; preferir armazenamento em memoria neste prototipo. Uso fora de localhost requer HTTPS.
- Rate limiting, recuperacao de senha, usuarios administrativos iniciais e revogacao ainda nao foram implementados. Nao considerar este incremento suficiente para disponibilizacao publica em producao.

## Estrutura dos arquivos

Arquivos Java ficam sob `src/main/java/br/com/murkyweb/unesc_share/`:

| Arquivo | Responsabilidade |
| --- | --- |
| [security/JwtProperties.java](../src/main/java/br/com/murkyweb/unesc_share/security/JwtProperties.java) | Binding e validacao das configuracoes JWT |
| [security/SecurityConfig.java](../src/main/java/br/com/murkyweb/unesc_share/security/SecurityConfig.java) | Beans e DSL nativos de seguranca, JWT, senha e CORS |
| [security/UsuarioDetailsService.java](../src/main/java/br/com/murkyweb/unesc_share/security/UsuarioDetailsService.java) | Adaptacao de usuarios persistidos ao contrato nativo `UserDetailsService` |
| [security/dto/LoginRequest.java](../src/main/java/br/com/murkyweb/unesc_share/security/dto/LoginRequest.java) | Entrada validada do login |
| [security/dto/TokenResponse.java](../src/main/java/br/com/murkyweb/unesc_share/security/dto/TokenResponse.java) | Token, tipo, validade em segundos e expiracao UTC |
| [security/service/AuthService.java](../src/main/java/br/com/murkyweb/unesc_share/security/service/AuthService.java) | Autenticacao via `AuthenticationManager` e emissao via `JwtEncoder` |
| [security/controller/AuthController.java](../src/main/java/br/com/murkyweb/unesc_share/security/controller/AuthController.java) | Endpoints de cadastro e login |
| [usuario/dto/CadastroRequest.java](../src/main/java/br/com/murkyweb/unesc_share/usuario/dto/CadastroRequest.java) | Entrada validada e normalizada do cadastro |
| [usuario/dto/UsuarioResponse.java](../src/main/java/br/com/murkyweb/unesc_share/usuario/dto/UsuarioResponse.java) | Saida sem senha/hash |
| [usuario/service/UsuarioService.java](../src/main/java/br/com/murkyweb/unesc_share/usuario/service/UsuarioService.java) | Cadastro transacional e consulta do usuario atual |
| [usuario/controller/UsuarioController.java](../src/main/java/br/com/murkyweb/unesc_share/usuario/controller/UsuarioController.java) | Consulta de identidade derivada do JWT |
| [shared/api/ApiExceptionHandler.java](../src/main/java/br/com/murkyweb/unesc_share/shared/api/ApiExceptionHandler.java) | Extensao do tratamento MVC nativo com `ProblemDetail` |

Configuracoes alteradas: `pom.xml`, `.env.example`, `src/main/resources/application.properties` e `application-dev.properties`.
O starter utilizado e `spring-boot-starter-security-oauth2-resource-server`, com versao gerenciada pelo Spring Boot.

Testes: `src/test/java/br/com/murkyweb/unesc_share/security/AutenticacaoTests.java`, `JwtConfigTests.java` e teste de contexto existente.
Configuracao exclusiva de testes: `src/test/resources/application-test.properties`.

## Configurar e executar

No `.env` existente, acrescente:

```dotenv
JWT_SECRET=<resultado-do-comando-abaixo>
JWT_TTL=15m
JWT_ISSUER=unesc-share
JWT_AUDIENCE=unesc-share-api
```

Gere o segredo localmente:

```bash
openssl rand -base64 32
```

Nao use o texto de exemplo como chave, nao versione o `.env` e nao reutilize a chave publica de testes.
`JWT_ISSUER` e um identificador validado localmente, nao um endereco de descoberta de um provedor externo.

Com o PostgreSQL ativo:

```bash
docker compose up -d
set -a
source .env
set +a
./mvnw spring-boot:run
```

O prefixo `/api/v1` permanece no context-path. Controllers e matchers usam caminhos relativos, sem repetir o prefixo.
`CORS_ALLOWED_ORIGINS` recebe origens explicitas separadas por virgula, por exemplo `http://localhost:5173`.

## Requisicoes de teste manual

As credenciais abaixo sao exemplos locais, nao credenciais criadas automaticamente.

Cadastro:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/cadastro \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Estudante","email":"estudante@example.com","senha":"UmaSenhaLocal123!"}'
```

Retorno `201`, com `id`, `nome`, `email`, `perfil=USUARIO` e `dataCadastro`.
Email e normalizado com trim e minusculas; a unicidade do banco protege tambem cadastros concorrentes pela API.
Perfil enviado no JSON nao pode elevar privilegios. Senha deve ter de 8 a 64 caracteres, respeitando o limite adicional de 72 bytes UTF-8 do BCrypt; nao e aparada ou normalizada.

Login:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"estudante@example.com","senha":"UmaSenhaLocal123!"}'
```

Resposta `200`:

```json
{
  "accessToken": "<jwt-assinado>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "expiresAt": "<instante-UTC>"
}
```

Consulta do usuario autenticado, substituindo o marcador pelo token recebido:

```bash
curl -i http://localhost:8080/api/v1/usuarios/me \
  -H 'Authorization: Bearer <accessToken>'
```

Nao e necessario nem permitido escolher o usuario por ID: a identidade vem da claim `sub` validada.

## Erros

- `400`: campos invalidos, senha acima do limite ou JSON malformado.
- `409`: email ja cadastrado.
- `401`: login incorreto, token ausente/invalido/expirado ou usuario atual indisponivel.
- `403`: perfil sem permissao em rota protegida ou origem CORS rejeitada.

Erros MVC usam `application/problem+json`. Erros de validacao incluem `errors` com campo e mensagem, sem valores rejeitados.
Credenciais incorretas e usuario inexistente retornam a mesma mensagem.
Falhas nos filtros de seguranca usam handlers Bearer nativos, incluindo `WWW-Authenticate` quando aplicavel; o frontend nao deve presumir corpo JSON nessas respostas.

## Testes automatizados

Com PostgreSQL e as variaveis de conexao exportadas:

```bash
./mvnw test
```

Ou apenas os testes deste incremento:

```bash
./mvnw -Dtest=AutenticacaoTests,JwtConfigTests test
```

Os testes HTTP passam pela cadeia real de filtros e usam JWTs assinados e decodificados pelos beans nativos, nao apenas autenticacao simulada.
Cobrem cadastro/login ponta a ponta, normalizacao e duplicidade de email, hash, perfil fixo, validacao, identidade, expiracao, adulteracao, issuer/audience, ausencia de sessao, restricao de transporte, roles e CORS.
As rotas administrativas de teste existem apenas no contexto de testes e nao sao incluidas no backend de producao.
Os testes de integracao sao transacionais, revertem seus registros e nao acionam o runner. Continuam usando o PostgreSQL local de desenvolvimento e `ddl-auto=update`; nao ha banco isolado de testes neste incremento.

## Referencias oficiais

- [Spring Security: JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security: DaoAuthenticationProvider](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html)
- [Spring Web: Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
