package uk.gov.companieshouse.web.emergencyauthcodeweb.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import uk.gov.companieshouse.auth.filter.HijackFilter;
import uk.gov.companieshouse.auth.filter.UserAuthFilter;

import static uk.gov.companieshouse.csrf.config.ChsCsrfMitigationHttpSecurityBuilder.configureWebCsrfMitigations;

@Configuration
@EnableWebSecurity
public class WebSecurity {

    @Bean
    @Order(1)
    public SecurityFilterChain authCodeSecurityFilterChain(HttpSecurity http) throws Exception {
        return configureWebCsrfMitigations(
                http.securityMatcher("/auth-code-requests/start", "/auth-code-requests/accessibility-statement")
                        .addFilterBefore(new HijackFilter(), BasicAuthenticationFilter.class)
        ).build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain eacWebSecurityFilterChain(HttpSecurity http) throws Exception {
        return configureWebCsrfMitigations(
                http.securityMatcher("/auth-code-requests/company/**", "/auth-code-requests/requests/**")
                        .addFilterBefore(new HijackFilter(), BasicAuthenticationFilter.class)
                        .addFilterBefore(new UserAuthFilter(), BasicAuthenticationFilter.class)
        ).build();
    }
}

