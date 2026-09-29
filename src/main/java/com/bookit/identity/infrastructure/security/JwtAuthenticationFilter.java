package com.bookit.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtService jwtService;
    
    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // STEP 1: Read the boarding pass from the request header
        String authHeader = request.getHeader("Authorization");

        // STEP 2: Guard — if no boarding pass or wrong format, just let them through
        //         (Spring Security will handle blocking them at the endpoint level)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // STEP 3: Strip the "Bearer " prefix (7 characters) to get just the token
        String token = authHeader.substring(7);

        // STEP 4: Validate the boarding pass using our JwtService
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // STEP 5: Token is VALID — extract the passenger's identity
        UUID userId = jwtService.extractUserId(token);
        List<String> roles = jwtService.extractRoles(token);

        // STEP 6: Convert role strings into Spring Security's GrantedAuthority objects
        //         "CUSTOMER" → SimpleGrantedAuthority("ROLE_CUSTOMER")
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        // STEP 7: Create the "sticky note" — the Authentication object
        // STEP 8: Place the sticky note in the tray for this request's thread
        // Only set authentication if the context doesn't already have one
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId.toString(),  // principal (who they are)
                            null,               // credentials (null — we don't re-verify password)
                            authorities         // what they're allowed to do
                    );
            authentication.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        
        // STEP 9: Let the request continue through to the controller
        filterChain.doFilter(request, response);
    }
}
