package com.activest.domain.user;

import com.activest.config.SecurityUtils;
import com.activest.domain.auth.dto.CodeConfirmDTO;
import com.activest.domain.auth.dto.CodeRequestDTO;
import com.activest.domain.auth.dto.CodeSentDTO;
import com.activest.domain.auth.dto.GoogleLoginRequestDTO;
import com.activest.domain.auth.dto.TokenResponseDTO;
import com.activest.domain.user.dto.EmailChangeDTO;
import com.activest.domain.user.dto.PasswordChangeDTO;
import com.activest.domain.user.dto.PreferencesDTO;
import com.activest.domain.user.dto.UserResponseDTO;
import com.activest.domain.user.dto.UserUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** O próprio usuário (docs/planos/PLANO_CADASTRO_USUARIO.md, 4.4). Tudo escopado pelo token. */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final UserAvatarService avatarService;

    public UserController(UserService userService, UserAvatarService avatarService) {
        this.userService = userService;
        this.avatarService = avatarService;
    }

    /**
     * Foto enviada (JPEG). A tela busca com o token e mostra como blob — {@code <img src>} nao manda o
     * header. A URL leva a versao ({@code ?v=}), entao o navegador pode guardar a resposta.
     */
    @GetMapping("/avatar")
    public ResponseEntity<byte[]> avatar() {
        UserAvatarService.Avatar a = avatarService.get(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(a.contentType()))
            .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate())
            .body(a.image());
    }

    /** Envio da foto (multipart, campo {@code file}): JPEG ou PNG, ate 2 MB, 128 px no minimo. */
    @PutMapping(value = "/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponseDTO uploadAvatar(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        avatarService.save(SecurityUtils.getCurrentUserId(), file);
        return userService.getUserById(SecurityUtils.getCurrentUserId());
    }

    /** Tira a foto enviada: volta a do Google (se houver) ou as iniciais. */
    @DeleteMapping("/avatar")
    public UserResponseDTO deleteAvatar() {
        avatarService.delete(SecurityUtils.getCurrentUserId());
        return userService.getUserById(SecurityUtils.getCurrentUserId());
    }

    @GetMapping
    public UserResponseDTO me() {
        return userService.getUserById(SecurityUtils.getCurrentUserId());
    }

    @PutMapping
    public UserResponseDTO update(@Valid @RequestBody UserUpdateDTO dto) {
        return userService.updateUser(SecurityUtils.getCurrentUserId(), dto);
    }

    @PutMapping("/preferences")
    public UserResponseDTO preferences(@Valid @RequestBody PreferencesDTO dto) {
        return userService.updatePreferences(SecurityUtils.getCurrentUserId(), dto);
    }

    /** Devolve tokens novos: a troca derruba as sessões abertas (D15), inclusive a desta chamada. */
    @PutMapping("/password")
    public TokenResponseDTO password(@Valid @RequestBody PasswordChangeDTO dto) {
        return userService.changePassword(SecurityUtils.getCurrentUserId(), dto);
    }

    @PostMapping("/email")
    public ResponseEntity<CodeSentDTO> requestEmailChange(@Valid @RequestBody EmailChangeDTO dto) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(userService.requestEmailChange(SecurityUtils.getCurrentUserId(), dto));
    }

    @PostMapping("/email/resend")
    public ResponseEntity<CodeSentDTO> resendEmailChange(@Valid @RequestBody CodeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(userService.resendEmailChange(SecurityUtils.getCurrentUserId(), dto.requestId()));
    }

    @PostMapping("/email/confirm")
    public UserResponseDTO confirmEmailChange(@Valid @RequestBody CodeConfirmDTO dto) {
        return userService.confirmEmailChange(SecurityUtils.getCurrentUserId(), dto);
    }

    @PostMapping("/google")
    public UserResponseDTO linkGoogle(@Valid @RequestBody GoogleLoginRequestDTO dto) {
        return userService.linkGoogle(SecurityUtils.getCurrentUserId(), dto.credential());
    }

    @DeleteMapping("/google")
    public UserResponseDTO unlinkGoogle() {
        return userService.unlinkGoogle(SecurityUtils.getCurrentUserId());
    }

    /** Aceite da versão atual dos termos (D19). Fica liberado mesmo com o aceite pendente. */
    @PostMapping("/terms")
    public UserResponseDTO acceptTerms() {
        return userService.acceptTerms(SecurityUtils.getCurrentUserId());
    }

    @PostMapping("/delete-request")
    public ResponseEntity<CodeSentDTO> requestDeletion() {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(userService.requestDeletion(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/delete-request/resend")
    public ResponseEntity<CodeSentDTO> resendDeletion(@Valid @RequestBody CodeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(userService.resendDeletion(SecurityUtils.getCurrentUserId(), dto.requestId()));
    }

    /** Exclusão definitiva com o código do e-mail (D14). */
    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @RequestBody CodeConfirmDTO dto) {
        userService.deleteAccount(SecurityUtils.getCurrentUserId(), dto);
        return ResponseEntity.noContent().build();
    }
}
