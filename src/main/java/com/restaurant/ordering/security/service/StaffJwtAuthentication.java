package com.restaurant.ordering.security.service;
import com.restaurant.ordering.security.repository.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.List;

@Component @RequiredArgsConstructor
public class StaffJwtAuthentication implements Converter<Jwt, AbstractAuthenticationToken> {
    private final StaffUserRepository users;
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var u = users.findByUsername(jwt.getSubject()).filter(user -> user.isEnabled())
            .orElseThrow(() -> new BadCredentialsException("Staff account unavailable"));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())), u.getUsername());
    }
}
