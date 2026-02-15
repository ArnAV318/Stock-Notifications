package com.projects.stockalerts.api;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.projects.stockalerts.api.dto.CreateRuleRequest;
import com.projects.stockalerts.api.dto.RuleResponse;
import com.projects.stockalerts.api.dto.UpdateRuleRequest;
import com.projects.stockalerts.service.RuleService;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/rules")
public class RuleController {

    private final RuleService service;

    public RuleController(RuleService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@PathVariable String userId,
                               @Valid @RequestBody CreateRuleRequest req) {
        return service.createRule(userId, req);
    }

    @PutMapping("/{ruleId}")
    public RuleResponse update(@PathVariable String userId,
                               @PathVariable String ruleId,
                               @Valid @RequestBody UpdateRuleRequest req) {
        return service.updateRule(userId, ruleId, req);
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String userId,
                       @PathVariable String ruleId) {
        service.deleteRule(userId, ruleId);
    }

    // Recommended (helps you test quickly)
    @GetMapping
    public List<RuleResponse> list(@PathVariable String userId) {
        return service.listRules(userId);
    }

    @GetMapping("/{ruleId}")
    public RuleResponse get(@PathVariable String userId,
                            @PathVariable String ruleId) {
        return service.getRule(userId, ruleId);
    }
}