# Farmácia Digital

Mapa das farmácias e UBS de Sorocaba e Votorantim, com busca por endereço, horário de funcionamento e preço de remédios. Tem também login: cidadãos salvam favoritos, farmácias informam preços, e o administrador aprova as farmácias.

**Atenção:** os preços e estoques de exemplo são **fictícios**. Nenhuma fonte oficial informa preço ou estoque.

## 🧠 Tecnologias
- **Servidor:** Java puro (`com.sun.net.httpserver`), sem framework e sem Maven. Conexão com o banco por JDBC.
- **Banco:** PostgreSQL 17.
- **Site:** HTML, CSS e JavaScript puro, com Bootstrap 5.3.3 e o mapa do Mapbox GL JS.
- **Bibliotecas externas** (só estas, em `servidor/lib/`): o driver JDBC do PostgreSQL e, para enviar o e-mail de recuperação de senha, o Jakarta Mail (com suas dependências de ativação).

## 📁 Estrutura de pastas
- `banco/`: scripts SQL.
- `servidor/`: o servidor Java (`src/`, `testes/`, `lib/`) e o `rodar.bat`.
- `site/`: as páginas, o CSS, o JS e os contornos das cidades (`data/`).

## ▶️ Como rodar
1. Crie o banco `farmaciadigital` no PostgreSQL.
2. Rode os scripts de `banco/`, nesta ordem (todos podem ser rodados de novo):
   1. `tabela.sql`, `dados.sql`, `precos.sql`, `farmacias_reais.sql`, `ubs.sql`, `horarios.sql` (dados do mapa);
   2. `login.sql` (tabelas do login, favoritos, pedidos de farmácia e recuperação de senha);
   3. `demo.sql` **só para demonstração** (veja abaixo).
   4. `servicos_estoque.sql` (serviços gratuitos e estoque por lote). Rode **depois** do `demo.sql`, porque ele acerta o contador de ids dos remédios.
   5. `cadastro_farmacia.sql` (cadastro de farmácia com aprovação do administrador). Rode depois do `servicos_estoque.sql`.
   6. `consultas.sql` é **opcional**: só consultas de leitura de exemplo, para ver os dados do banco. Não altera nada.
3. Defina as variáveis de ambiente (nunca escreva senhas no código):
   - `DB_PASSWORD`: senha do PostgreSQL. Obrigatória.
   - `SMTP_USUARIO` e `SMTP_SENHA`: conta e senha de app para enviar e-mails. Opcionais (veja "Recuperação de senha").
   - `SMTP_HOST` (padrão `smtp.gmail.com`) e `SMTP_PORTA` (padrão `587`, com STARTTLS). Opcionais.
   - `SITE_URL`: endereço do site, usado para montar o link do e-mail (padrão `http://localhost:8080`).

   Exemplo no PowerShell: `$env:DB_PASSWORD = "sua-senha"`. No Git Bash: `export DB_PASSWORD="sua-senha"`.
4. Dê dois cliques em `servidor/rodar.bat` (ou rode-o no terminal, dentro da pasta `servidor`). Ele compila tudo e sobe o servidor.
5. Abra http://localhost:8080

**Testes do servidor** (com o servidor já compilado, em outro terminal, dentro da pasta `servidor`):

    java -Dstdout.encoding=UTF-8 -cp "out;lib/*" TesteSimples

Os testes criam contas de teste e as apagam no fim. Eles leem o banco com os dados já carregados.

## 👤 Contas, login e administrador
- **Cidadão:** cria a conta em `cadastro.html` (nome, e-mail, senha e aceite da política de privacidade). Entra em `entrar.html`, vê a conta em "Minha conta", troca a senha, salva favoritos e pode apagar a própria conta.
- **Farmácia:** veja "Cadastro de farmácia e aprovação" abaixo.
- **Administrador:** a área `admin.html` aprova, recusa e revoga cadastros de farmácia, e mostra um resumo. A tela **nunca** cria administrador. Isso só é feito pelo terminal:

      java -cp "out;lib/*" CriarAdmin

  O programa pede nome, e-mail e senha no terminal (a senha não aparece na tela). Não funciona pela IDE, porque a IDE não tem o terminal com a senha escondida.
