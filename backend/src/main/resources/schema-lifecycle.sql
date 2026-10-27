-- Additive upgrade only: no existing accounts, sessions or demo records are changed.
CREATE TABLE IF NOT EXISTS session_billing_snapshot (
  session_id BIGINT PRIMARY KEY,
  price_per_hour DECIMAL(10,2) NOT NULL,
  min_minutes INT NOT NULL,
  billing_unit_minutes INT NOT NULL,
  low_balance_threshold DECIMAL(10,2) NOT NULL,
  auth_method VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
