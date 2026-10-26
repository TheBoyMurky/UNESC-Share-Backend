# Perfil do usuário

Este incremento permite ao usuário autenticado atualizar seu nome e e-mail e trocar sua senha. A identidade sempre vem do `sub` do JWT; nenhum endpoint aceita UUID de usuário informado pelo cliente.

## Endpoints

Todos os caminhos são relativos ao context-path `/api/v1` e exigem `Authorization: Bearer <accessToken>`.

### Atualizar nome e e-mail

`PUT /usuarios/me`

```json
{
  "nome": "Nome atualizado",
  "email": "novo-email@example.com",
  "senhaAtual": "senha atual, quando o e-mail mudar"
}
```

O nome é obrigatório e limitado a 120 caracteres. E-mail obrigatório, válido, até 254 caracteres, normalizado com `strip()` e minúsculas. Se o e-mail normalizado for diferente do atual, `senhaAtual` é obrigatória e revalidada pelo `AuthenticationManager` nativo. O e-mail muda imediatamente neste protótipo; ainda não existe verificação por mensagem. Nome e e-mail iguais ao estado atual não exigem senha atual. A resposta `200` usa `UsuarioResponse`, sem senha/hash, preserva UUID, perfil e data de cadastro.

E-mail já utilizado retorna `409`, inclusive em caso de concorrência protegida pela constraint do banco. Senha atual ausente ou incorreta ao mudar e-mail retorna `400` com mensagem fixa, sem invalidar o JWT da requisição.

### Trocar senha

`PUT /usuarios/me/senha`

```json
{
  "senhaAtual": "senha atual",
  "novaSenha": "nova senha escolhida"
}
```

Exige a senha atual e aplica à nova senha a mesma regra do cadastro: 8 a 64 caracteres e no máximo 72 bytes UTF-8 para BCrypt. A nova senha precisa ser diferente da atual. Sucesso retorna `204 No Content`, sem corpo, e armazena somente o hash produzido pelo `PasswordEncoder` do Spring Security. Senha atual incorreta retorna `400` sem invalidar a sessão Bearer.

JWTs existentes não são revogados pela troca de senha; permanecem válidos até expirar, no máximo 30 minutos conforme `JWT_TTL` (15 minutos por padrão). O cliente deve descartar sua sessão local após a troca e solicitar novo login. Não há recuperação de senha ou verificação de e-mail neste incremento.

## Segurança e privacidade

- Nome/e-mail e senha são fluxos separados; a senha nunca aparece em `UsuarioResponse`.
- A senha atual é revalidada por `AuthenticationManager`/`DaoAuthenticationProvider`, reutilizando o `PasswordEncoder` configurado.
- DTOs redigem credenciais em `toString`; não registrar corpos, credenciais, tokens, hashes ou valores SQL sensíveis.
- Usuários não podem alterar seu próprio perfil (`USUARIO`, `MODERADOR`, `ADMINISTRADOR`), UUID ou data de cadastro por campos extras.
- O endpoint não oferece verificação de e-mail; confirme o endereço antes de salvar, pois a mudança é imediata e o login seguinte usa o novo e-mail.
- Sem revogação individual, rate limiting, recuperação de senha ou prova de verificação do endereço.

## Arquivos

- `src/main/java/br/com/murkyweb/unesc_share/usuario/controller/UsuarioController.java`
- `src/main/java/br/com/murkyweb/unesc_share/usuario/service/UsuarioService.java`
- `src/main/java/br/com/murkyweb/unesc_share/usuario/dto/PerfilAtualizacaoRequest.java`
- `src/main/java/br/com/murkyweb/unesc_share/usuario/dto/AlterarSenhaRequest.java`
- `src/test/java/br/com/murkyweb/unesc_share/usuario/UsuarioPerfilApiTests.java`

## Testar

Com PostgreSQL local ativo e `.env` configurado, execute:

```bash
set -a
source .env
set +a
./mvnw -Dtest=UsuarioPerfilApiTests test
```

Os testes usam JWTs e contas sintéticos no perfil `test`, com transação revertida. Não use credenciais reais nos comandos ou no chat. Para a troca de e-mail, use um endereço local descartável.

Validacao em 26/10/2026: `./mvnw -Dtest=UsuarioPerfilApiTests test` passou com4 testes; `./mvnw clean verify` passou com555 testes, sem falhas/erros/ignorados e JAR gerado. Os testes de API usam PostgreSQL local no perfil `test` e rollback transacional. Nenhum comando Docker foi executado.
