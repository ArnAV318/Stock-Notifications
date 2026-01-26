package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/gorilla/websocket"
	"github.com/joho/godotenv"
	"github.com/segmentio/kafka-go"
)

type AlpacaAuth struct {
	Action string `json:"action"`
	Key    string `json:"key"`
	Secret string `json:"secret"`
}

type AlpacaSubscribe struct {
	Action string   `json:"action"`
	Trades []string `json:"trades,omitempty"`
	Quotes []string `json:"quotes,omitempty"`
	Bars   []string `json:"bars,omitempty"`
}

type Envelope struct {
	Source     string          `json:"source"`
	Feed       string          `json:"feed"`
	IngestTsMs int64           `json:"ingest_ts_ms"`
	Event      json.RawMessage `json:"event"`
}

func getenv(key, def string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return def
}

func parseSymbols(feed string) []string {
	raw := strings.TrimSpace(os.Getenv("SYMBOLS"))
	if raw == "" {
		if feed == "test" {
			return []string{"FAKEPACA"}
		}
		return []string{"AAPL"}
	}
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		s := strings.ToUpper(strings.TrimSpace(p))
		if s != "" {
			out = append(out, s)
		}
	}
	if len(out) == 0 {
		if feed == "test" {
			return []string{"FAKEPACA"}
		}
		return []string{"AAPL"}
	}
	return out
}

type fastSymbol struct {
	S      string `json:"S"`
	Symbol string `json:"symbol"`
}

func eventKey(event json.RawMessage) string {
	var s fastSymbol
	// Only unmarshal what we need
	if err := json.Unmarshal(event, &s); err != nil {
		return "unknown"
	}
	if s.S != "" {
		return s.S
	}
	if s.Symbol != "" {
		return s.Symbol
	}
	return "unknown"
}

type MessageWriter interface {
	WriteMessages(ctx context.Context, msgs ...kafka.Message) error
}

type mockWriter struct{}

func (m *mockWriter) WriteMessages(ctx context.Context, msgs ...kafka.Message) error {
	log.Printf("[MOCK] Would write %d messages to Kafka", len(msgs))
	return nil
}

func main() {
	_ = godotenv.Load() // loads .env file into os.Environ

	keyID := os.Getenv("ALPACA_KEY_ID")
	secret := os.Getenv("ALPACA_SECRET_KEY")

	if keyID == "" || secret == "" {
		log.Fatal("Missing ALPACA_KEY_ID or ALPACA_SECRET_KEY")
	}

	feed := strings.ToLower(getenv("FEED", "test"))
	wsURL := fmt.Sprintf("wss://stream.data.alpaca.markets/v2/%s", feed)
	symbols := parseSymbols(feed)

	kafkaBrokers := strings.Split(getenv("KAFKA_BROKERS", "localhost:9092"), ",")
	kafkaTopic := getenv("KAFKA_TOPIC", "alpaca.marketdata")

	writer := &kafka.Writer{
		Addr:         kafka.TCP(kafkaBrokers...),
		Topic:        kafkaTopic,
		Balancer:     &kafka.Hash{},
		RequiredAcks: kafka.RequireAll,
		Async:        false,
		BatchTimeout: 50 * time.Millisecond,
	}
	defer writer.Close()

	log.Printf("Alpaca WS: %s | symbols=%v | Kafka: %v topic=%s", wsURL, symbols, kafkaBrokers, kafkaTopic)
	// writer := &mockWriter{}
	backoff := 1 * time.Second
	for {
		err := runOnce(wsURL, keyID, secret, feed, symbols, writer)
		log.Printf("stream ended: %v", err)

		time.Sleep(backoff)
		if backoff < 30*time.Second {
			backoff *= 2
			if backoff > 30*time.Second {
				backoff = 30 * time.Second
			}
		}
	}
}

func runOnce(wsURL, keyID, secret, feed string, symbols []string, writer MessageWriter) error {
	dialer := websocket.Dialer{
		Proxy:            http.ProxyFromEnvironment,
		HandshakeTimeout: 10 * time.Second,
	}

	conn, resp, err := dialer.Dial(wsURL, nil)
	if err != nil {
		if resp != nil {
			return fmt.Errorf("ws dial failed: %w (http=%s)", err, resp.Status)
		}
		return fmt.Errorf("ws dial failed: %w", err)
	}
	defer conn.Close()

	// Keep-alives / timeouts
	_ = conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	conn.SetPongHandler(func(string) error {
		_ = conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		return nil
	})

	// Read initial message (often "connected")
	_, first, err := conn.ReadMessage()
	if err != nil {
		return fmt.Errorf("read welcome failed: %w", err)
	}
	log.Printf("< %s", string(first))

	// AUTH ASAP (Alpaca closes with "auth timeout" if you don't auth quickly)
	auth := AlpacaAuth{
		Action: "auth",
		Key:    keyID,
		Secret: secret,
	}
	if err := conn.WriteJSON(auth); err != nil {
		return fmt.Errorf("auth send failed: %w", err)
	}

	_, authResp, err := conn.ReadMessage()
	if err != nil {
		return fmt.Errorf("auth resp read failed: %w", err)
	}
	log.Printf("< %s", string(authResp))

	if strings.Contains(string(authResp), `"T":"error"`) {
		return errors.New("alpaca auth error: " + string(authResp))
	}

	// SUBSCRIBE
	sub := AlpacaSubscribe{
		Action: "subscribe",
		Trades: symbols,
		Quotes: symbols,
	}
	if err := conn.WriteJSON(sub); err != nil {
		return fmt.Errorf("subscribe send failed: %w", err)
	}
	log.Printf("> subscribed %v", symbols)

	// Ping loop
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		t := time.NewTicker(20 * time.Second)
		defer t.Stop()
		for {
			select {
			case <-t.C:
				_ = conn.WriteControl(websocket.PingMessage, []byte("ping"), time.Now().Add(5*time.Second))
			case <-ctx.Done():
				return
			}
		}
	}()

	// Read loop: Alpaca usually sends JSON arrays of events
	for {
		_, msg, err := conn.ReadMessage()
		if err != nil {
			return err
		}

		// msg can be []events or single object
		events := splitEvents(msg)

		nowMs := time.Now().UnixMilli()
		for _, ev := range events {
			env := Envelope{
				Source:     "alpaca",
				Feed:       feed,
				IngestTsMs: nowMs,
				Event:      ev,
			}
			b, _ := json.Marshal(env)
			key := eventKey(ev)

			err = writer.WriteMessages(context.Background(), kafka.Message{
				Key:   []byte(key),
				Value: b,
				Time:  time.Now(),
			})
			if err != nil {
				return fmt.Errorf("kafka write failed: %w", err)
			}
		}
	}
}

func splitEvents(raw []byte) []json.RawMessage {
	raw = bytesTrim(raw)
	if len(raw) == 0 {
		return nil
	}
	if raw[0] == '[' {
		var arr []json.RawMessage
		if err := json.Unmarshal(raw, &arr); err == nil {
			return arr
		}
	}
	// single object fallback
	return []json.RawMessage{json.RawMessage(raw)}
}

func bytesTrim(b []byte) []byte {
	// minimal trim (spaces/newlines)
	i, j := 0, len(b)-1
	for i <= j && (b[i] == ' ' || b[i] == '\n' || b[i] == '\r' || b[i] == '\t') {
		i++
	}
	for j >= i && (b[j] == ' ' || b[j] == '\n' || b[j] == '\r' || b[j] == '\t') {
		j--
	}
	if i > j {
		return nil
	}
	return b[i : j+1]
}
