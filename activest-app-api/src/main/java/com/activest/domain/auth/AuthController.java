package com.activest.domain.auth;

import com.activest.domain.auth.dto.CodeConfirmDTO;
import com.activest.domain.auth.dto.CodeRequestDTO;
import com.activest.domain.auth.dto.CodeSentDTO;
import com.activest.domain.auth.dto.GoogleLoginRequestDTO;
import com.activest.domain.auth.dto.LoginRequestDTO;
import com.activest.domain.auth.dto.PasswordForgotDTO;
import com.activest.domain.auth.dto.PasswordResetDTO;
import com.activest.domain.auth.dto.RefreshRequestDTO;
import com.activest.domain.auth.dto.SignupStartDTO;
import com.activest.domain.auth.dto.SignupStatusDTO;
import com.activest.domain.auth.dto.TokenResponseDTO;
import com.activest.domain.user.LegalTerms;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Rotas públicas de entrada e cadastro (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.7). O antigo
 * {@code POST /auth/register} saiu (D11): cadastro sem verificação era porta dos fundos.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;
    private final PasswordResetService passwordResetService;
    private final AuthSettings settings;

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.loginWithEmail(request, ClientIp.of(http)));
    }

    @PostMapping("/google")
    public ResponseEntity<TokenResponseDTO> googleLogin(@Valid @RequestBody GoogleLoginRequestDTO request) {
        return ResponseEntity.ok(authService.processGoogleToken(request.credential()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO request) {
        return ResponseEntity.ok(authService.refreshToken(request.refreshToken()));
    }

    @GetMapping("/signup/enabled")
    public SignupStatusDTO signupEnabled() {
        return new SignupStatusDTO(settings.isSignupEnabled(), LegalTerms.CURRENT_VERSION);
    }

    @PostMapping("/signup/start")
    public ResponseEntity<CodeSentDTO> signupStart(@Valid @RequestBody SignupStartDTO dto, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(signupService.start(dto, ClientIp.of(http)));
    }

    @PostMapping("/signup/resend")
    public ResponseEntity<CodeSentDTO> signupResend(@Valid @RequestBody CodeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(signupService.resend(dto.requestId()));
    }

    @PostMapping("/signup/confirm")
    public ResponseEntity<TokenResponseDTO> signupConfirm(@Valid @RequestBody CodeConfirmDTO dto) {
        return ResponseEntity.ok(signupService.confirm(dto));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<CodeSentDTO> forgot(@Valid @RequestBody PasswordForgotDTO dto, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(passwordResetService.forgot(dto.email(), ClientIp.of(http)));
    }

    @PostMapping("/password/resend")
    public ResponseEntity<CodeSentDTO> forgotResend(@Valid @RequestBody CodeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(passwordResetService.resend(dto.requestId()));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetDTO dto) {
        passwordResetService.reset(dto);
        return ResponseEntity.noContent().build();
    }
}
