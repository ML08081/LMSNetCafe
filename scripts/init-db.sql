CREATE DATABASE IF NOT EXISTS lms_netcafe
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE lms_netcafe;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id BIGINT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32),
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_user_username (username),
  UNIQUE KEY uk_sys_user_member (member_id)
);

SET @member_id_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'member_id'
);
SET @add_member_id_sql = IF(
  @member_id_column_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN member_id BIGINT NULL AFTER id',
  'SELECT 1'
);
PREPARE add_member_id_statement FROM @add_member_id_sql;
EXECUTE add_member_id_statement;
DEALLOCATE PREPARE add_member_id_statement;

SET @member_id_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND INDEX_NAME = 'uk_sys_user_member'
);
SET @add_member_id_index_sql = IF(
  @member_id_index_exists = 0,
  'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_member (member_id)',
  'SELECT 1'
);
PREPARE add_member_id_index_statement FROM @add_member_id_index_sql;
EXECUTE add_member_id_index_statement;
DEALLOCATE PREPARE add_member_id_index_statement;

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_role_code (role_code)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_user_role (user_id, role_id),
  KEY idx_sys_user_role_role (role_id)
);

CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permission_code VARCHAR(128) NOT NULL,
  permission_name VARCHAR(128) NOT NULL,
  permission_type VARCHAR(20) NOT NULL DEFAULT 'MENU',
  route_path VARCHAR(255),
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_permission_code (permission_code)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
  KEY idx_sys_role_permission_permission (permission_id)
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

CREATE TABLE IF NOT EXISTS member_account_flow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  flow_no VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  related_id BIGINT,
  related_type VARCHAR(32) NOT NULL,
  change_amount DECIMAL(10,2) NOT NULL,
  balance_before DECIMAL(10,2) NOT NULL,
  balance_after DECIMAL(10,2) NOT NULL,
  operator_id BIGINT,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_member_account_flow_no (flow_no),
  KEY idx_account_flow_member_created (member_id, created_at)
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
  sys_user_id BIGINT NULL,
  member_id BIGINT NULL,
  feature_ref VARCHAR(255) NOT NULL,
  image_ref VARCHAR(255),
  quality_score DECIMAL(5,2),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_face_profile_user (sys_user_id),
  UNIQUE KEY uk_face_profile_member (member_id)
);

SET @face_user_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'face_profile'
    AND COLUMN_NAME = 'sys_user_id'
);
SET @add_face_user_sql = IF(
  @face_user_column_exists = 0,
  'ALTER TABLE face_profile ADD COLUMN sys_user_id BIGINT NULL AFTER id',
  'SELECT 1'
);
PREPARE add_face_user_statement FROM @add_face_user_sql;
EXECUTE add_face_user_statement;
DEALLOCATE PREPARE add_face_user_statement;

ALTER TABLE face_profile MODIFY COLUMN member_id BIGINT NULL;

SET @face_user_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'face_profile'
    AND INDEX_NAME = 'uk_face_profile_user'
);
SET @add_face_user_index_sql = IF(
  @face_user_index_exists = 0,
  'ALTER TABLE face_profile ADD UNIQUE KEY uk_face_profile_user (sys_user_id)',
  'SELECT 1'
);
PREPARE add_face_user_index_statement FROM @add_face_user_index_sql;
EXECUTE add_face_user_index_statement;
DEALLOCATE PREPARE add_face_user_index_statement;

CREATE TABLE IF NOT EXISTS face_verify_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sys_user_id BIGINT,
  member_id BIGINT,
  session_id BIGINT,
  device_id BIGINT,
  similarity DECIMAL(5,4),
  result VARCHAR(20) NOT NULL,
  fail_reason VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SET @face_log_user_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'face_verify_log'
    AND COLUMN_NAME = 'sys_user_id'
);
SET @add_face_log_user_sql = IF(
  @face_log_user_column_exists = 0,
  'ALTER TABLE face_verify_log ADD COLUMN sys_user_id BIGINT NULL AFTER id',
  'SELECT 1'
);
PREPARE add_face_log_user_statement FROM @add_face_log_user_sql;
EXECUTE add_face_log_user_statement;
DEALLOCATE PREPARE add_face_log_user_statement;

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

CREATE TABLE IF NOT EXISTS client_device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  device_code VARCHAR(32) NOT NULL,
  client_token VARCHAR(128),
  app_version VARCHAR(32),
  online_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
  last_heartbeat_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_client_device_code (device_code),
  KEY idx_client_device_status (online_status)
);

