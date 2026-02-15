package com.projects.stockalerts.persistence;

import java.math.BigDecimal;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class RuleItem {

    private String userId;              // PK
    private String ruleId;              // SK

    private String ticker;              // "TSLA"
    private String direction;           // "ABOVE" | "BELOW"
    private BigDecimal threshold;       // N

    private String tickerDirection;     // GSI HASH: e.g. "TSLA#ABOVE"
    private Boolean enabled;

    private String createdAt;           // ISO-8601 string
    private String updatedAt;

    // ---------- Primary Key ----------
    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSortKey
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    // ---------- GSI ----------
    // IndexName MUST match exactly: "TickerDirectionThresholdIndex"
    @DynamoDbSecondaryPartitionKey(indexNames = "TickerDirectionThresholdIndex")
    @DynamoDbAttribute("ticker_direction")
    public String getTickerDirection() { return tickerDirection; }
    public void setTickerDirection(String tickerDirection) { this.tickerDirection = tickerDirection; }

    @DynamoDbSecondarySortKey(indexNames = "TickerDirectionThresholdIndex")
    @DynamoDbAttribute("threshold")
    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    // ---------- Other attributes ----------
    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}