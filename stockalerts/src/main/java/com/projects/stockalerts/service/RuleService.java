package com.projects.stockalerts.service;

import org.springframework.stereotype.Service;

import com.projects.stockalerts.api.dto.CreateRuleRequest;
import com.projects.stockalerts.api.dto.RuleResponse;
import com.projects.stockalerts.api.dto.UpdateRuleRequest;
import com.projects.stockalerts.api.errors.NotFoundException;
import com.projects.stockalerts.domain.Direction;
import com.projects.stockalerts.persistence.RuleItem;
import com.projects.stockalerts.persistence.RuleRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RuleService {

    private final RuleRepository repo;

    public RuleService(RuleRepository repo) {
        this.repo = repo;
    }

    public RuleResponse createRule(String userId, CreateRuleRequest req) {
        String ruleId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        String ticker = normalizeTicker(req.ticker());
        boolean enabled = req.enabled() == null ? true : req.enabled();

        RuleItem item = new RuleItem();
        item.setUserId(userId);
        item.setRuleId(ruleId);

        item.setUserId(pk(userId));
        item.setRuleId(sk(ruleId));

        item.setTicker(ticker);
        item.setDirection(req.direction().name());
        item.setThreshold(req.threshold());
        item.setEnabled(enabled);

        // Optional GSI attributes
        item.setTickerDirection(ticker + "#" + req.direction().name());

        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        repo.put(item);
        return toResponse(item);
    }

    public RuleResponse updateRule(String userId, String ruleId, UpdateRuleRequest req) {
        RuleItem item = repo.get(pk(userId), sk(ruleId))
                .orElseThrow(() -> new NotFoundException("Rule not found"));

        boolean tickerChanged = false;

        if (req.ticker() != null) {
            item.setTicker(normalizeTicker(req.ticker()));
            tickerChanged = true;
        }
        if (req.direction() != null) item.setDirection(req.direction().name());
        if (req.threshold() != null) item.setThreshold(req.threshold());
        if (req.enabled() != null) item.setEnabled(req.enabled());

        // If ticker changed, update GSI keys too
        if (tickerChanged) {
            String ticker = item.getTicker();
            item.setTickerDirection(ticker + "#" + req.direction().name());

        }

        item.setUpdatedAt(Instant.now().toString());
        repo.put(item); // put overwrites item with same pk/sk

        return toResponse(item);
    }

    public void deleteRule(String userId, String ruleId) {
        // optional: verify exists for nicer error
        if (repo.get(pk(userId), sk(ruleId)).isEmpty()) {
            throw new NotFoundException("Rule not found");
        }
        repo.delete(pk(userId), sk(ruleId));
    }

    public List<RuleResponse> listRules(String userId) {
        return repo.listByUserPk(pk(userId)).stream().map(this::toResponse).toList();
    }

    public RuleResponse getRule(String userId, String ruleId) {
        RuleItem item = repo.get(pk(userId), sk(ruleId))
                .orElseThrow(() -> new NotFoundException("Rule not found"));
        return toResponse(item);
    }

    private RuleResponse toResponse(RuleItem i) {
        return new RuleResponse(
                i.getUserId(),
                i.getRuleId(),
                i.getTicker(),
                Direction.valueOf(i.getDirection()),
                i.getThreshold(),
                Boolean.TRUE.equals(i.getEnabled()),
                i.getCreatedAt(),
                i.getUpdatedAt()
        );
    }

    private static String pk(String userId) { return "USER#" + userId; }
    private static String sk(String ruleId) { return "RULE#" + ruleId; }

    private static String normalizeTicker(String ticker) {
        return ticker.trim().toUpperCase();
    }
}