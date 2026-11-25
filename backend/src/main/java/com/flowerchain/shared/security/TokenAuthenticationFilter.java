package com.flowerchain.shared.security;

import com.flowerchain.shared.api.FlowerChainPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectProvider<TokenPrincipalResolver> resolversProvider;

    public TokenAuthenticationFilter(ObjectProvider<TokenPrincipalResolver> resolversProvider) {
        this.resolversProvider = resolversProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String rawToken = header.substring(7).trim();
            if (!rawToken.isEmpty()) {
                for (TokenPrincipalResolver resolver : resolversProvider) {
                    Optional<FlowerChainPrincipal> resolved = resolver.resolveByToken(rawToken);
                    if (resolved.isPresent()) {
                        FlowerChainPrincipal principal = resolved.get();
                        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                .collect(Collectors.toList());
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        break;
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
