# acontece-api

API REST desenvolvida em kotlin para gerenciamento de eventos, inscrições e participantes da plataforma Acontece

## Estrutura do Projeto

```
src/main/kotlin/com/gestaoeventos/
├── config/                          # Configurações globais (OpenFeign, CORS, Swagger)
│   └── FeignConfig.kt
├── exception/                       # Tratamento centralizado de erros (@ControllerAdvice)
│   ├── GlobalExceptionHandler.kt
│   └── ResourceNotFoundException.kt
├── model/                           # Entidades JPA e Enums da aplicação
│   ├── Usuario.kt                   # Class Base com @Inheritance(strategy = InheritanceType.JOINED)
│   ├── Participante.kt              # Filha (herda de Usuario)
│   ├── Evento.kt                    # Dados do evento e endereço
│   ├── Inscricao.kt                 # Relacionamento @ManyToOne com Participante e Evento
│   └── enums/
│       └── StatusInscricao.kt       # enum class (PENDENTE, CONFIRMADA, CANCELADA)
├── modules/                         # Funcionalidades organizadas por módulo
│   ├── usuario/                     # Módulo de Participantes / Usuários
│   │   ├── controller/
│   │   │   └── ParticipanteController.kt
│   │   ├── service/
│   │   │   └── ParticipanteService.kt
│   │   ├── repository/
│   │   │   └── ParticipanteRepository.kt
│   │   └── dto/
│   │       └── ParticipanteDTOs.kt  # Data classes de Request (@field:NotBlank, @field:CPF) e Response
│   ├── evento/                      # Módulo de Eventos
│   │   ├── controller/
│   │   │   └── EventoController.kt
│   │   ├── service/
│   │   │   └── EventoService.kt     # Integração com ViaCEP antes de salvar
│   │   ├── repository/
│   │   │   └── EventoRepository.kt  # Custom queries (título e intervalo de datas)
│   │   └── dto/
│   │       └── EventoDTOs.kt        # Data classes de Request, Response e ViaCepResponseDTO
│   └── inscricao/                   # Módulo de Inscrições
│       ├── controller/
│       │   └── InscricaoController.kt # Endpoint PATCH para alterar StatusInscricao
│       ├── service/
│       │   └── InscricaoService.kt
│       ├── repository/
│       │   └── InscricaoRepository.kt
│       └── dto/
│           └── InscricaoDTOs.kt     # Data classes de Request, Response e      AtualizarStatusDTO
└── client/                          # Integração de APIs externas
    └── ViaCepClient.kt              # Interface OpenFeign declarativa para o ViaCEP
```
