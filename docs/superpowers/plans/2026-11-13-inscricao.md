# Módulo de Inscrição — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que um participante se inscreva em um evento respeitando as vagas, e que a inscrição seja confirmada ou cancelada por transições válidas.

**Architecture:** Módulo `modules/inscricao` no mesmo padrão de `usuario` e `evento` (entidade, DTOs, repository, service, controller). O service trava a linha do evento (`PESSIMISTIC_WRITE`) antes de contar as inscrições ativas, para que pedidos simultâneos não passem do limite de vagas.

**Tech Stack:** Kotlin 2.3.21, Java 21, Spring Boot 4.1.2, Spring Data JPA, Bean Validation, JUnit 5 + Mockito, H2 em memória nos testes.

**Spec:** [docs/superpowers/specs/2026-11-13-inscricao-design.md](../specs/2026-11-13-inscricao-design.md)

## Global Constraints

- Pacote: `acontece.api.modules.inscricao`. O enum `acontece.api.enums.StatusInscricao` (`PENDENTE`, `CONFIRMADA`, `CANCELADA`) já existe e **não muda**.
- Os 5 arquivos de `modules/inscricao` hoje são stubs (`class X {}`): substituir o conteúdo, não criar arquivos paralelos.
- Status inicial: `PENDENTE`. `PENDENTE` e `CONFIRMADA` ocupam vaga; só `CANCELADA` libera.
- Transições permitidas: `PENDENTE → CONFIRMADA`, `PENDENTE → CANCELADA`, `CONFIRMADA → CANCELADA`. `CANCELADA` é final. Qualquer outra, inclusive repetir o status atual: 409.
- Reinscrição depois de cancelar cria uma inscrição nova. Duas ativas do mesmo participante no mesmo evento: 409.
- Evento com `dataFim` no passado não aceita inscrição nova: 400. Evento já iniciado mas não encerrado aceita.
- Erros pelo `GlobalExceptionHandler` existente: `ResourceNotFoundException` (404), `ConflictException` (409), `require(...)`/`IllegalArgumentException` (400). Mensagens em português.
- `spring.jpa.open-in-view=false`: toda resposta é montada dentro da transação do service.
- **Nunca tocar o banco real:** rodar testes só com `./mvnw test -Dtest=<Classe>`. Não rodar `ApiApplicationTests` nem `./mvnw test` sem filtro (o `contextLoads` conecta no Supabase).
- Sem commit a menos que o usuário peça. Cada tarefa termina com os arquivos prontos e `git status` mostrando o que mudou.

## Review Focus

1. Duas inscrições simultâneas para a última vaga: exatamente uma é aceita, a outra recebe 409 (Task 5).
2. Service chamado fora de uma transação de teste, como em produção, não lança `LazyInitializationException` (Task 5).
3. `POST` com participante ou evento inexistente devolve 404, não 500 (Tasks 2 e 4).
4. `GET /inscricoes?status=XYZ` e `PATCH` com status inválido devolvem 400, não 500 (Task 4).
5. Evento em andamento (já começou, ainda não acabou) aceita inscrição (Task 2).

Os testes usam H2. Eles **não provam** o comportamento no Postgres do Supabase (filtros com parâmetro nulo, `FOR UPDATE`). Depois do deploy, conferir uma vez à mão no Swagger: listar com e sem filtros, e criar uma inscrição.

---

### Task 1: Entidade, repositories e consultas

