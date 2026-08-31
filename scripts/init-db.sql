CREATE DATABASE IF NOT EXISTS lms_netcafe
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE lms_netcafe;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32),
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_user_username (username)
);

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_role_code (role_code)
);

CREATE TABLE IF NOT EXISTS member_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_no VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  phone VARCHAR(32) NOT NULL,
  id_card_no VARCHAR(64),
  level VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_member_no (member_no),
  KEY idx_member_phone (phone)
);

CREATE TABLE IF NOT EXISTS member_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  balance DECIMAL(10,2) NOT NULL DEFAULT 0,
  total_recharge DECIMAL(10,2) NOT NULL DEFAULT 0,
  total_consume DECIMAL(10,2) NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_member_account_member (member_id)
);

CREATE TABLE IF NOT EXISTS recharge_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  recharge_no VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  gift_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  pay_method VARCHAR(20) NOT NULL,
  operator_id BIGINT,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_recharge_no (recharge_no),
  KEY idx_recharge_member_created (member_id, created_at)
);

CREATE TABLE IF NOT EXISTS device_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_code VARCHAR(32) NOT NULL,
  area VARCHAR(64),
  seat_no VARCHAR(32) NOT NULL,
  ip_address VARCHAR(64),
  config_desc VARCHAR(255),
  status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_device_code (device_code)
);

CREATE TABLE IF NOT EXISTS billing_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_name VARCHAR(64) NOT NULL,
  price_per_hour DECIMAL(10,2) NOT NULL,
  min_minutes INT NOT NULL DEFAULT 0,
  billing_unit_minutes INT NOT NULL DEFAULT 15,
  low_balance_threshold DECIMAL(10,2) NOT NULL DEFAULT 10,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS machine_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_no VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  billing_rule_id BIGINT NOT NULL,
  start_at DATETIME NOT NULL,
  end_at DATETIME NULL,
  duration_minutes INT,
  estimated_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  final_amount DECIMAL(10,2),
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  operator_id BIGINT,
  settled_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_machine_session_no (session_no),
  KEY idx_session_member_status (member_id, status),
  KEY idx_session_device_status (device_id, status)
);

CREATE TABLE IF NOT EXISTS consume_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  consume_no VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  session_id BIGINT,
  consume_type VARCHAR(20) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  balance_after DECIMAL(10,2) NOT NULL,
  operator_id BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_consume_no (consume_no),
  KEY idx_consume_member_created (member_id, created_at)
);

CREATE TABLE IF NOT EXISTS face_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  feature_ref VARCHAR(255) NOT NULL,
  image_ref VARCHAR(255),
  quality_score DECIMAL(5,2),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_face_profile_member (member_id)
);

CREATE TABLE IF NOT EXISTS face_verify_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id BIGINT,
  session_id BIGINT,
  device_id BIGINT,
  similarity DECIMAL(5,4),
  result VARCHAR(20) NOT NULL,
  fail_reason VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS device_fault (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  fault_type VARCHAR(64) NOT NULL,
  description VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  reported_by BIGINT,
  reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS repair_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  fault_id BIGINT NOT NULL,
  repair_user_id BIGINT,
  result_desc VARCHAR(500),
  repaired_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO sys_role (id, role_code, role_name) VALUES
  (1, 'admin', '超级管理员'),
  (2, 'manager', '店长'),
  (3, 'cashier', '前台收银'),
  (4, 'repair', '维修人员');

INSERT IGNORE INTO sys_user (id, username, password_hash, real_name, status) VALUES
  (1, 'admin', '{noop}123456', '系统管理员', 'ENABLED');

INSERT IGNORE INTO billing_rule (id, rule_name, price_per_hour, min_minutes, billing_unit_minutes, low_balance_threshold) VALUES
  (1, '默认计费规则', 10.00, 15, 15, 10.00);
