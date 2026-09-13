package com.primevest.domain.goal;

import com.primevest.domain.goal.dto.GoalCreateDTO;
import com.primevest.domain.goal.dto.GoalResponseDTO;
import com.primevest.domain.goal.dto.GoalUpdateDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GoalService {

    /**
     * Creates a goal.
     */
    @Transactional
    public GoalResponseDTO createGoal(UUID userId, GoalCreateDTO dto) {
        return null;
    }

    /**
     * Gets goals.
     */
    public List<GoalResponseDTO> getGoals(UUID userId) {
        return List.of();
    }

    /**
     * Updates a goal.
     */
    @Transactional
    public GoalResponseDTO updateGoal(UUID userId, UUID goalId, GoalUpdateDTO dto) {
        return null;
    }

    /**
     * Deletes a goal.
     */
    @Transactional
    public void deleteGoal(UUID userId, UUID goalId) {
    }
}