CREATE TABLE IF NOT EXISTS member_pet_setting (
  member_id BIGINT PRIMARY KEY,
  enabled TINYINT NOT NULL DEFAULT 1,
  always_on_top TINYINT NOT NULL DEFAULT 1,
  show_bubble TINYINT NOT NULL DEFAULT 1,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS service_call (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  call_no VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  device_id BIGINT,
  call_type VARCHAR(32) NOT NULL,
  message VARCHAR(255),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  handled_by BIGINT,
  handled_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_service_call_no (call_no),
  KEY idx_service_call_status_created (status, created_at),
  KEY idx_service_call_member_created (member_id, created_at)
);

CREATE TABLE IF NOT EXISTS shop_product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_code VARCHAR(32) NOT NULL,
  product_name VARCHAR(64) NOT NULL,
  category VARCHAR(32) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_shop_product_code (product_code),
  KEY idx_shop_product_category_status (category, status)
);

CREATE TABLE IF NOT EXISTS shop_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  device_id BIGINT,
  total_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  remark VARCHAR(255),
  handled_by BIGINT,
  paid_at DATETIME NOT NULL,
  completed_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_shop_order_no (order_no),
  KEY idx_shop_order_status_created (status, created_at),
  KEY idx_shop_order_member_created (member_id, created_at)
);

CREATE TABLE IF NOT EXISTS shop_order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(64) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  subtotal DECIMAL(10,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_shop_order_item_order (order_id)
);

CREATE TABLE IF NOT EXISTS operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT,
  module_name VARCHAR(64) NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  target_id BIGINT,
  request_summary VARCHAR(500),
  result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_operation_log_operator_created (operator_id, created_at),
  KEY idx_operation_log_module_created (module_name, created_at)
);

INSERT INTO sys_role (id, role_code, role_name, status) VALUES
  (1, 'super_admin', '超级管理员', 'ENABLED'),
  (2, 'front_desk', '前台人员', 'ENABLED'),
  (3, 'customer', '普通用户', 'ENABLED')
ON DUPLICATE KEY UPDATE
  role_code = VALUES(role_code),
  role_name = VALUES(role_name),
  status = VALUES(status);

DELETE FROM sys_user_role;
DELETE FROM sys_role_permission;
DELETE FROM sys_role WHERE id NOT IN (1, 2, 3);

INSERT INTO sys_user (id, member_id, username, password_hash, real_name, status, deleted) VALUES
  (1, NULL, 'admin', '{noop}123456', '系统管理员', 'ENABLED', 0),
  (3, NULL, 'cashier', '{noop}123456', '前台人员', 'ENABLED', 0),
  (5, 1, 'member001', '{noop}123456', '张三', 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
  member_id = VALUES(member_id),
  password_hash = VALUES(password_hash),
  real_name = VALUES(real_name),
  status = VALUES(status),
  deleted = VALUES(deleted);

UPDATE sys_user
SET status = 'DISABLED', member_id = NULL, deleted = 1
WHERE username IN ('manager', 'repair');

INSERT INTO sys_user_role (user_id, role_id) VALUES
  (1, 1),
  (3, 2),
  (5, 3);

INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, permission_type, route_path, sort_order) VALUES
  (1, 'dashboard:view', '经营看板', 'MENU', '/', 10),
  (2, 'workbench:view', '前台工作台', 'MENU', '/workbench', 20),
  (3, 'member:manage', '会员管理', 'MENU', '/members', 30),
  (4, 'device:manage', '设备管理', 'MENU', '/devices', 40),
  (5, 'billing:manage', '计费规则', 'MENU', '/billing/rules', 50),
  (6, 'session:view', '上机记录', 'MENU', '/sessions', 60),
  (7, 'face:manage', '人脸认证', 'MENU', '/faces', 70),
  (8, 'statistics:view', '数据统计', 'MENU', '/statistics', 80),
  (9, 'system:user', '系统用户', 'MENU', '/system/users', 90),
  (10, 'maintenance:manage', '维修维护', 'MENU', '/maintenance', 100),
  (11, 'portal:home', '我的首页', 'MENU', '/portal', 110),
  (12, 'portal:account', '我的账户', 'MENU', '/portal/account', 120),
  (13, 'portal:sessions', '上机记录', 'MENU', '/portal/sessions', 130),
  (14, 'portal:devices', '机位与计费', 'MENU', '/portal/devices', 140),
  (15, 'portal:support', '故障反馈', 'MENU', '/portal/support', 150),
  (16, 'device:view', '设备监控', 'API', '/devices', 45),
  (17, 'portal:services', '呼叫与点餐', 'MENU', '/portal/services', 145),
  (18, 'service:manage', '服务与订单', 'MENU', '/service-desk', 75);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id NOT BETWEEN 11 AND 17;

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
  (2, 1), (2, 2), (2, 3), (2, 5), (2, 6), (2, 7), (2, 16), (2, 18),
  (3, 11), (3, 12), (3, 13), (3, 14), (3, 15), (3, 17);

