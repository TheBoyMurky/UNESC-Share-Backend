# OpenAPI e Swagger UI

O projeto usa `springdoc-openapi-starter-webmvc-ui` para gerar uma especificação OpenAPI 3 a partir dos controllers Spring MVC, DTOs e validações, e para servir a interface Swagger UI. A configuração registra o nome/descrição da API e o esquema HTTP Bearer JWT. As rotas públicas de cadastro e login aparecem sem exigência de token; as demais operações documentadas usam Bearer.

## Acesso local

A documentação está desligada por padrão em `application.properties` e ligada apenas por `application-dev.properties`. No perfil `dev`, ela fica disponível em:

- Interface Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`
- Especificação JSON: `http://localhost:8080/api/v1/v3/api-docs`
- Especificação YAML: `http://localhost:8080/api/v1/v3/api-docs.yaml`

O context path `/api/v1` é adicionado pelo servidor; os paths internos na especificação continuam relativos a esse prefixo. Na UI, use **Authorize** e informe somente o JWT no campo Bearer. A senha do login não é reutilizada automaticamente para obter token.

Inicie o backend local com o perfil `dev` e as variáveis do `.env` carregadas:

```bash
set -a
source .env
set +a
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Esse perfil também habilita outros comportamentos locais (como o catálogo inicial, conforme `SEED_ENABLED`). Não use `dev` em produção. Fora desse perfil a dependência fica instalada, mas os endpoints de documentação permanecem desabilitados e não são liberados pelo Spring Security.

## Segurança e limitações

- A especificação e a UI são públicas somente no perfil `dev`; operações de negócio mantêm as regras JWT/perfil existentes.
- Não há senha, JWT real ou segredo da aplicação embutido na especificação.
- A especificação é gerada em tempo de execução; não é um snapshot exportado/versionado. Mudanças de contrato devem continuar acompanhadas por testes e guias funcionais.
- A geração automática registra caminhos, parâmetros, DTOs e constraints disponíveis no código. Regras de negócio, todos os códigos de erro e situações de concorrência podem exigir anotações OpenAPI e documentação funcional complementar.
- Swagger UI facilita explorar a API, mas não substitui testes automatizados ou validação do frontend.

## Dependência e compatibilidade

A dependência usada é `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`. O springdoc publica a linha 3.x para Spring Boot 4 e indica essa versão no guia atual; a matriz de compatibilidade publicada lista explicitamente Spring Boot 4.0.x. Como este projeto usa Spring Boot 4.1.1, a geração e os endpoints foram verificados pelos testes Maven do projeto.

Referências do projeto: [Getting Started](https://springdoc.org/getting-started.html), [FAQ e matriz de compatibilidade](https://springdoc.org/faq.html).

## Arquivos e validação

- `pom.xml`: dependência `springdoc-openapi-starter-webmvc-ui`.
- `src/main/java/br/com/murkyweb/unesc_share/config/OpenApiConfig.java`: metadados e esquema Bearer, somente no perfil `dev`.
- `src/main/java/br/com/murkyweb/unesc_share/security/SecurityConfig.java`: liberação da documentação somente no perfil `dev`; demais rotas preservam as permissões atuais.
- `src/main/java/br/com/murkyweb/unesc_share/security/controller/AuthController.java`: operações públicas identificadas sem requisito Bearer.
- `src/main/resources/application.properties` e `application-dev.properties`: documentação desabilitada por padrão e habilitada localmente.
- `src/test/java/br/com/murkyweb/unesc_share/config/OpenApiDevProfileTests.java`: especificação, esquema de autenticação, UI e proteção das rotas.

Executar o teste focado:

```bash
set -a
source .env
set +a
./mvnw -Dtest=OpenApiDevProfileTests test
```

`OpenApiDevProfileTests` e `UnescShareApplicationTests` passaram com4 testes focados. A validação completa em 26/10/2026 (`./mvnw clean verify`) passou com **558 testes**, sem falhas/erros/ignorados e JAR gerado, usando PostgreSQL17.11. Nenhum comando Docker foi executado.
