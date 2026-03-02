package com.company.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limiter configuration for api-gateway.
 *
 * Uses Redis token bucket algorithm via Spring Cloud Gateway.
 * Current limits:
 *   - 20 requests/second replenish rate per client IP
 *   - 40 requests burst capacity
 *
 * Applied to all routes via application.yml default-filters.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Rate limiter: 20 req/s replenish, 40 burst.
     * Backed by Redis — keys are stored as gateway:rate-limit:{key}
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(
                20,  // replenishRate  — tokens added per second
                40,  // burstCapacity  — max tokens bucket can hold
                1    // requestedTokens — tokens consumed per request
        );
    }

    /**
     * Key resolver: rate limit by client IP address.
     * Swap for user ID / API key based resolvers in production.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown"
        );
    }
}
