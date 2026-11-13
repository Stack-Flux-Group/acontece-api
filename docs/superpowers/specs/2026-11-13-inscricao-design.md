# Módulo de Inscrição — desenho

Data: 2026-11-13 · Tamanho: mudança pequena (um módulo novo, no padrão dos que já existem)

## Intenção

Hoje a API tem Eventos e Participantes, mas nada liga os dois. O módulo `modules/inscricao`
existe só como arquivos vazios (`class Inscricao {}`). Esta etapa faz um participante conseguir
se inscrever em um evento, respeitando o número de vagas, e permite confirmar ou cancelar a
inscrição.

## Objetivos

- Inscrever um participante em um evento (`POST /inscricoes`).
- Consultar uma inscrição e listar inscrições com filtros, paginado.
- Mudar o status de uma inscrição, só por transições válidas.
- Nunca aceitar mais inscrições ativas do que as `vagas` do evento, nem com pedidos simultâneos.

## Fora de escopo

- Login, permissões e o tipo "organizador". Qualquer chamada pode mudar o status por enquanto.
- Validar a edição de `vagas` do evento contra inscrições já existentes (hoje dá para reduzir as
  vagas abaixo do número de inscritos). Fica como próxima etapa.
- Mensagem de erro específica ao excluir um evento ou participante que já tem inscrições. Continua
  o 409 genérico do `DataIntegrityViolationException`.
- Atualizar o README (a estrutura descrita lá já estava desatualizada).

## Anti-objetivos (seria falha mesmo que "funcionasse")

- Estourar as vagas quando duas inscrições chegam ao mesmo tempo.
- Quebrar com erro de carregamento preguiçoso (`open-in-view=false`): a resposta tem que ser
  montada dentro da transação.

## Regras (decididas com o usuário)

| Regra | Decisão |
|---|---|
| Status inicial | `PENDENTE` |
| Quem ocupa vaga | `PENDENTE` e `CONFIRMADA`. Só `CANCELADA` libera. |
| Reinscrição | Permitida depois de cancelar. Cria uma inscrição nova; a cancelada fica no histórico. |
| Permissões | Livre por enquanto, sem login. |

Regras que são decisão minha (o usuário não as viu uma a uma, ajustar se não servirem):

- Evento já encerrado (`dataFim` no passado) não aceita inscrição nova: 400.
- Não pode haver duas inscrições ativas (`PENDENTE` ou `CONFIRMADA`) do mesmo participante no
  mesmo evento: 409.
- Transições permitidas: `PENDENTE → CONFIRMADA`, `PENDENTE → CANCELADA`, `CONFIRMADA → CANCELADA`.
  `CANCELADA` é final. Qualquer outra transição, inclusive repetir o status atual, dá 409.
- Como `CANCELADA` é final e reinscrever cria uma linha nova, mudar status nunca ocupa vaga. Mesmo
  assim a transição precisa ser atômica: o service trava a linha da inscrição ao mudar o status,
  senão dois pedidos simultâneos leriam o mesmo status e o último a gravar venceria (por exemplo,
  `CANCELADA → CONFIRMADA`). A criação é protegida pela trava no evento. (Ajustado após a revisão
  final.)

## Contrato da API

Mesmo estilo de `/participantes`.

| Rota | Corpo / parâmetros | Resposta |
|---|---|---|
| `POST /inscricoes` | `{ "participanteId": 1, "eventoId": 2 }` | 201 + `Location: /inscricoes/{id}` |
| `GET /inscricoes/{id}` | | 200 ou 404 |
| `GET /inscricoes` | `eventoId`, `participanteId`, `status` (todos opcionais) + paginação | 200, `PagedModel` |
| `PATCH /inscricoes/{id}/status` | `{ "status": "CONFIRMADA" }` | 200 ou 404 / 409 |

Resposta (`InscricaoResponse`): `id`, `participanteId`, `participanteNome`, `eventoId`,
`eventoTitulo`, `status`, `dataInscricao`.

Erros: 404 participante/evento/inscrição inexistente, 409 duplicada, sem vaga ou transição
inválida, 400 evento encerrado ou corpo inválido. Tudo pelo `GlobalExceptionHandler` existente
(`ResourceNotFoundException`, `ConflictException`, `IllegalArgumentException`).

## Modelo

`Inscricao` (entidade, no estilo de `Evento`): `participante` (`@ManyToOne`, obrigatório),
`evento` (`@ManyToOne`, obrigatório), `status` (`@Enumerated(STRING)`, padrão `PENDENTE`),
`dataInscricao` (`LocalDateTime`, preenchida na criação) e `id`.
O enum `StatusInscricao` já existe em `acontece.api.enums` e não muda.

## Abordagens consideradas

1. **Contar inscrições ativas e travar o evento ao criar (escolhida, minha escolha).** O service
   busca o evento com `PESSIMISTIC_WRITE`, conta as ativas e só então grava. Sem coluna nova, sem
   risco de contador dessincronizado. Custo: pedidos para o mesmo evento passam um por vez, o que
   é aceitável nesta escala.
2. **Coluna `vagasOcupadas` em `Evento`.** Evita a contagem, mas duplica uma informação que pode
   sair de sincronia e muda a entidade `Evento` e seu CRUD.
3. **Sem trava, só contar.** Mais simples, mas dois pedidos simultâneos podem passar do limite.
   Fere o anti-objetivo principal.

## Deixado ao construtor

- Como montar a listagem com filtros opcionais (`@Query` com `is null` ou `Specification`).
- ~~Se o service recebe um `Clock`~~ Decidido na revisão final: o service recebe um `Clock` com
  fuso `America/Sao_Paulo` (`ClockConfig`), porque as datas do evento são horário de Brasília sem
  fuso e o servidor de produção roda em UTC.
- Nomes dos métodos de repository e a estrutura exata dos DTOs, seguindo `ParticipanteDTOs.kt`.

## Testes

Sem tocar no banco real do Supabase:

- Service e repository com `@DataJpaTest` (H2 em memória, já no classpath): vagas, duplicidade,
  reinscrição, evento encerrado, cada transição de status.
- Controller com `@WebMvcTest` e service simulado, como em `ParticipanteControllerTest`.
- Após a revisão final, `src/test/resources/application.properties` aponta todos os testes para o
  H2. Nenhum teste conecta no Supabase, inclusive o `contextLoads`, e `./mvnw test` é seguro.
