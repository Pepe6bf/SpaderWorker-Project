package site.devtown.spadeworker.global.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsUtils;
import site.devtown.spadeworker.domain.auth.exception.CustomAuthenticationEntryPoint;
import site.devtown.spadeworker.domain.auth.filter.TokenAuthenticationFilter;
import site.devtown.spadeworker.domain.auth.handler.OAuth2AuthenticationFailureHandler;
import site.devtown.spadeworker.domain.auth.handler.OAuth2AuthenticationSuccessHandler;
import site.devtown.spadeworker.domain.auth.handler.TokenAccessDeniedHandler;
import site.devtown.spadeworker.domain.auth.repository.OAuth2AuthorizationRequestBasedOnCookieRepository;
import site.devtown.spadeworker.domain.auth.service.JwtService;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    private final TokenAccessDeniedHandler tokenAccessDeniedHandler;
    private final OAuth2AuthorizationRequestBasedOnCookieRepository oAuth2AuthorizationRequestBasedOnCookieRepository;
    private final JwtService jwtService;
    private final OAuth2UserService oAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        // CSRF & Form Login ?????? ????????????
        http
                .csrf().disable()
                .formLogin().disable()
                .httpBasic().disable();

        // ????????? ???????????? ?????? ?????????, ?????? ????????? STATELESS ??? ??????
        http
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // ????????? ?????? ?????? ??????
        http
                .authorizeRequests()
                .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                // AUTH API
                .antMatchers(POST, "/api/auth/refresh").permitAll()
                // Project API
                .antMatchers(GET, "/api/projects/**").permitAll()
                // ???????????? ?????? ?????? ??????
                .anyRequest().authenticated();

        // JWT ?????? ?????? ??? ???????????? ??????
        http
                .addFilterBefore(
                        new TokenAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class
                )
                .exceptionHandling()
                // ?????? ?????? ???????????? ????????? ????????? ??????
                .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                .accessDeniedHandler(tokenAccessDeniedHandler);

        // front ?????? login ??? ????????? url
        http.oauth2Login()
                .authorizationEndpoint()
                .baseUri("/api/oauth2/authorization")
                .authorizationRequestRepository(oAuth2AuthorizationRequestBasedOnCookieRepository);

        // OAuth Server ??????????????? ??????
        http.oauth2Login()
                .redirectionEndpoint()
                .baseUri("/*/oauth2/code/*");

        // ?????? ??? user ????????? ????????? ????????? ??????
        http.oauth2Login()
                .userInfoEndpoint()
                .userService(oAuth2UserService);

        // OAuth2 ??????/?????? ??? ?????? ??? ????????? ??????
        http.oauth2Login()
                .successHandler(oAuth2AuthenticationSuccessHandler)
                .failureHandler(oAuth2AuthenticationFailureHandler);

        return http.build();
    }

    /**
     * Password Encoder
     */
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
