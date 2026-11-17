package acontece.api.enums

enum class StatusInscricao {
    PENDENTE,
    CONFIRMADA,
    CANCELADA;

    companion object {
        // Status que ocupam vaga no evento. CANCELADA libera a vaga.
        val QUE_OCUPAM_VAGA = setOf(PENDENTE, CONFIRMADA)
    }
}