**Files:**
- Modify: `src/main/kotlin/acontece/api/modules/inscricao/Inscricao.kt` (substituir o stub)
- Modify: `src/main/kotlin/acontece/api/modules/inscricao/InscricaoRepository.kt` (substituir o stub)
- Modify: `src/main/kotlin/acontece/api/modules/evento/EventoRepository.kt`
- Test: `src/test/kotlin/acontece/api/modules/inscricao/InscricaoRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `class Inscricao(participante: Participante, evento: Evento, status: StatusInscricao = PENDENTE, dataInscricao: LocalDateTime = LocalDateTime.now(), id: Long? = null)`. `participante` e `evento` são `@ManyToOne(fetch = LAZY, optional = false)`, `status` é `@Enumerated(EnumType.STRING)` e `var`, `id` é `val` com `IDENTITY`, no estilo de `Evento.kt`.
  - `InscricaoRepository : JpaRepository<Inscricao, Long>` com:
    - `fun countByEventoIdAndStatusIn(eventoId: Long, statuses: Collection<StatusInscricao>): Long`
    - `fun existsByParticipanteIdAndEventoIdAndStatusIn(participanteId: Long, eventoId: Long, statuses: Collection<StatusInscricao>): Boolean`
    - `fun buscar(eventoId: Long?, participanteId: Long?, status: StatusInscricao?, pageable: Pageable): Page<Inscricao>`: `@Query` JPQL em que cada filtro é ignorado quando nulo (`(:eventoId IS NULL OR i.evento.id = :eventoId)` e equivalentes). Sem `ORDER BY` na query.
  - `EventoRepository.buscarParaInscricao(id: Long): Evento?`: `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query("SELECT e FROM Evento e WHERE e.id = :id")`.

- [ ] **Step 1: Escrever `InscricaoRepositoryTest`** (`@DataJpaTest`, injeta os 3 repositories). Helpers privados criam `Evento` (com `Endereco(cep = "01001000", logradouro = "Praça da Sé", bairro = "Sé", cidade = "São Paulo", uf = "SP")`, datas futuras) e `Participante` (CPF e e-mail distintos por chamada). Testes:
  - ``contagem considera so pendentes e confirmadas do evento``: no evento A, 1 `PENDENTE` + 1 `CONFIRMADA` + 1 `CANCELADA`; no evento B, 1 `PENDENTE`. `countByEventoIdAndStatusIn(A, listOf(PENDENTE, CONFIRMADA))` é `2`.
  - ``existe inscricao ativa so para o mesmo participante e evento``: `true` com `PENDENTE`; `false` se só existe `CANCELADA`; `false` para outro participante; `false` para outro evento.
  - ``buscar sem filtros devolve todas``, ``buscar filtra por evento``, ``buscar filtra por participante``, ``buscar filtra por status`` e ``buscar combina filtros``: 3 inscrições variadas, `PageRequest.of(0, 10)`, comparando o conjunto de ids (a ordem não é garantida).
  - ``buscarParaInscricao devolve o evento ou null``: id existente devolve o evento; `999999L` devolve `null`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=InscricaoRepositoryTest`
Expected: falha de compilação (`Inscricao`, `buscar`, `buscarParaInscricao` não definidos).

