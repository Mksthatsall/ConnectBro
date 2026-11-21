package com.connectBro.ApiGateway.filter;
import com.connectBro.ApiGateway.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory <AuthenticationFilter.Config>{


    private final JwtService jwtService;

    public AuthenticationFilter(JwtService jwtService) {
        super(Config.class);
        this.jwtService= jwtService;
    }


    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain)-> {
            log.info("Auth request: {}", exchange.getRequest().getURI());

            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) return chain.filter(exchange);

            final String tokenHeader= exchange.getRequest().getHeaders().getFirst("Authorization");


            if(tokenHeader==null || !tokenHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            final String token = tokenHeader.substring("Bearer ".length()).trim();
            if (token.isEmpty()) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            try {
                String userId = jwtService.getUserIdFromToken(token);
              ServerWebExchange mutatedExchange= exchange.mutate()
                        .request(r -> r.headers(headers -> {
                            headers.remove("X-User-Id");
                            headers.set("X-User-Id", userId);
                        }))
                        .build();

              return chain.filter(mutatedExchange);
            } catch(JwtException e){

                log.error("JWT Exception {}", e.getLocalizedMessage());

                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);

                return exchange.getResponse().setComplete();


            }


        });
    }


    static class Config {

    }




}
