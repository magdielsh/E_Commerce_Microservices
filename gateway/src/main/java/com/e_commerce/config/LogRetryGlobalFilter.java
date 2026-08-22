package com.e_commerce.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class LogRetryGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LogRetryGlobalFilter.class);


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // El Gateway guarda el contador en este atributo interno de la petición
        Integer retryCount = exchange.getAttribute("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_RETRY_ITERATION_ATTR");
        //log.info("!!!!!!!!!!!!!!!!!!...ESTOY ENTRNDO A ESTA PINGA...!!!!!!!!!!!!!!!!!!!!");
        // Si el contador existe y es mayor a 0, significa que es un reintento en progreso
        if (retryCount != null && retryCount > 0) {
            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().name();
            logger.info("⚠️ REINTENTO DETECTADO: El método {} en la ruta '{}' está en su intento número: {}", method, path, retryCount);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Un orden bajo asegura que se ejecute en sintonía con el filtro de reintentos
        return -5;
    }
}
