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

CREATE TABLE IF NOT EXISTS ai_daily_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_date DATE NOT NULL UNIQUE,
  metrics_json LONGTEXT NOT NULL,
  analysis TEXT NOT NULL,
  generation_mode VARCHAR(20) NOT NULL,
  model_name VARCHAR(200) NOT NULL,
  notice VARCHAR(500) NOT NULL,
  generated_by BIGINT NOT NULL,
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