- [ ] **Step 3: Implementar** a entidade `Inscricao`, o `InscricaoRepository` e o método em `EventoRepository` com as assinaturas acima.

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw test -Dtest=InscricaoRepositoryTest`
Expected: `BUILD SUCCESS`, todos os testes passando.

- [ ] **Step 5: Registrar** `git status` com os 4 arquivos tocados.

---

### Task 2: Criar inscrição (regras de vaga, duplicidade e evento encerrado)

**Files:**
- Modify: `src/main/kotlin/acontece/api/modules/inscricao/InscricaoDTOs.kt` (substituir o stub)
- Modify: `src/main/kotlin/acontece/api/modules/inscricao/InscricaoService.kt` (substituir o stub)
- Test: `src/test/kotlin/acontece/api/modules/inscricao/InscricaoServiceTest.kt`

**Interfaces:**
- Consumes: `Inscricao`, `InscricaoRepository.countByEventoIdAndStatusIn`, `existsByParticipanteIdAndEventoIdAndStatusIn`, `EventoRepository.buscarParaInscricao` (Task 1); `ParticipanteRepository` (existente).
- Produces:
  - `data class InscricaoRequest(@field:NotNull(message = "O participante é obrigatório") val participanteId: Long? = null, @field:NotNull(message = "O evento é obrigatório") val eventoId: Long? = null)`
  - `data class InscricaoResponse(val id: Long, val participanteId: Long, val participanteNome: String, val eventoId: Long, val eventoTitulo: String, val status: StatusInscricao, val dataInscricao: LocalDateTime)` e `fun Inscricao.toResponse(): InscricaoResponse`.
  - `@Service class InscricaoService(repository: InscricaoRepository, eventoRepository: EventoRepository, participanteRepository: ParticipanteRepository)` com `@Transactional fun criar(request: InscricaoRequest): InscricaoResponse`.

- [ ] **Step 1: Escrever `InscricaoServiceTest`** (`@DataJpaTest` + `@Import(InscricaoService::class)`, mesmos helpers da Task 1, `vagas` configurável por evento). Testes, com as mensagens exatas:
  - ``criar inscreve como PENDENTE``: resposta com `status == PENDENTE`, `participanteNome`, `eventoTitulo`, `dataInscricao` não nula; `repository.count() == 1`.
  - ``criar com participante inexistente``: `ResourceNotFoundException("Participante 999999 não encontrado")`.
  - ``criar com evento inexistente``: `ResourceNotFoundException("Evento 999999 não encontrado")`.
  - ``criar em evento encerrado``: `dataFim` ontem, `IllegalArgumentException("O evento já foi encerrado")`.
  - ``criar em evento em andamento``: `dataInicio` ontem e `dataFim` amanhã, aceita.
  - ``criar duplicada ativa``: já existe `PENDENTE` e depois `CONFIRMADA` do mesmo par, ambos dão `ConflictException("Participante já possui inscrição ativa neste evento")`.
  - ``criar sem vagas``: `vagas = 1`, outro participante já `PENDENTE`, `ConflictException("Evento sem vagas disponíveis")`.
  - ``cancelada libera a vaga``: `vagas = 1`, outro participante `CANCELADA`, aceita.
  - ``reinscricao depois de cancelar``: mesmo participante com `CANCELADA`, aceita, e `repository.count() == 2`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=InscricaoServiceTest`
Expected: falha de compilação (`InscricaoService`, `InscricaoRequest` não definidos).

- [ ] **Step 3: Implementar** `InscricaoDTOs.kt` e `criar`. Ordem das checagens (cada uma encerra com o erro dela): participante (404) → evento via `buscarParaInscricao` (404, é aqui que a trava é obtida) → `dataFim` depois de agora, via `require` (400) → duplicada ativa (409) → contagem de ativas `>= evento.vagas` (409) → salvar `PENDENTE`. Os status ativos ficam numa constante `STATUS_ATIVOS = listOf(PENDENTE, CONFIRMADA)` no `companion object` do service. A duplicidade tem de ser checada **depois** de obter a trava, para que um duplo clique do mesmo participante também seja serializado.

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw test -Dtest=InscricaoServiceTest`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Registrar** `git status`.

---

### Task 3: Consultar, listar e mudar o status

**Files:**
- Modify: `src/main/kotlin/acontece/api/modules/inscricao/InscricaoDTOs.kt`
- Modify: `src/main/kotlin/acontece/api/modules/inscricao/InscricaoService.kt`
- Test: `src/test/kotlin/acontece/api/modules/inscricao/InscricaoServiceTest.kt`

**Interfaces:**
- Consumes: tudo da Task 2, mais `InscricaoRepository.buscar`.
- Produces:
  - `data class AtualizarStatusRequest(@field:NotNull(message = "O status é obrigatório") val status: StatusInscricao? = null)`
  - `@Transactional(readOnly = true) fun buscarPorId(id: Long): InscricaoResponse`
  - `@Transactional(readOnly = true) fun listar(eventoId: Long?, participanteId: Long?, status: StatusInscricao?, pageable: Pageable): Page<InscricaoResponse>`
  - `@Transactional fun atualizarStatus(id: Long, request: AtualizarStatusRequest): InscricaoResponse`

- [ ] **Step 1: Acrescentar testes ao `InscricaoServiceTest`:**
  - ``buscarPorId devolve a inscricao`` e ``buscarPorId inexistente``: `ResourceNotFoundException("Inscrição 999999 não encontrada")`.
  - ``listar aplica os filtros``: 3 inscrições variadas; filtrando por evento e por status, a página traz só as esperadas, com `participanteNome` e `eventoTitulo` preenchidos.
  - ``atualizarStatus aceita as transicoes validas``: `PENDENTE→CONFIRMADA`, `PENDENTE→CANCELADA`, `CONFIRMADA→CANCELADA`; a resposta e a entidade salva trazem o novo status.
  - ``atualizarStatus rejeita as demais``: tabela com os 6 pares restantes (`PENDENTE→PENDENTE`, `CONFIRMADA→PENDENTE`, `CONFIRMADA→CONFIRMADA`, `CANCELADA→PENDENTE`, `CANCELADA→CONFIRMADA`, `CANCELADA→CANCELADA`), todos `ConflictException("Transição de status inválida: <ORIGEM> → <DESTINO>")` (ex.: `"Transição de status inválida: CANCELADA → CONFIRMADA"`), e o status no banco não muda.
  - ``atualizarStatus inexistente``: `ResourceNotFoundException("Inscrição 999999 não encontrada")`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=InscricaoServiceTest`
