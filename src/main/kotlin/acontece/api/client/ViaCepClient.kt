package acontece.api.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@FeignClient(name = "viacep", url = "https://viacep.com.br/ws")
interface ViaCepClient {

    @GetMapping("/{cep}/json/") fun buscarCep(@PathVariable("cep") cep: String): ViaCepResponseDTO
}
