package com.activest.domain.user;

import com.activest.domain.auth.AuthService;
import com.activest.domain.auth.Emails;
import com.activest.domain.auth.GoogleTokenVerifier;
import com.activest.domain.auth.PasswordResetService;
import com.activest.domain.auth.Phones;
import com.activest.domain.auth.dto.CodeConfirmDTO;
import com.activest.domain.auth.dto.CodeSentDTO;
import com.activest.domain.auth.dto.TokenResponseDTO;
import com.activest.domain.auth.mail.AccountMailer;
import com.activest.domain.auth.mail.AccountNotice;
import com.activest.domain.auth.verification.PendingCode;
import com.activest.domain.auth.verification.VerificationCodeService;
import com.activest.domain.auth.verification.VerificationPurpose;
import com.activest.domain.integration.ConnectionStatus;
import com.activest.domain.integration.IntegrationConnectionRepository;
import com.activest.domain.user.dto.EmailChangeDTO;
import com.activest.domain.user.dto.PasswordChangeDTO;
import com.activest.domain.user.dto.PreferencesDTO;
import com.activest.domain.user.dto.UserResponseDTO;
import com.activest.domain.user.dto.UserUpdateDTO;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Edição do próprio usuário (docs/planos/PLANO_CADASTRO_USUARIO.md, seção 4): perfil, CPF, tema,
 * senha, e-mail com código, vínculo com o Google, aceite dos termos e exclusão da conta.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserCpfService cpfService;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService codes;
    private final AccountMailer mailer;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AuthService authService;
    private final IntegrationConnectionRepository connections;
    private final JdbcTemplate jdbc;
    private final UserAvatarRepository avatars;

    public UserService(UserRepository userRepository, UserCpfService cpfService, PasswordEncoder passwordEncoder,
                       VerificationCodeService codes, AccountMailer mailer, GoogleTokenVerifier googleTokenVerifier,
                       AuthService authService, IntegrationConnectionRepository connections, JdbcTemplate jdbc,
                       UserAvatarRepository avatars) {
        this.avatars = avatars;
        this.userRepository = userRepository;
        this.cpfService = cpfService;
        this.passwordEncoder = passwordEncoder;
        this.codes = codes;
        this.mailer = mailer;
        this.googleTokenVerifier = googleTokenVerifier;
        this.authService = authService;
        this.connections = connections;
        this.jdbc = jdbc;
    }

    public UserResponseDTO getUserById(UUID id) {
        return toDTO(find(id));
    }

    @Transactional
    public UserResponseDTO updateUser(UUID userId, UserUpdateDTO dto) {
        User user = find(userId);
        user.setName(dto.name().strip());

        String phone = Phones.normalize(dto.phone());
        if (phone != null && userRepository.existsByPhoneAndIdNot(phone, userId)) {
            throw new BusinessException("Este celular já está cadastrado em outra conta.");
        }
        user.setPhone(phone);

        if (dto.cpf() != null) {
            updateCpf(user, dto.cpf());
        }
        return toDTO(userRepository.save(user));
    }

    /**
     * CPF: vazio tira, número troca. Com conexão viva com a B3 o CPF não muda (D20): a conexão usa o
     * do perfil, e ficaria apontando para outro documento.
     */
    private void updateCpf(User user, String raw) {
        String created = raw.isBlank() ? null : cpfService.normalize(raw);
        String current = cpfService.read(user).orElse(null);
        if (Objects.equals(created, current)) {
            return;
        }
        // Fontes que conectam pelo CPF do perfil (hoje a B3; docs/planos/PLANO_INTEGRACOES.md, 10.1)
        for (var source : com.activest.domain.integration.source.SourceDescriptors.all()) {
            if (source.credential() == com.activest.domain.integration.source.SourceDescriptor.CredentialKind.DOCUMENT
                    && connections.existsByUserIdAndProviderAndStatusNot(user.getId(), source.provider(),
                        ConnectionStatus.REVOKED)) {
                throw new BusinessException("O CPF não pode ser alterado enquanto houver conexão com a "
                    + source.name() + ". Desconecte a " + source.name() + " em Integrações e tente de novo.");
            }
        }
        if (created == null) {
            cpfService.clear(user);
            return;
        }
        if (userRepository.existsByCpfHashAndIdNot(cpfService.hash(created), user.getId())) {
            throw new BusinessException("Este CPF já está cadastrado em outra conta.");
        }
        cpfService.apply(user, created);
    }

    @Transactional
    public UserResponseDTO updatePreferences(UUID userId, PreferencesDTO dto) {
        User user = find(userId);
        user.setTheme(dto.theme());
        return toDTO(userRepository.save(user));
    }

    /**
     * Alterar (pede a atual) ou definir a primeira senha (conta só com Google). Derruba as outras
     * sessões (D15) e devolve tokens novos para esta continuar.
     */
    @Transactional
    public TokenResponseDTO changePassword(UUID userId, PasswordChangeDTO dto) {
        User user = find(userId);
        if (user.hasPassword()) {
            if (dto.currentPassword() == null || !passwordEncoder.matches(dto.currentPassword(), user.getPasswordHash())) {
                throw new BusinessException("Senha atual incorreta.");
            }
            if (passwordEncoder.matches(dto.newPassword(), user.getPasswordHash())) {
                throw new BusinessException("A nova senha precisa ser diferente da atual.");
            }
        }
        PasswordResetService.setPassword(user, passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
        mailer.sendNotice(AccountNotice.PASSWORD_CHANGED, user.getEmail(), user.getName());
        return authService.tokensFor(user);
    }

    /** Troca de e-mail: o código vai para o endereço NOVO; sem ele, nada muda (E3). */
    public CodeSentDTO requestEmailChange(UUID userId, EmailChangeDTO dto) {
        User user = find(userId);
        String created = Emails.normalize(dto.newEmail());
        if (created.equals(user.getEmail())) {
            throw new BusinessException("Este já é o e-mail da sua conta.");
        }
        if (userRepository.existsByEmail(created)) {
            throw new BusinessException("Este e-mail já está em uso em outra conta.");
        }
        String requestId = VerificationCodeService.newRequestId();
        VerificationCodeService.Issued issued = codes.issue(requestId, VerificationPurpose.CHANGE_EMAIL, created,
            Map.of("userId", userId.toString()), false);
        mailer.sendCode(VerificationPurpose.CHANGE_EMAIL, created, user.getName(), issued.code(), codes.expiresInSeconds() / 60);
        return sent(requestId, created);
    }

    public CodeSentDTO resendEmailChange(UUID userId, String requestId) {
        User user = find(userId);
        VerificationCodeService.Issued issued = codes.resend(requestId, VerificationPurpose.CHANGE_EMAIL);
        requireOwner(issued.pending(), userId);
        mailer.sendCode(VerificationPurpose.CHANGE_EMAIL, issued.pending().email(), user.getName(), issued.code(),
            codes.expiresInSeconds() / 60);
        return sent(requestId, issued.pending().email());
    }

    @Transactional
    public UserResponseDTO confirmEmailChange(UUID userId, CodeConfirmDTO dto) {
        PendingCode p = codes.verify(dto.requestId(), VerificationPurpose.CHANGE_EMAIL, dto.code());
        requireOwner(p, userId);
        User user = find(userId);
        if (userRepository.existsByEmail(p.email())) {
            throw new BusinessException("Este e-mail acabou de ser cadastrado em outra conta.");
        }
        String previous = user.getEmail();
        user.setEmail(p.email());
        user.setEmailVerifiedAt(LocalDateTime.now());
        UserResponseDTO response = toDTO(userRepository.save(user));
        mailer.sendNotice(AccountNotice.EMAIL_CHANGED, previous, user.getName());
        return response;
    }

    @Transactional
    public UserResponseDTO linkGoogle(UUID userId, String credential) {
        User user = find(userId);
        GoogleTokenVerifier.GoogleProfile profile = googleTokenVerifier.verify(credential);
        userRepository.findByGoogleId(profile.subject())
            .filter(other -> !other.getId().equals(userId))
            .ifPresent(other -> {
                throw new BusinessException("Esta conta Google já está vinculada a outro usuário do Activest.");
            });
        user.setGoogleId(profile.subject());
        if (user.getAvatarUrl() == null) {
            user.setAvatarUrl(profile.pictureUrl());
        }
        return toDTO(userRepository.save(user));
    }

    /** Sem senha, desvincular deixaria a conta sem como entrar (E7). */
    @Transactional
    public UserResponseDTO unlinkGoogle(UUID userId) {
        User user = find(userId);
        if (user.getGoogleId() == null) {
            throw new BusinessException("A conta não está vinculada ao Google.");
        }
        if (!user.hasPassword()) {
            throw new BusinessException("Defina uma senha antes de desvincular o Google: sem ela não haveria "
                + "como entrar na conta.");
        }
        user.setGoogleId(null);
        return toDTO(userRepository.save(user));
    }

    @Transactional
    public UserResponseDTO acceptTerms(UUID userId) {
        User user = find(userId);
        user.acceptTerms(LocalDateTime.now());
        return toDTO(userRepository.save(user));
    }

    public CodeSentDTO requestDeletion(UUID userId) {
        User user = find(userId);
        String requestId = VerificationCodeService.newRequestId();
        VerificationCodeService.Issued issued = codes.issue(requestId, VerificationPurpose.DELETE_ACCOUNT, user.getEmail(),
            Map.of("userId", userId.toString()), false);
        mailer.sendCode(VerificationPurpose.DELETE_ACCOUNT, user.getEmail(), user.getName(), issued.code(),
            codes.expiresInSeconds() / 60);
        return sent(requestId, user.getEmail());
    }

    public CodeSentDTO resendDeletion(UUID userId, String requestId) {
        User user = find(userId);
        VerificationCodeService.Issued issued = codes.resend(requestId, VerificationPurpose.DELETE_ACCOUNT);
        requireOwner(issued.pending(), userId);
        mailer.sendCode(VerificationPurpose.DELETE_ACCOUNT, user.getEmail(), user.getName(), issued.code(),
            codes.expiresInSeconds() / 60);
        return sent(requestId, user.getEmail());
    }

    /**
     * Exclusão definitiva (D14). Pelo banco, não pela entidade: as FKs de dado do usuário são
     * {@code ON DELETE CASCADE} (carteira, posições, transações, notas, estratégia, metas, preço teto,
     * integrações e credenciais), e o cascade do JPA carregaria tudo na memória antes de apagar.
     */
    @Transactional
    public void deleteAccount(UUID userId, CodeConfirmDTO dto) {
        PendingCode p = codes.verify(dto.requestId(), VerificationPurpose.DELETE_ACCOUNT, dto.code());
        requireOwner(p, userId);
        User user = find(userId);
        String email = user.getEmail();
        String name = user.getName();
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        log.info("Conta {} excluída a pedido do usuário", userId);
        mailer.sendNotice(AccountNotice.ACCOUNT_DELETED, email, name);
    }

    private void requireOwner(PendingCode p, UUID userId) {
        if (!userId.toString().equals(p.get("userId"))) {
            throw new BusinessException("O código expirou ou já foi usado. Peça um novo código.");
        }
    }

    private CodeSentDTO sent(String requestId, String email) {
        return new CodeSentDTO(requestId, Emails.mask(email), codes.resendInSeconds(), codes.expiresInSeconds());
    }

    private User find(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private UserResponseDTO toDTO(User user) {
        return new UserResponseDTO(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getAvatarUrl(),
            avatars.findUpdatedAt(user.getId())
                .map(t -> t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()).orElse(null),
            user.getPhone(),
            cpfService.read(user).map(UserCpfService::mask).orElse(null),
            user.getEmailVerifiedAt() != null,
            user.hasPassword(),
            user.getGoogleId() != null,
            user.isAdmin(),
            user.getTheme().name(),
            user.getTermsVersion(),
            user.getTermsAcceptedAt(),
            LegalTerms.CURRENT_VERSION,
            !user.termsUpToDate(),
            user.getCreatedAt()
        );
    }
}