Expected: falha de compilação (`buscarPorId`, `listar`, `atualizarStatus`, `AtualizarStatusRequest` não definidos).

- [ ] **Step 3: Implementar** os três métodos e o DTO. As transições ficam em um mapa no `companion object` (`PENDENTE → {CONFIRMADA, CANCELADA}`, `CONFIRMADA → {CANCELADA}`, `CANCELADA → {}`). `atualizarStatus` não precisa de trava: confirmar e cancelar nunca ocupam vaga. `listar` usa `repository.buscar(...).map { it.toResponse() }` dentro da transação.

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw test -Dtest=InscricaoServiceTest`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Registrar** `git status`.

---

### Task 4: Controller

**Files:**
- Modify: `src/main/kotlin/acontece/api/modules/inscricao/InscricaoController.kt` (substituir o stub)
- Test: `src/test/kotlin/acontece/api/modules/inscricao/InscricaoControllerTest.kt`

**Interfaces:**
- Consumes: `InscricaoService`, `InscricaoRequest`, `InscricaoResponse`, `AtualizarStatusRequest` (Tasks 2 e 3).
- Produces: `@RestController @RequestMapping("/inscricoes") class InscricaoController(service: InscricaoService)`:
  - `@PostMapping fun criar(@Valid @RequestBody request: InscricaoRequest): ResponseEntity<InscricaoResponse>`: 201 com `Location` `/inscricoes/{id}`, igual ao `ParticipanteController.criar`.
  - `@GetMapping("/{id}") fun buscarPorId(@PathVariable id: Long): InscricaoResponse`
  - `@GetMapping fun listar(@RequestParam(required = false) eventoId: Long?, @RequestParam(required = false) participanteId: Long?, @RequestParam(required = false) status: StatusInscricao?, @PageableDefault(size = 20, sort = ["id"]) pageable: Pageable): PagedModel<InscricaoResponse>` (o `size = 20` é explícito: o padrão do `@PageableDefault` seria 10)
  - `@PatchMapping("/{id}/status") fun atualizarStatus(@PathVariable id: Long, @Valid @RequestBody request: AtualizarStatusRequest): InscricaoResponse`

- [ ] **Step 1: Escrever `InscricaoControllerTest`** (`@WebMvcTest(InscricaoController::class)`, `@MockitoBean lateinit var service: InscricaoService`, no estilo de `ParticipanteControllerTest`):
  - ``POST cria e devolve 201 com Location``: corpo `{"participanteId": 1, "eventoId": 2}`; `status().isCreated`, header `Location` terminando em `/inscricoes/10`, `$.status == "PENDENTE"`.
  - ``POST sem participanteId devolve 400 com fieldErrors``: `$.fieldErrors[0].field == "participanteId"`.
  - ``POST com participante inexistente devolve 404``: service lança `ResourceNotFoundException`.
  - ``POST sem vagas devolve 409``: service lança `ConflictException`.
  - ``GET por id devolve 200`` e ``GET por id inexistente devolve 404``.
  - ``GET lista repassa os filtros``: `?eventoId=2&status=CONFIRMADA` chama `service.listar(2L, null, StatusInscricao.CONFIRMADA, PageRequest.of(0, 20, Sort.by("id")))`; resposta 200 com a página em `$.content`.
  - ``GET lista com status invalido devolve 400``: `?status=XYZ`.
  - ``PATCH status devolve 200``: corpo `{"status": "CONFIRMADA"}`.
  - ``PATCH status invalido devolve 400``: `{"status": "XYZ"}`; e também corpo `{}` devolve 400 com `fieldErrors[0].field == "status"`.
  - ``PATCH transicao invalida devolve 409``: service lança `ConflictException`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=InscricaoControllerTest`
