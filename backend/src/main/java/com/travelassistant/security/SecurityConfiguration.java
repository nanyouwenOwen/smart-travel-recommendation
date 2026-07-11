package com.travelassistant.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.travelassistant.auth.AuthProperties;
import com.travelassistant.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean SecretKey jwtKey(AuthProperties p) {
        return new SecretKeySpec(p.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key, AuthProperties p) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();
        decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(p.issuer()));
        return decoder;
    }
    @Bean Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(UserRepository users) {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        return jwt -> {
            if (jwt.getSubject() == null || users.findById(jwt.getSubject()).isEmpty())
                throw new BadCredentialsException("User is unavailable");
            return new JwtAuthenticationToken(jwt, authorities.convert(jwt), jwt.getSubject());
        };
    }
    @Bean SecurityFilterChain security(HttpSecurity http, SecurityErrorWriter errors,
            Converter<Jwt, AbstractAuthenticationToken> converter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/**", "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401, "UNAUTHORIZED", "需要有效的身份认证"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 403, "FORBIDDEN", "无权执行此操作")))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401, "UNAUTHORIZED", "需要有效的身份认证")))
                .build();
    }
}
