package com.activest.domain.goal;

import com.activest.domain.goal.dto.GoalCreateDTO;
import com.activest.domain.goal.dto.GoalResponseDTO;
import com.activest.domain.goal.dto.GoalUpdateDTO;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
import com.activest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    /**
     * Lista as metas do usuário, mais recentes primeiro.
     */
    public List<GoalResponseDTO> getGoals(UUID userId) {
        return goalRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Cria uma meta. Nasce sempre ACTIVE e com valor acumulado zerado —
     * o progresso é registrado depois, via update.
     */
    @Transactional
    public GoalResponseDTO createGoal(UUID userId, GoalCreateDTO dto) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Goal goal = Goal.builder()
            .user(user)
            .name(dto.name())
            .targetAmount(dto.targetAmount())
            .currentAmount(BigDecimal.ZERO)
            .targetDate(dto.targetDate())
            .status(GoalStatus.ACTIVE)
            .build();

        return toDTO(goalRepository.save(goal));
    }

    /**
     * Atualiza uma meta. Campos nulos no DTO são ignorados, permitindo
     * atualização parcial (ex: enviar só o valor acumulado).
     */
    @Transactional
    public GoalResponseDTO updateGoal(UUID userId, UUID goalId, GoalUpdateDTO dto) {
        Goal goal = findOwnedGoal(userId, goalId);

        if (dto.name() != null)          goal.setName(dto.name());
        if (dto.targetAmount() != null)  goal.setTargetAmount(dto.targetAmount());
        if (dto.currentAmount() != null) goal.setCurrentAmount(dto.currentAmount());
        if (dto.targetDate() != null)    goal.setTargetDate(dto.targetDate());
        if (dto.status() != null)        goal.setStatus(dto.status());

        return toDTO(goalRepository.save(goal));
    }

    /**
     * Exclui uma meta do usuário.
     */
    @Transactional
    public void deleteGoal(UUID userId, UUID goalId) {
        goalRepository.delete(findOwnedGoal(userId, goalId));
    }

    /**
     * Busca a meta garantindo que ela pertence ao usuário. Meta de outro dono
     * responde "não encontrada" de propósito: dizer "sem permissão" revelaria
     * que o id existe.
     */
    private Goal findOwnedGoal(UUID userId, UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada"));

        if (goal.getUser() == null || !goal.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Meta não encontrada");
        }
        return goal;
    }

    private GoalResponseDTO toDTO(Goal goal) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal current = goal.getCurrentAmount() != null
            ? goal.getCurrentAmount() : BigDecimal.ZERO;

        BigDecimal progress = BigDecimal.ZERO;
        if (target != null && target.compareTo(BigDecimal.ZERO) > 0) {
            progress = current
                .divide(target, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        }

        return new GoalResponseDTO(
            goal.getId(),
            goal.getName(),
            target,
            current,
            progress,
            goal.getTargetDate(),
            goal.getStatus(),
            goal.getCreatedAt()
        );
    }
}