Expected: falha (rotas inexistentes: 404/405 nos testes) ou de compilação.

- [ ] **Step 3: Implementar** o controller com as assinaturas acima.

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw test -Dtest=InscricaoControllerTest`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Registrar** `git status`.

---

### Task 5: Concorrência e uso fora de transação

**Files:**
- Test: `src/test/kotlin/acontece/api/modules/inscricao/InscricaoConcorrenciaTest.kt`

**Interfaces:**
- Consumes: `InscricaoService.criar`, `buscarPorId`, `listar` (Tasks 2 e 3) e os 3 repositories.
- Produces: nada além do teste. Se ele falhar, o defeito está no service (trava ausente ou resposta montada fora da transação), não no teste.

- [ ] **Step 1: Escrever `InscricaoConcorrenciaTest`.** `@SpringBootTest` com `properties` apontando para H2 em memória, **sem isso o teste cairia no Supabase**: `spring.datasource.url=jdbc:h2:mem:concorrencia;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000`, `spring.datasource.username=sa`, `spring.datasource.password=` e `spring.jpa.hibernate.ddl-auto=create-drop`. Sem `@Transactional` na classe. Um `@BeforeEach` primeiro confere que `dataSource.connection.use { it.metaData.url }` começa com `jdbc:h2:mem` e aborta o teste se não começar; depois cria o cenário. `@AfterEach` apaga inscrições, participantes e eventos. Testes:
  - ``so uma de varias inscricoes simultaneas fica com a ultima vaga``: evento com `vagas = 1`, 5 participantes, 5 threads liberadas ao mesmo tempo por um `CountDownLatch`, cada uma chamando `service.criar`. Resultado: exatamente 1 sucesso e 4 `ConflictException`; `countByEventoIdAndStatusIn(evento, listOf(PENDENTE, CONFIRMADA)) == 1`.
  - ``consultas fora de transacao devolvem a resposta completa``: depois de criar uma inscrição, `service.buscarPorId` e `service.listar(null, null, null, PageRequest.of(0, 10))` devolvem `participanteNome` e `eventoTitulo` preenchidos, sem `LazyInitializationException`.

- [ ] **Step 2: Rodar**

Run: `./mvnw test -Dtest=InscricaoConcorrenciaTest`
Expected: `BUILD SUCCESS`. Se o primeiro teste falhar com mais de 1 sucesso, a trava da Task 2 não está sendo obtida antes da contagem: corrigir no service, não no teste.

- [ ] **Step 3: Rodar a suíte do módulo inteiro**

Run: `./mvnw test -Dtest="Inscricao*Test,ParticipanteControllerTest"`
Expected: `BUILD SUCCESS`, nenhum teste falhando, `ApiApplicationTests` não executado.

- [ ] **Step 4: Registrar** `git status` e a lista final de arquivos novos e alterados.

## Observação sobre o banco real

Na próxima vez que a aplicação subir com o banco do Supabase (`ddl-auto=update`), a tabela `inscricao` será criada lá automaticamente. Os testes acima não fazem isso.
