package acontece.api.config

import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Configuration

// Srping carregar a classe na inicialização
@Configuration

// Procurar as interfaces do feignClient
@EnableFeignClients(basePackages = ["acontece.api.client"])
class FeignConfig {}