INSERT IGNORE INTO billing_rule (id, rule_name, price_per_hour, min_minutes, billing_unit_minutes, low_balance_threshold) VALUES
  (1, '默认计费规则', 10.00, 15, 15, 10.00),
  (2, 'VIP 包间规则', 18.00, 30, 15, 20.00),
  (3, '包夜规则', 50.00, 360, 360, 10.00);

INSERT IGNORE INTO member_info (id, member_no, name, phone, id_card_no, level, status) VALUES
  (1, 'M0001', '张三', '13800000001', 'MASKED-0001', 'NORMAL', 'ACTIVE'),
  (2, 'M0002', '李四', '13800000002', 'MASKED-0002', 'VIP', 'ACTIVE'),
  (3, 'M0003', '王五', '13800000003', 'MASKED-0003', 'NORMAL', 'FROZEN'),
  (4, 'M0004', '赵六', '13800000004', 'MASKED-0004', 'NORMAL', 'ACTIVE');

INSERT IGNORE INTO member_account (id, member_id, balance, total_recharge, total_consume) VALUES
  (1, 1, 57.30, 100.00, 42.70),
  (2, 2, 6.50, 60.00, 53.50),
  (3, 3, 0.00, 0.00, 0.00),
  (4, 4, 88.00, 120.00, 32.00);

INSERT INTO member_pet_setting (member_id, enabled, always_on_top, show_bubble) VALUES
  (1, 1, 1, 1),
  (2, 1, 1, 1),
  (3, 1, 1, 1),
  (4, 1, 1, 1)
ON DUPLICATE KEY UPDATE member_id = VALUES(member_id);

INSERT INTO shop_product (id, product_code, product_name, category, price, stock, status, sort_order) VALUES
  (1, 'DRINK-COLA', '冰镇可乐', 'DRINK', 5.00, 80, 'ENABLED', 10),
  (2, 'DRINK-TEA', '冰红茶', 'DRINK', 5.00, 60, 'ENABLED', 20),
  (3, 'DRINK-COFFEE', '罐装咖啡', 'DRINK', 8.00, 40, 'ENABLED', 30),
  (4, 'SNACK-CHIPS', '薯片', 'SNACK', 7.00, 50, 'ENABLED', 40),
  (5, 'SNACK-SAUSAGE', '烤肠', 'SNACK', 6.00, 45, 'ENABLED', 50),
  (6, 'SNACK-NUTS', '每日坚果', 'SNACK', 10.00, 35, 'ENABLED', 60),
  (7, 'MEAL-NOODLES', '香辣牛肉面', 'MEAL', 12.00, 30, 'ENABLED', 70),
  (8, 'MEAL-RICE', '黑椒鸡排饭', 'MEAL', 22.00, 25, 'ENABLED', 80)
ON DUPLICATE KEY UPDATE
  product_name = VALUES(product_name), category = VALUES(category), price = VALUES(price),
  status = VALUES(status), sort_order = VALUES(sort_order);

INSERT IGNORE INTO device_info (id, device_code, area, seat_no, ip_address, config_desc, status) VALUES
  (1, 'PC-A01', 'A区', 'A01', '192.168.1.101', 'RTX 4060 / 32G', 'IN_USE'),
  (2, 'PC-A02', 'A区', 'A02', '192.168.1.102', 'RTX 4060 / 32G', 'IDLE'),
  (3, 'PC-A03', 'A区', 'A03', '192.168.1.103', 'RTX 3060 / 16G', 'MAINTENANCE'),
  (4, 'PC-A04', 'A区', 'A04', '192.168.1.104', 'RTX 3060 / 16G', 'FAULT'),
  (5, 'PC-A05', 'A区', 'A05', '192.168.1.105', 'RTX 4060 / 32G', 'IN_USE'),
  (6, 'PC-A06', 'A区', 'A06', '192.168.1.106', 'RTX 4060 / 32G', 'IDLE'),
  (7, 'PC-B01', 'B区', 'B01', '192.168.1.121', 'RTX 4070 / 32G', 'IN_USE'),
  (8, 'PC-B02', 'B区', 'B02', '192.168.1.122', 'RTX 4070 / 32G', 'IDLE'),
  (9, 'PC-B03', 'B区', 'B03', '192.168.1.123', 'RTX 4070 / 32G', 'IN_USE'),
  (10, 'PC-VIP01', 'VIP区', 'V01', '192.168.1.151', 'RTX 4080 / 64G', 'IDLE');

