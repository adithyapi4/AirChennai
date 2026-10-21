package com.aa.airchennai.employee.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig
{
    @Bean
    public AuthenticationProvider employeeAuthenticationProvider(
            EmployeeUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder)
    {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            AuthenticationProvider employeeAuthenticationProvider) throws Exception
    {
        http.authenticationProvider(employeeAuthenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/employee/login",
                                "/employee/forgot-password",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico"
                        ).permitAll()
                        .requestMatchers("/employee/hr/**").hasRole("HR")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/employee/login")
                        .loginProcessingUrl("/employee/login")
                        .usernameParameter("employeeId")
                        .passwordParameter("password")

                        .successHandler((request, response, authentication) -> {
                            Object principal = authentication.getPrincipal();

                            String destination = "/employee/dashboard";

                            if (principal instanceof EmployeeUserDetails employee)
                            {
                                if (employee.isMustChangePassword())
                                {
                                    destination = "/employee/change-password";
                                }
                                else
                                {
                                    destination = switch (employee.getDepartment())
                                    {
                                        case "ADMIN" -> "/employee/admin/dashboard";
                                        case "HR" -> "/employee/hr/dashboard";
                                        case "COUNTER" -> "/employee/counter/dashboard";
                                        case "OPERATIONS" -> "/employee/operations/dashboard";
                                        case "FLEET" -> "/employee/fleet/dashboard";
                                        default -> "/employee/dashboard";
                                    };
                                }
                            }

                            response.sendRedirect(
                                    request.getContextPath() + destination
                            );
                        })

                        .failureUrl("/employee/login?error")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/employee/logout")
                        .logoutSuccessUrl("/employee/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        http.addFilterAfter(
                new MustChangePasswordFilter(),
                AuthorizationFilter.class
        );

        return http.build();
    }
}
