# Roteiro de apresentação do protótipo UnescShare

**Duração sugerida:** 8–10 minutos.  
**Público:** banca, docentes e estudantes.  
**Cenário:** uma pessoa estudante cadastrando/consultando um material e uma pessoa da equipe moderando-o.

## Antes da apresentação

1. Deixe `UNESC-Share-Backend` e `UNESC-Share-Frontend` em pastas irmãs no computador.
2. Configure o `.env` do backend, incluindo `JWT_SECRET`. Se o banco estiver vazio e quiser demonstrar ações administrativas, prepare uma conta de administrador por bootstrap antes da apresentação e desative o bootstrap depois.
3. Construa e suba os containers seguindo as [instruções do ambiente containerizado](../README.md). Confirme frontend, API e PostgreSQL antes de começar; faça isso com antecedência, pois a primeira construção baixa imagens e dependências.
4. Prepare duas contas já existentes: uma `USUARIO` e outra `MODERADOR` ou `ADMINISTRADOR`. Não deixe cadastro nem criação de credenciais para o tempo da apresentação.
5. Deixe disponível um material publicado e um material pendente. Use dados fictícios, criados pela equipe, ou conteúdo com licença/autorização adequada. Verifique a instituição, o curso, a disciplina e a categoria antes.
6. Se for mostrar Torrent/Magnet, use somente metadados preparados para demonstração e conteúdo autorizado. Evite abrir um cliente BitTorrent durante a banca se não tiver conferido a origem, a licença e o que ele irá baixar.
7. Abra `http://localhost:5173` e, se quiser mostrar a API, deixe outra aba em `http://localhost:8080/api/v1/swagger-ui/index.html`.

## Apresentação falada e demonstração

### 1. Problema e proposta — 45 segundos

**Fala sugerida:**

> Estudantes compartilham materiais acadêmicos por vários canais, mas é difícil encontrar a versão certa para uma disciplina específica. O UnescShare organiza esses registros por instituição, curso e disciplina, e permite que a comunidade encontre e avalie os materiais.

Mostre a página inicial e a hierarquia **Instituição → Curso → Disciplina → Material**.

### 2. Decisão principal da arquitetura — 1 minuto

**Fala sugerida:**

> O UnescShare é um catálogo, não um serviço para hospedar PDFs ou vídeos. No sistema ficam os metadados acadêmicos e as referências de compartilhamento: arquivos `.torrent` e Magnet URIs. Quando o material é acessado, a distribuição do conteúdo acontece pela rede BitTorrent, com um cliente compatível no computador da pessoa usuária.

Se mostrar o diagrama ou Swagger, explique o caminho **Vue 3 → API REST Spring Boot → PostgreSQL** e, para obter o conteúdo, **cliente BitTorrent ↔ rede BitTorrent**. O arquivo `.torrent` contém metadados de distribuição, não o conteúdo acadêmico hospedado pelo UnescShare.

### 3. Login e descoberta — 1 minuto

Entre com a conta de estudante preparada. Mostre o catálogo e filtre por instituição, curso, disciplina ou categoria.

**Fala sugerida:**

> A busca usa a estrutura acadêmica para reduzir resultados irrelevantes. A Matriz 6 de Ciência da Computação da UNESC e as categorias iniciais já estão cadastradas no ambiente de desenvolvimento.

Abra um resultado publicado e mostre título, descrição, disciplina, categoria e mecanismos disponíveis. Não apresente como público um material pendente, bloqueado ou removido.

### 4. Cadastro por estudante — 1 minuto e 15 segundos

Abra **Meus materiais → Cadastrar material**. Mostre os campos de metadados e a associação de um Magnet ou de um `.torrent`. Se fizer o cadastro ao vivo, use um exemplo simples e preparado.

**Fala sugerida:**

> O estudante pode catalogar o próprio material. O servidor define o autor pela conta autenticada e o registro começa pendente de revisão. O formulário não envia um PDF ou vídeo acadêmico ao servidor; envia metadados e um mecanismo de compartilhamento permitido.

Depois, abra **Meus materiais** para mostrar o estado `PENDENTE`. Explique que edição de metadados ou adição de mecanismos pode voltar o material para revisão.

### 5. Moderação — 1 minuto

Entre com a conta de equipe preparada e abra **Moderação → Materiais**. Selecione o material pendente, revise seus metadados e demonstre a ação de publicar com confirmação explícita.

**Fala sugerida:**

> A publicação é uma decisão explícita da equipe. A API verifica novamente o estado e as permissões; a interface não é a autoridade de segurança. A equipe pode revisar e moderar sem se passar pelo autor.

Se não houver tempo, mostre a fila e o detalhe de revisão sem executar a mutação ao vivo.

### 6. Acesso e participação — 1 minuto e 15 segundos

Volte à conta de estudante, atualize o catálogo e abra o material publicado. Demonstre **copiar Magnet** ou baixar o `.torrent`, sem iniciar download de conteúdo durante a apresentação.

Mostre brevemente comentários, avaliação e a opção de denúncia.

**Fala sugerida:**

> A cópia do Magnet ou o download do `.torrent` entrega ao usuário uma referência para um cliente compatível. Comentários e avaliações apoiam a descoberta; denúncias encaminham possíveis problemas para análise da equipe. Uma denúncia não bloqueia automaticamente um material.

### 7. Administração e documentação da API — 45 segundos (opcional)

Se houver tempo, use a conta administradora para mostrar a consulta da estrutura acadêmica e uma operação administrativa que já esteja preparada. Em seguida, abra o Swagger e mostre um endpoint de consulta.

**Fala sugerida:**

> A API organiza as operações por recursos e valida os dados no backend. A interface controla o fluxo de uso, enquanto a API aplica autenticação, autorização e validações.

Evite criar ou excluir instituição, curso, disciplina ou categoria ao vivo: a operação pode afetar vínculos e dados usados em outras partes da demonstração.

### 8. Encerramento — 30 segundos

**Fala sugerida:**

> O protótipo demonstra o catálogo acadêmico, a publicação moderada e a integração entre frontend, API e PostgreSQL. A distribuição do conteúdo fica na rede BitTorrent. Os próximos passos são validar a apresentação em diferentes navegadores, ampliar testes de ponta a ponta e preparar o deploy de demonstração com configuração segura.

## Se algo falhar durante a demonstração

- Se a API ainda estiver iniciando, aguarde alguns segundos e atualize a página. Não cadastre o mesmo material repetidamente após uma resposta incerta.
- Se o login falhar, confira antes da apresentação se a conta existe, se o perfil é o esperado e se o bootstrap de administrador está desligado depois de criar a conta.
- Se a integração falhar, explique a arquitetura com o Swagger e as telas que carregarem; não simule respostas nem afirme que uma ação foi persistida sem confirmação da API.
- Se não houver cliente BitTorrent instalado ou material autorizado para teste, mostre a cópia do Magnet ou os metadados do `.torrent` sem abrir o cliente.

## Limites que vale explicitar

- O UnescShare cataloga referências; não verifica se o conteúdo continuará disponível na rede BitTorrent nem controla peers ou trackers.
- Somente `.torrent` e Magnet URI são aceitos como mecanismos. Conteúdo acadêmico em PDF, EPUB, vídeo ou ZIP não deve ser enviado ao servidor.
- JWT tem curta duração; não há refresh token nem revogação imediata de tokens emitidos.
- Denúncias precisam de análise da equipe e não causam bloqueio automático.
- Este é um protótipo. A infraestrutura de apresentação deve ser testada no computador e na rede que serão usados na banca.
