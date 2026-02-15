# Stock Alerts Platform (Kafka + Spring Boot + Flink)

A small end-to-end system for stock price alerts:

1) **Stock Tick Injector (Go)** publishes simulated (or real) stock ticks to **Kafka**  
2) **Rules API (Spring Boot + DynamoDB Local)** lets users create/update/delete alert rules  
3) **Alert Processor (Apache Flink, Java)** consumes ticks + rules and emits alerts when thresholds are crossed

---

## Architecture

- **Kafka**
  - Topic `stock.ticks` — price updates (ticker, price, timestamp)
  - Topic `stock.alerts` — emitted alerts (userId, ruleId, ticker, direction, threshold, price, timestamp)
- **DynamoDB**
  - Table `StockAlertRules` — stores user alert rules
  - GSI `TickerDirectionThresholdIndex` — enables efficient matching by `(ticker_direction, threshold)`
- **Flink**
  - Reads `stock.ticks` from Kafka
  - Looks up and/or maintains rule state (from DynamoDB or rules stream)
  - Emits alerts to Kafka topic `stock.alerts`

---

