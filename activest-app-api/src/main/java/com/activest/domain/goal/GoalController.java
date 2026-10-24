package com.activest.domain.goal;

import com.activest.config.SecurityUtils;
import com.activest.domain.goal.dto.GoalCreateDTO;
import com.activest.domain.goal.dto.GoalResponseDTO;
import com.activest.domain.goal.dto.GoalUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping("/")
    public ResponseEntity<GoalResponseDTO> create(@Valid @RequestBody GoalCreateDTO dto) {
        return ResponseEntity.ok(goalService.createGoal(SecurityUtils.getCurrentUserId(), dto));
    }

    @GetMapping("/")
    public ResponseEntity<List<GoalResponseDTO>> list() {
        return ResponseEntity.ok(goalService.getGoals(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody GoalUpdateDTO dto) {
        return ResponseEntity.ok(goalService.updateGoal(SecurityUtils.getCurrentUserId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        goalService.deleteGoal(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