- **Revogar** o acesso de uma farmácia fecha o painel na hora, mas a conta continua como farmácia.
- **Sessão:** fica na memória do servidor e dura 8 horas. Reiniciar o servidor desloga todo mundo. O tipo da conta é conferido no banco a cada pedido, então a aprovação vale sem sair e entrar de novo.

### Recuperação de senha
- **Trocar senha** (logado): em "Minha conta". Ao trocar, as outras sessões da conta saem.
- **Esqueci minha senha**: em `entrar.html`. A pessoa digita o e-mail e recebe um link que vale 30 minutos e serve uma vez só. A resposta é sempre a mesma, exista a conta ou não.
- **Com SMTP configurado** (`SMTP_USUARIO` e `SMTP_SENHA`), o link vai por e-mail.
- **Sem SMTP** (modo de desenvolvimento, o padrão), nada é enviado. O servidor escreve no console: `E-mail não configurado; link de redefinição: ...`. É assim que se testa e se demonstra sem e-mail. **Não rode sem SMTP em um ambiente de verdade**: qualquer pessoa com acesso ao console lê o link.

## 🩺 Serviços gratuitos e estoque por lote
No **Painel da farmácia** (`painel-farmacia.html`, para a conta de uma farmácia aprovada ou com cadastro novo em análise) há duas abas:
- **Estoque**: cada remédio tem um ou mais lotes, com lote, validade, quantidade, preço de venda e, se quiser, preço de custo e fornecedor. Também dá para definir um estoque mínimo (o painel avisa quando faltar) e cadastrar um remédio que ainda não está na lista, com apresentação, tipo (referência, genérico ou similar) e se exige receita. Lotes vencidos ou perto de vencer (30 dias) ficam destacados.
- **Serviços gratuitos**: a farmácia escolhe da lista (aferição de pressão, glicemia, hemoglobina, colesterol...) ou digita um serviço próprio, com dias e horários, se precisa agendar e uma observação. Dá para pausar um serviço sem apagar.

Como o estoque conversa com a busca pública: a quantidade e o preço usados em `/api/precos` são recalculados a cada alteração. Só entram lotes que não venceram, e o preço é o do lote que vence primeiro. Se todos os lotes de um remédio vencerem, a farmácia deixa de aparecer na busca dele, mesmo que ninguém abra o painel.

Rotas novas: `GET/POST /api/painel/estoque`, `GET/POST /api/painel/servicos` (as duas exigem a conta da farmácia) e `GET /api/servicos?farmacia=ID` (público, só serviços ativos).

## 🏪 Cadastro de farmácia e aprovação
1. A pessoa cria uma conta e abre `farmacia-acesso.html` (**Cadastrar farmácia**). Informa CNPJ e telefone. Se o CNPJ for novo, a tela pede nome, **cidade (só Sorocaba ou Votorantim)**, rua e número, bairro e CEP, e o botão **Localizar no mapa** acha o ponto pelo Mapbox. A localização precisa ser marcada para enviar.
2. Ao enviar, a farmácia é criada na hora como **pendente**. Ela **já pode usar o painel** para lançar estoque, preços e serviços gratuitos, mas fica **fora do mapa, da busca de preços, dos favoritos e dos serviços públicos**.
3. O cadastro cai em `admin.html` (filtro "Pendentes"), com endereço, bairro, CEP, um link para conferir o ponto no mapa e o que já foi lançado. O administrador **aprova** (a farmácia passa a aparecer para todos) ou **recusa**.
4. **Revogar** ou **recusar** esconde a farmácia de novo e fecha o painel. O estoque e os serviços ficam guardados. A mesma conta pode corrigir os dados e reenviar o cadastro: a farmácia é reaproveitada, sem duplicar.

Se o CNPJ já está na base pública (farmácias do CNES), não é cadastro novo: vira um **pedido de acesso** a uma farmácia que já aparece no mapa. Nesse caso o painel só abre **depois** da aprovação, para ninguém mexer nos preços de uma farmácia pública antes de o administrador confirmar quem é.

O servidor confere de novo o que a tela mandou: CNPJ, cidade e se a localização está dentro da área de Sorocaba e Votorantim. A posição vem do navegador, então o administrador deve conferir o ponto no link do cadastro antes de aprovar.

