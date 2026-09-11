package com.electronics.store.service;

import com.electronics.store.dto.request.LoginRequest;
import com.electronics.store.dto.request.RegisterRequest;
import com.electronics.store.entity.UserEntity;
import com.electronics.store.entity.Role;
import com.electronics.store.mapper.UserMapper;
import com.electronics.store.repository.UserRepository;
import com.electronics.store.security.CustomUserDetails;
import com.electronics.store.security.JwtService;
import com.electronics.store.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock AuthenticationManager authentication;
    @Mock JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private AuthServiceImpl service() {
        return new AuthServiceImpl(users, new UserMapper(), encoder, authentication, jwt);
    }

    @Test
    void loginNormalizesEmailIndependentlyOfLocaleAndPreservesPassword() {
        UserEntity user = UserEntity.builder().id(1L).email("i@example.test").role(Role.USER).build();
        CustomUserDetails principal = new CustomUserDetails(user);
        when(authentication.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(jwt.generateToken(principal)).thenReturn("test-token");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(service().login(new LoginRequest(" I@EXAMPLE.TEST ", " password ")).accessToken())
                    .isEqualTo("test-token");
        } finally {
            Locale.setDefault(previous);
        }
        ArgumentCaptor<Authentication> credentials = ArgumentCaptor.forClass(Authentication.class);
        verify(authentication).authenticate(credentials.capture());
        assertThat(credentials.getValue().getName()).isEqualTo("i@example.test");
        assertThat(credentials.getValue().getCredentials()).isEqualTo(" password ");
        verifyNoInteractions(users);
    }

    @Test
    void rejectedPasswordNeverIssuesTokenOrWritesUser() {
        when(authentication.authenticate(any())).thenThrow(new BadCredentialsException("Rejected"));
        assertThatThrownBy(() -> service().login(new LoginRequest("user@example.test", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(jwt, users);
    }

    @Test
    void registrationAcceptsExactly72Utf8BytesAndHashesWithoutTrimming() {
        String password = " " + "\u00e9".repeat(35) + " ";
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service().register(new RegisterRequest(" Customer ", "USER@EXAMPLE.TEST", password, "   "));
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(saved.capture());
        assertThat(encoder.matches(password, saved.getValue().getPassword())).isTrue();
        assertThat(encoder.matches(password.trim(), saved.getValue().getPassword())).isFalse();
        assertThat(saved.getValue().getEmail()).isEqualTo("user@example.test");
        assertThat(saved.getValue().getPhone()).isNull();
    }

    @Test
    void overlongUtf8PasswordIsRejectedBeforeAnyPersistenceOrTokenWork() {
        assertThatThrownBy(() -> service().register(new RegisterRequest(
                "Customer", "user@example.test", "\u00e9".repeat(37), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("72 UTF-8 bytes");
        verifyNoInteractions(users, authentication, jwt);
    }
}
