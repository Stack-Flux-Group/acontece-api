# acontece-api

API REST em Kotlin para gerenciamento de eventos, inscrições e participantes da plataforma **Acontece**.
O app que consome esta API está em [acontece-frontend](https://github.com/Stack-Flux-Group/acontece-frontend).

- **API online:** https://acontece-api.onrender.com/swagger-ui/index.html
- **Documentação das rotas:** o Swagger (`/swagger-ui.html`) é a referência completa: cada rota, corpo, resposta e quem pode chamar.

## Tecnologias

| | |
|---|---|
| Linguagem | Kotlin 2.3 na JVM 21 |
| Framework | Spring Boot 4 (Web MVC, Data JPA, Validation, Security) |
| Autenticação | JWT assinado com HS256 (OAuth2 Resource Server) e senhas com BCrypt |
| Banco | PostgreSQL (Supabase) em produção; H2 em memória nos testes |
| Integrações | ViaCEP via OpenFeign, para preencher o endereço do evento pelo CEP |
| Documentação | springdoc-openapi (Swagger UI) |
| Deploy | Docker no Render |

## O que a API faz

| Módulo | Resumo |
|---|---|
| **Autenticação** | `POST /auth/login` devolve o token JWT. Mande-o em `Authorization: Bearer <token>`. |
| **Participantes** | Cadastro público (`POST /participantes`). Cada participante vê e edita só o próprio cadastro; o organizador vê todos. O organizador inativa e reativa contas (`PATCH /participantes/{id}/status`). Excluir a conta apaga os dados pessoais e mantém o histórico de inscrições. |
| **Eventos** | Qualquer usuário logado consulta; só o organizador cria, edita e apaga. O endereço é buscado no ViaCEP. |
| **Inscrições** | O participante se inscreve em nome próprio e vê só as suas. O organizador confirma ou cancela (`PENDENTE → CONFIRMADA → CANCELADA`). |

### Papéis

- **PARTICIPANTE**: se cadastra sozinho, consulta eventos e se inscreve.
- **ORGANIZADOR**: administra eventos e inscrições. Não há cadastro público de organizador: o primeiro é criado na subida da aplicação a partir de `ORGANIZADOR_EMAIL` e `ORGANIZADOR_SENHA`.

### Regras de negócio principais

- Um evento não aceita mais inscrições ativas (`PENDENTE` ou `CONFIRMADA`) do que tem vagas. Inscrições simultâneas na última vaga são resolvidas com trava de linha no banco: só uma passa.
- Um participante não pode ter duas inscrições ativas no mesmo evento.
- Não é possível se inscrever em evento já encerrado, nem reduzir as vagas abaixo das inscrições ativas.
- As datas dos eventos são em horário de Brasília (`America/Sao_Paulo`), mesmo com o servidor em UTC.

Erros seguem sempre o mesmo formato JSON (`status`, `error`, `message`, `path` e, em erros de validação, `fieldErrors` com cada campo).

## Como rodar

Pré-requisito: **JDK 21**. O Maven não precisa ser instalado: o projeto usa o wrapper (`mvnw`).

1. Defina as variáveis de ambiente (ao menos `JWT_SECRET`, sem ela a aplicação não sobe):

   | Variável | Obrigatória | Para quê |
   |---|---|---|
   | `JWT_SECRET` | sim | Chave que assina os tokens. Mínimo de 32 caracteres. |
   | `JWT_EXPIRACAO_MINUTOS` | não (120) | Validade do token. |
   | `ORGANIZADOR_EMAIL` / `ORGANIZADOR_SENHA` | para ter um organizador | Cria o primeiro organizador na subida, se ainda não existir. |
   | `ORGANIZADOR_NOME` | não ("Organizador") | Nome desse organizador. |
   | `CORS_ALLOWED_ORIGINS` | não | Origens do front liberadas, separadas por vírgula. |

   No PowerShell, por exemplo:

   ```powershell
   $env:JWT_SECRET = "uma-chave-local-com-pelo-menos-32-caracteres"
   ```

2. Suba a aplicação:

   ```bash
   ./mvnw spring-boot:run
   ```

   No Windows (PowerShell/cmd), use `.\mvnw` no lugar de `./mvnw`.

3. Abra o Swagger em http://localhost:8080/swagger-ui.html. Para testar rotas protegidas, faça login em `POST /auth/login`, clique em **Authorize** e cole o token.

> A aplicação usa o banco configurado em `src/main/resources/application.properties`.

## Testes

```bash
./mvnw test
```

Os testes nunca usam o banco de produção: rodam num H2 em memória, com o ViaCEP trocado por um mock. Cobrem regras de negócio, permissões por papel, validação, login com JWT, CORS, Swagger, fuso horário e inscrições simultâneas.

## Estrutura do projeto

```
src/main/kotlin/acontece/api/
├── ApiApplication.kt
├── client/                 # ViaCEP (OpenFeign)
├── config/                 # Segurança, JWT, CORS, Swagger, Clock (fuso) e organizador inicial
├── enums/                  # Papel e StatusInscricao
├── exception/              # Exceções e o tratamento global de erros (@RestControllerAdvice)
└── modules/                # Um pacote por funcionalidade: controller, service, repository, DTOs e entidades
    ├── auth/               # Login e emissão do token
    ├── evento/             # Eventos e endereço (embutido)
    ├── inscricao/          # Inscrições e transições de status
    └── usuario/            # Usuario (base), Participante e Organizador
```

As entidades de usuário usam herança JPA `JOINED`: `usuario` guarda nome, e-mail e senha; `participante` e `organizador` guardam o que é só deles.

## Deploy

O `Dockerfile` faz o build em dois estágios (Maven → imagem só com a JRE) e é usado pelo Render. A aplicação escuta na porta 8080, e as variáveis da tabela acima ficam configuradas no painel do Render.

## Licença

[MIT](LICENSE)
