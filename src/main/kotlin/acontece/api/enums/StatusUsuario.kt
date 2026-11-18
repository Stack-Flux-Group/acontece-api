package acontece.api.enums

// A conta nunca é apagada do banco, porque as inscrições (mesmo canceladas) apontam para ela.
// - ATIVO: usa o sistema normalmente.
// - INATIVO: bloqueada pelo organizador; não entra nem usa o token. Pode voltar a ATIVO.
// - EXCLUIDO: o participante (ou o organizador) excluiu a conta. Os dados pessoais foram
//   apagados e a conta não volta mais; só fica a linha para o histórico das inscrições.
enum class StatusUsuario {
    ATIVO,
    INATIVO,
    EXCLUIDO,
}