### Contas de demonstração (`demo.sql`)
**Só para testar o projeto.** A senha é pública: `Demo@2026`.
- `admin@demo.farmaciadigital.local` (administrador)
- `cidadao@demo.farmaciadigital.local` (cidadão)
- `farmacia@demo.farmaciadigital.local` (farmácia, com acesso aprovado à Farmácia Flores)

O `demo.sql` também cria 15 remédios fictícios com estoque e preço nas 5 farmácias de exemplo. **Não rode `demo.sql` em um banco com dados reais.** Para entregar ou usar de verdade, crie só a conta de administrador com `CriarAdmin`.

### Segurança, em poucas palavras
- A senha nunca é guardada: o banco guarda só um resumo PBKDF2 com um sal aleatório por conta.
- Todo SQL usa `PreparedStatement`.
- O cookie de sessão é `HttpOnly` e `SameSite=Lax`.
- Cada rota protegida confere a sessão e o tipo da conta **no servidor**. Esconder um link no site não protege nada.
- 5 erros de senha em 10 minutos bloqueiam o e-mail por 10 minutos.
- Todo envio de formulário (POST) confere o cabeçalho `Origin`.

## 🔑 Chaves do site (`site/js/config.js`)
As chaves ficam num arquivo local que **não vai para o git**:
1. Copie `site/js/config.exemplo.js` para `site/js/config.js`.
2. Preencha as chaves.

Sem `config.js` a página mostra o aviso de que a chave do mapa falta.

- **`MAPBOX_TOKEN`**: token público do Mapbox (começa com `pk.`). **Restrinja o token ao endereço do site** no painel do Mapbox (por exemplo `http://localhost:8080/*`), porque ele fica no navegador.
- **`STREETVIEW_KEY`** (opcional): foto da fachada no balão, pela Google Street View Static API. Restrinja a chave por referrer e por API, e defina uma cota diária. Sem chave, o balão só não mostra a foto.

## 🗺️ Mapa do código (`servidor/src`)
- `Servidor.java`: liga a porta 8080, entrega os arquivos do `site/` e encaminha cada pedido para a classe certa.
- `Rotas.java`: consultas do mapa, de medicamentos, de preços e do catálogo.
- `Login.java`: cadastro, entrar, sair, quem está logado, apagar conta e a regra de administrador.
- `RedefinirSenha.java`: trocar senha e recuperação de senha por link.
- `Favoritos.java`: favoritos de farmácias, UBS e remédios.
- `PainelFarmacia.java`: cadastro da farmácia, regra de acesso ao painel e o painel de preços.
- `ServicosFarmacia.java` e `EstoqueFarmacia.java`: serviços gratuitos e estoque por lote.
- `Admin.java`: área do administrador (resumo e decisões sobre os pedidos).
- `Senha.java`: senha com PBKDF2 e sal aleatório.
- `Email.java`: envio de e-mail pelo SMTP (Jakarta Mail).
- `Banco.java`: abre a conexão com o PostgreSQL. `Json.java`: escreve o JSON na mão.
- `CriarAdmin.java`: cria a conta de administrador pelo terminal.
- `testes/TesteSimples.java`: os testes, sem framework.

## 🧭 Páginas
- `index.html`: o mapa, a lista de farmácias e UBS, e "Remédio e preço".
- `entrar.html`, `cadastro.html`, `esqueci-senha.html`, `redefinir-senha.html`, `conta.html`: login, cadastro e senha.
- `favoritos.html`, `farmacia-acesso.html`, `painel-farmacia.html`: favoritos, cadastro e painel da farmácia.
- `admin.html`: área do administrador. `remedios.html`: remédios cadastrados (com o selo de origem).
- `sobre.html`: de onde vêm os dados, o que os avisos significam, a privacidade e a segurança.

## 📚 Conceitos da disciplina
- **HTML e CSS**: as páginas e `css/style.css` (só o que o Bootstrap não cobre).
- **Bootstrap**: grade, navbar, cards, modais, toasts e formulários com validação.
- **JavaScript**: classes (`Api`, `Mapa`, `Balao`, `Sessao`, `Favoritos` e outras em `site/js/`), DOM, eventos, `fetch` e `async`/`await`.
- **Cliente e servidor**: o site chama `/api/...` e o servidor responde em JSON.
- **Java e JDBC**: `servidor/src`, com `PreparedStatement` em toda consulta.
