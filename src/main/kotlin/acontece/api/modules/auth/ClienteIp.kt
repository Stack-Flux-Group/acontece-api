package acontece.api.modules.auth

import jakarta.servlet.http.HttpServletRequest

// IP real do cliente, para o limite de tentativas de login. A API roda atras do
// Cloudflare + load balancer do Render: o remoteAddr e o IP do proxy, que muda a
// cada request, e o RemoteIpValve do Tomcat nao confia em proxy de IP publico.
// O Render grava o IP real do cliente na primeira posicao do X-Forwarded-For,
// entao e ela que usamos; sem o cabecalho (local/teste), cai no remoteAddr.
fun HttpServletRequest.ipDoCliente(): String =
    getHeader("X-Forwarded-For")
        ?.split(",")
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: remoteAddr