INSERT IGNORE INTO machine_session (id, session_no, member_id, device_id, billing_rule_id, start_at, end_at, duration_minutes, estimated_amount, final_amount, status, operator_id, settled_by) VALUES
  (1, 'S202608310001', 1, 1, 1, '2026-08-31 08:10:00', NULL, NULL, 12.70, NULL, 'RUNNING', 3, NULL),
  (2, 'S202608310002', 2, 5, 1, '2026-08-31 07:42:00', NULL, NULL, 17.40, NULL, 'RUNNING', 3, NULL),
  (3, 'S202608300012', 4, 8, 1, '2026-08-30 19:00:00', '2026-08-30 21:18:00', 138, 23.00, 23.00, 'ENDED', 3, 3);

INSERT IGNORE INTO recharge_record (id, recharge_no, member_id, amount, gift_amount, pay_method, operator_id, remark, created_at) VALUES
  (1, 'R202608310001', 1, 100.00, 0.00, 'CASH', 3, '演示充值', '2026-08-31 08:00:00'),
  (2, 'R202608310002', 2, 60.00, 0.00, 'WECHAT', 3, '演示充值', '2026-08-31 08:05:00'),
  (3, 'R202608300001', 4, 120.00, 0.00, 'ALIPAY', 3, '演示充值', '2026-08-30 18:30:00');

INSERT IGNORE INTO consume_record (id, consume_no, member_id, session_id, consume_type, amount, balance_after, operator_id, created_at) VALUES
  (1, 'C202608300001', 4, 3, 'MACHINE', 23.00, 97.00, 3, '2026-08-30 21:18:00'),
  (2, 'C202608310001', 1, 1, 'MACHINE', 42.70, 57.30, 3, '2026-08-31 09:20:00'),
  (3, 'C202608310002', 2, 2, 'MACHINE', 53.50, 6.50, 3, '2026-08-31 09:21:00');

INSERT IGNORE INTO member_account_flow (id, flow_no, member_id, related_id, related_type, change_amount, balance_before, balance_after, operator_id, remark, created_at) VALUES
  (1, 'F202608310001', 1, 1, 'RECHARGE', 100.00, 0.00, 100.00, 3, '会员充值', '2026-08-31 08:00:00'),
  (2, 'F202608310002', 1, 2, 'CONSUME', -42.70, 100.00, 57.30, 3, '上机消费', '2026-08-31 09:20:00'),
  (3, 'F202608310003', 2, 2, 'RECHARGE', 60.00, 0.00, 60.00, 3, '会员充值', '2026-08-31 08:05:00'),
  (4, 'F202608310004', 2, 3, 'CONSUME', -53.50, 60.00, 6.50, 3, '上机消费', '2026-08-31 09:21:00');

DELETE FROM face_profile
WHERE feature_ref IN ('features/member-1.bin', 'features/member-2.bin');

UPDATE face_profile fp
JOIN sys_user u ON u.member_id = fp.member_id AND u.deleted = 0
SET fp.sys_user_id = u.id
WHERE fp.sys_user_id IS NULL;

INSERT IGNORE INTO device_fault (id, device_id, fault_type, description, status, reported_by, reported_at) VALUES
  (1, 4, 'USER_REPORT', '耳机左声道无声音，请协助检查。', 'OPEN', 5, '2026-08-31 09:30:00');

INSERT IGNORE INTO client_device (id, device_id, device_code, client_token, app_version, online_status, last_heartbeat_at) VALUES
  (1, 1, 'PC-A01', 'dev-token-pc-a01', '0.1.0', 'ONLINE', '2026-08-31 09:20:00'),
  (2, 2, 'PC-A02', 'dev-token-pc-a02', '0.1.0', 'OFFLINE', NULL);

DROP VIEW IF EXISTS v_dashboard_summary;

CREATE VIEW v_dashboard_summary AS
SELECT
  (SELECT COUNT(*) FROM machine_session WHERE status = 'RUNNING') AS running_sessions,
  (SELECT COUNT(*) FROM device_info WHERE status = 'IDLE' AND deleted = 0) AS idle_devices,
  (SELECT COUNT(*) FROM device_info WHERE status = 'FAULT' AND deleted = 0) AS fault_devices,
  (SELECT COALESCE(SUM(amount), 0) FROM recharge_record WHERE DATE(created_at) = CURRENT_DATE) AS today_recharge,
  (SELECT COALESCE(SUM(amount), 0) FROM consume_record WHERE DATE(created_at) = CURRENT_DATE) AS today_consume;
