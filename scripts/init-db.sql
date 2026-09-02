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

CREATE TABLE IF NOT EXISTS member_operation_profile (
  member_id BIGINT PRIMARY KEY,
  favorite_games VARCHAR(255),
  preferred_time_slot VARCHAR(64),
  beverage_preference VARCHAR(128),
  spending_power VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  churn_risk VARCHAR(32) NOT NULL DEFAULT 'LOW',
  segment VARCHAR(64) NOT NULL DEFAULT '休闲追剧用户',
  last_visit_at DATETIME NULL,
  last_order_at DATETIME NULL,
  recommendation VARCHAR(255),
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_member_profile_churn (churn_risk),
  KEY idx_member_profile_segment (segment)
);

CREATE TABLE IF NOT EXISTS member_coupon (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  coupon_no VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  coupon_type VARCHAR(32) NOT NULL,
  title VARCHAR(128) NOT NULL,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_rate DECIMAL(5,2),
  min_spend DECIMAL(10,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
  source_reason VARCHAR(255),
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_member_coupon_no (coupon_no),
  KEY idx_member_coupon_member_status (member_id, status)
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
  area_type VARCHAR(32) NOT NULL DEFAULT 'LOBBY',
  room_capacity INT NOT NULL DEFAULT 1,
  hourly_rate_hint DECIMAL(10,2),
  seat_no VARCHAR(32) NOT NULL,
  ip_address VARCHAR(64),
  config_desc VARCHAR(255),
  status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_device_code (device_code)
);

SET @device_area_type_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'device_info'
    AND COLUMN_NAME = 'area_type'
);
SET @add_device_area_type_sql = IF(
  @device_area_type_column_exists = 0,
  'ALTER TABLE device_info ADD COLUMN area_type VARCHAR(32) NOT NULL DEFAULT ''LOBBY'' AFTER area',
  'SELECT 1'
);
PREPARE add_device_area_type_statement FROM @add_device_area_type_sql;
EXECUTE add_device_area_type_statement;
DEALLOCATE PREPARE add_device_area_type_statement;

SET @device_capacity_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'device_info'
    AND COLUMN_NAME = 'room_capacity'
);
SET @add_device_capacity_sql = IF(
  @device_capacity_column_exists = 0,
  'ALTER TABLE device_info ADD COLUMN room_capacity INT NOT NULL DEFAULT 1 AFTER area_type',
  'SELECT 1'
);
PREPARE add_device_capacity_statement FROM @add_device_capacity_sql;
EXECUTE add_device_capacity_statement;
DEALLOCATE PREPARE add_device_capacity_statement;

SET @device_rate_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'device_info'
    AND COLUMN_NAME = 'hourly_rate_hint'
);
SET @add_device_rate_sql = IF(
  @device_rate_column_exists = 0,
  'ALTER TABLE device_info ADD COLUMN hourly_rate_hint DECIMAL(10,2) NULL AFTER room_capacity',
  'SELECT 1'
);
PREPARE add_device_rate_statement FROM @add_device_rate_sql;
EXECUTE add_device_rate_statement;
DEALLOCATE PREPARE add_device_rate_statement;

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
  product_type VARCHAR(32) NOT NULL DEFAULT 'MERCHANDISE',
  pet_species VARCHAR(32),
  pet_breed VARCHAR(64),
  expert_role VARCHAR(64),
  service_duration_minutes INT,
  description VARCHAR(255),
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_shop_product_code (product_code),
  KEY idx_shop_product_category_status (category, status)
);

SET @shop_product_type_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'shop_product'
    AND COLUMN_NAME = 'product_type'
);
SET @add_shop_product_type_sql = IF(
  @shop_product_type_column_exists = 0,
  'ALTER TABLE shop_product ADD COLUMN product_type VARCHAR(32) NOT NULL DEFAULT ''MERCHANDISE'' AFTER category',
  'SELECT 1'
);
PREPARE add_shop_product_type_statement FROM @add_shop_product_type_sql;
EXECUTE add_shop_product_type_statement;
DEALLOCATE PREPARE add_shop_product_type_statement;

SET @shop_pet_species_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'shop_product'
    AND COLUMN_NAME = 'pet_species'
);
SET @add_shop_pet_species_sql = IF(
  @shop_pet_species_column_exists = 0,
  'ALTER TABLE shop_product ADD COLUMN pet_species VARCHAR(32) NULL AFTER product_type',
  'SELECT 1'
);
PREPARE add_shop_pet_species_statement FROM @add_shop_pet_species_sql;
EXECUTE add_shop_pet_species_statement;
DEALLOCATE PREPARE add_shop_pet_species_statement;

SET @shop_pet_breed_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'shop_product'
    AND COLUMN_NAME = 'pet_breed'
);
SET @add_shop_pet_breed_sql = IF(
  @shop_pet_breed_column_exists = 0,
  'ALTER TABLE shop_product ADD COLUMN pet_breed VARCHAR(64) NULL AFTER pet_species',
  'SELECT 1'
);
PREPARE add_shop_pet_breed_statement FROM @add_shop_pet_breed_sql;
EXECUTE add_shop_pet_breed_statement;
DEALLOCATE PREPARE add_shop_pet_breed_statement;

SET @shop_expert_role_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'shop_product'
    AND COLUMN_NAME = 'expert_role'
);
SET @add_shop_expert_role_sql = IF(
  @shop_expert_role_column_exists = 0,
  'ALTER TABLE shop_product ADD COLUMN expert_role VARCHAR(64) NULL AFTER pet_breed',
  'SELECT 1'
);
PREPARE add_shop_expert_role_statement FROM @add_shop_expert_role_sql;
EXECUTE add_shop_expert_role_statement;
DEALLOCATE PREPARE add_shop_expert_role_statement;

SET @shop_duration_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'shop_product'
    AND COLUMN_NAME = 'service_duration_minutes'
);
SET @add_shop_duration_sql = IF(
  @shop_duration_column_exists = 0,
  'ALTER TABLE shop_product ADD COLUMN service_duration_minutes INT NULL AFTER expert_role',
  'SELECT 1'
);
PREPARE add_shop_duration_statement FROM @add_shop_duration_sql;
EXECUTE add_shop_duration_statement;
DEALLOCATE PREPARE add_shop_duration_statement;

SET @shop_description_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'shop_product'
    AND COLUMN_NAME = 'description'
);
SET @add_shop_description_sql = IF(
  @shop_description_column_exists = 0,
  'ALTER TABLE shop_product ADD COLUMN description VARCHAR(255) NULL AFTER service_duration_minutes',
  'SELECT 1'
);
PREPARE add_shop_description_statement FROM @add_shop_description_sql;
EXECUTE add_shop_description_statement;
DEALLOCATE PREPARE add_shop_description_statement;

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
  (5, 1, 'member001', '{noop}123456', '张三', 'ENABLED', 0),
  (6, 2, 'member002', '{noop}123456', '李四', 'ENABLED', 0),
  (7, 4, 'member004', '{noop}123456', '赵六', 'ENABLED', 0),
  (8, 5, 'member005', '{noop}123456', '陈晨', 'ENABLED', 0),
  (9, 6, 'member006', '{noop}123456', '周周', 'ENABLED', 0),
  (10, 7, 'member007', '{noop}123456', '林悦', 'ENABLED', 0),
  (11, 8, 'member008', '{noop}123456', '唐宇', 'ENABLED', 0),
  (12, 9, 'member009', '{noop}123456', '何苗', 'ENABLED', 0),
  (13, 10, 'member010', '{noop}123456', '高远', 'ENABLED', 0)
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
  (5, 3),
  (6, 3),
  (7, 3),
  (8, 3),
  (9, 3),
  (10, 3),
  (11, 3),
  (12, 3),
  (13, 3);

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
  (18, 'service:manage', '服务与订单', 'MENU', '/service-desk', 75),
  (19, 'product:manage', '商品管理', 'MENU', '/products', 65);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id NOT BETWEEN 11 AND 17;

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
  (2, 1), (2, 2), (2, 3), (2, 5), (2, 6), (2, 7), (2, 16), (2, 18),
  (3, 11), (3, 12), (3, 13), (3, 14), (3, 15), (3, 17);

INSERT IGNORE INTO billing_rule (id, rule_name, price_per_hour, min_minutes, billing_unit_minutes, low_balance_threshold) VALUES
  (1, '默认计费规则', 10.00, 15, 15, 10.00),
  (2, 'VIP 包间规则', 18.00, 30, 15, 20.00),
  (3, '包夜规则', 50.00, 360, 360, 10.00),
  (4, '单人豪华包房', 22.00, 30, 15, 20.00),
  (5, '双人包房', 36.00, 30, 15, 30.00),
  (6, '四人包房', 62.00, 60, 30, 50.00),
  (7, '五人包房', 78.00, 60, 30, 60.00);

INSERT INTO member_info (id, member_no, name, phone, id_card_no, level, status) VALUES
  (1, 'M0001', '张三', '13800000001', 'MASKED-0001', 'NORMAL', 'ACTIVE'),
  (2, 'M0002', '李四', '13800000002', 'MASKED-0002', 'VIP', 'ACTIVE'),
  (3, 'M0003', '王五', '13800000003', 'MASKED-0003', 'NORMAL', 'FROZEN'),
  (4, 'M0004', '赵六', '13800000004', 'MASKED-0004', 'VIP', 'ACTIVE'),
  (5, 'M0005', '陈晨', '13800000005', 'MASKED-0005', 'NORMAL', 'ACTIVE'),
  (6, 'M0006', '周周', '13800000006', 'MASKED-0006', 'VIP', 'ACTIVE'),
  (7, 'M0007', '林悦', '13800000007', 'MASKED-0007', 'NORMAL', 'ACTIVE'),
  (8, 'M0008', '唐宇', '13800000008', 'MASKED-0008', 'VIP', 'ACTIVE'),
  (9, 'M0009', '何苗', '13800000009', 'MASKED-0009', 'NORMAL', 'ACTIVE'),
  (10, 'M0010', '高远', '13800000010', 'MASKED-0010', 'VIP', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  phone = VALUES(phone),
  id_card_no = VALUES(id_card_no),
  level = VALUES(level),
  status = VALUES(status),
  deleted = 0;

INSERT INTO member_account (id, member_id, balance, total_recharge, total_consume) VALUES
  (1, 1, 86.30, 180.00, 93.70),
  (2, 2, 28.50, 260.00, 231.50),
  (3, 3, 0.00, 0.00, 0.00),
  (4, 4, 188.00, 360.00, 172.00),
  (5, 5, 118.00, 220.00, 102.00),
  (6, 6, 299.00, 600.00, 301.00),
  (7, 7, 16.00, 80.00, 64.00),
  (8, 8, 76.00, 300.00, 224.00),
  (9, 9, 45.00, 120.00, 75.00),
  (10, 10, 508.00, 1000.00, 492.00)
ON DUPLICATE KEY UPDATE
  balance = VALUES(balance),
  total_recharge = VALUES(total_recharge),
  total_consume = VALUES(total_consume),
  version = version + 1;

INSERT INTO member_operation_profile
  (member_id, favorite_games, preferred_time_slot, beverage_preference, spending_power,
   churn_risk, segment, last_visit_at, last_order_at, recommendation)
VALUES
  (1, '英雄联盟、无畏契约、APEX', '晚间 19:00-23:00', '冰镇可乐、罐装咖啡',
   'MEDIUM', 'LOW', '游戏发烧友', '2026-09-02 12:20:00', '2026-09-02 12:35:00',
   '推荐竞技区连坐套餐和饮品加购券'),
  (2, '原神、影视追剧、Steam 独立游戏', '下午 14:00-18:00', '冰红茶',
   'LOW', 'MEDIUM', '休闲追剧用户', '2026-08-21 16:30:00', NULL,
   '推荐下午场时长券和轻食折扣'),
  (3, '穿越火线、地下城与勇士', '深夜 23:00-06:00', '烤肠、咖啡',
   'LOW', 'HIGH', '深夜包夜用户', '2026-08-10 02:15:00', NULL,
   '超过 14 天未到店，建议推送包夜代金券'),
  (4, '永劫无间、黑神话、主机游戏', '周末 18:00-24:00', '黑椒鸡排饭、每日坚果',
   'HIGH', 'LOW', '高价值包房用户', '2026-09-01 20:00:00', '2026-09-01 20:40:00',
   '推荐四人包房套餐、布偶猫宠物陪伴和真人高手陪玩'),
  (5, '云顶之弈、Steam 合作游戏、派对游戏', '晚间 18:00-22:00', '柠檬茶、薯片',
   'MEDIUM', 'LOW', '轻社交开黑用户', '2026-09-02 19:10:00', '2026-09-02 19:25:00',
   '推荐双人包房和轻食组合券'),
  (6, '瓦罗兰特、CS2、Apex Legends', '深夜 22:00-03:00', '能量饮料、罐装咖啡',
   'HIGH', 'LOW', '竞技上分用户', '2026-09-02 21:00:00', '2026-09-02 21:05:00',
   '推荐五人包房、FPS 高手陪玩和宠物陪伴组合套餐'),
  (7, '影视追剧、模拟经营、休闲小游戏', '下午 13:00-17:00', '矿泉水、巧克力',
   'LOW', 'HIGH', '低频休闲用户', '2026-08-12 15:20:00', NULL,
   '超过 14 天未到店，建议推送下午场上机券'),
  (8, '英雄联盟、永劫无间、派对游戏', '周末 20:00-02:00', '烤肠、能量饮料',
   'MEDIUM', 'MEDIUM', '周末组队用户', '2026-08-25 22:10:00', '2026-08-25 23:05:00',
   '推荐周末四人包房券和犬类宠物陪伴'),
  (9, '地下城与勇士、魔兽世界、刷本搬砖', '上午 09:00-13:00', '冰红茶、香辣牛肉面',
   'MEDIUM', 'MEDIUM', '长时段刷本用户', '2026-08-24 10:30:00', '2026-08-24 11:00:00',
   '推荐上午连充时长包和简餐折扣'),
  (10, '无畏契约、CS2、战队训练赛', '晚间 19:00-01:00', '黑椒鸡排饭、罐装咖啡',
   'HIGH', 'LOW', '高价值战队用户', '2026-09-02 20:30:00', '2026-09-02 20:45:00',
   '推荐五人战队房、包夜套餐和真人高手陪玩组合')
ON DUPLICATE KEY UPDATE
  favorite_games = VALUES(favorite_games),
  preferred_time_slot = VALUES(preferred_time_slot),
  beverage_preference = VALUES(beverage_preference),
  spending_power = VALUES(spending_power),
  churn_risk = VALUES(churn_risk),
  segment = VALUES(segment),
  last_visit_at = VALUES(last_visit_at),
  last_order_at = VALUES(last_order_at),
  recommendation = VALUES(recommendation);

INSERT INTO member_coupon
  (id, coupon_no, member_id, coupon_type, title, discount_amount, discount_rate, min_spend,
   status, source_reason, expires_at)
VALUES
  (1, 'CP202609020001', 3, 'MACHINE_VOUCHER', '14 天未到店上机代金券', 15.00, NULL, 30.00,
   'UNUSED', '流失风险高，自动生成召回券', '2026-10-02 23:59:59'),
  (2, 'CP202609020002', 2, 'DRINK_DISCOUNT', '下午场饮品折扣券', 3.00, NULL, 10.00,
   'UNUSED', '休闲追剧用户偏好饮品', '2026-09-30 23:59:59'),
  (3, 'CP202609020003', 4, 'ROOM_PACKAGE', '包房用户加时券', 20.00, NULL, 80.00,
   'UNUSED', '高价值包房用户运营', '2026-10-02 23:59:59'),
  (4, 'CP202609020004', 1, 'PET_COMPANION', '狸花猫宠物陪伴新人券', 8.00, NULL, 25.00,
   'UNUSED', '游戏发烧友偏好轻互动陪伴', '2026-09-25 23:59:59'),
  (5, 'CP202609020005', 1, 'DRINK_DISCOUNT', '竞技饮品加购券', 4.00, NULL, 12.00,
   'UNUSED', '晚间高频上机用户饮品偏好', '2026-09-20 23:59:59'),
  (6, 'CP202609020006', 6, 'ROOM_PACKAGE', '五人战队房满减券', 30.00, NULL, 150.00,
   'UNUSED', '高价值战队用户运营', '2026-10-08 23:59:59'),
  (7, 'CP202609020007', 7, 'MACHINE_VOUCHER', '下午场召回券', 12.00, NULL, 25.00,
   'UNUSED', '低频休闲用户流失预警', '2026-09-28 23:59:59'),
  (8, 'CP202609020008', 8, 'PET_COMPANION', '犬类宠物陪伴体验券', 10.00, NULL, 30.00,
   'UNUSED', '周末组队用户推荐', '2026-10-05 23:59:59'),
  (9, 'CP202609020009', 9, 'MEAL_DISCOUNT', '上午简餐补给券', 6.00, NULL, 20.00,
   'UNUSED', '长时段刷本用户餐食偏好', '2026-09-26 23:59:59'),
  (10, 'CP202609020010', 10, 'ROOM_PACKAGE', '战队包夜加时券', 50.00, NULL, 260.00,
   'UNUSED', '高价值战队用户复购激励', '2026-10-15 23:59:59')
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  discount_amount = VALUES(discount_amount),
  discount_rate = VALUES(discount_rate),
  min_spend = VALUES(min_spend),
  status = VALUES(status),
  source_reason = VALUES(source_reason),
  expires_at = VALUES(expires_at);

INSERT INTO member_pet_setting (member_id, enabled, always_on_top, show_bubble) VALUES
  (1, 1, 1, 1),
  (2, 1, 1, 1),
  (3, 1, 1, 1),
  (4, 1, 1, 1),
  (5, 1, 1, 1),
  (6, 1, 1, 1),
  (7, 1, 1, 1),
  (8, 1, 1, 1),
  (9, 1, 1, 1),
  (10, 1, 1, 1)
ON DUPLICATE KEY UPDATE member_id = VALUES(member_id);

INSERT INTO shop_product
  (id, product_code, product_name, category, product_type, pet_species, pet_breed,
   expert_role, service_duration_minutes, description, price, stock, status, sort_order)
VALUES
  (1, 'DRINK-COLA', '冰镇可乐', 'DRINK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '冰柜常备饮品，可配送到机位。', 5.00, 80, 'ENABLED', 10),
  (2, 'DRINK-TEA', '冰红茶', 'DRINK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '瓶装茶饮，适合长时段上机。', 5.00, 60, 'ENABLED', 20),
  (3, 'DRINK-COFFEE', '罐装咖啡', 'DRINK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '提神咖啡，支持常温或冰镇。', 8.00, 40, 'ENABLED', 30),
  (4, 'DRINK-ENERGY', '能量饮料', 'DRINK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '深夜包夜和竞技用户常购。', 9.00, 55, 'ENABLED', 40),
  (5, 'DRINK-LEMON', '冻柠茶', 'DRINK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '冰爽茶饮，适合搭配零食。', 7.00, 45, 'ENABLED', 50),
  (6, 'DRINK-WATER', '矿泉水', 'DRINK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '基础补水饮品。', 3.00, 100, 'ENABLED', 60),
  (7, 'SNACK-CHIPS', '薯片', 'SNACK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '袋装零食。', 7.00, 50, 'ENABLED', 70),
  (8, 'SNACK-SAUSAGE', '烤肠', 'SNACK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '热食零食，前台加热后配送。', 6.00, 45, 'ENABLED', 80),
  (9, 'SNACK-NUTS', '每日坚果', 'SNACK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '小包装坚果。', 10.00, 35, 'ENABLED', 90),
  (10, 'SNACK-LATIAO', '香辣条', 'SNACK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '休闲辣味零食。', 4.00, 70, 'ENABLED', 100),
  (11, 'SNACK-CHOCO', '巧克力棒', 'SNACK', 'MERCHANDISE', NULL, NULL, NULL, NULL, '补充能量小食。', 6.00, 42, 'ENABLED', 110),
  (12, 'MEAL-NOODLES', '香辣牛肉面', 'MEAL', 'MERCHANDISE', NULL, NULL, NULL, NULL, '热食简餐，适合长时段上机。', 12.00, 30, 'ENABLED', 120),
  (13, 'MEAL-RICE', '黑椒鸡排饭', 'MEAL', 'MERCHANDISE', NULL, NULL, NULL, NULL, '主食套餐。', 22.00, 25, 'ENABLED', 130),
  (14, 'MEAL-CHICKEN', '鸡排能量套餐', 'MEAL', 'MERCHANDISE', NULL, NULL, NULL, NULL, '鸡排与饮品组合。', 28.00, 18, 'ENABLED', 140),
  (15, 'MEAL-SANDWICH', '火腿芝士三明治', 'MEAL', 'MERCHANDISE', NULL, NULL, NULL, NULL, '轻食简餐。', 15.00, 24, 'ENABLED', 150),
  (16, 'PET-CAT-LIHUA', '宠物陪伴·狸花猫 30 分钟', 'PET_CAT', 'PET_COMPANION', 'CAT', '狸花猫', NULL, 30, '活泼亲人，适合休闲陪伴和拍照互动。', 28.00, 6, 'ENABLED', 210),
  (17, 'PET-CAT-RAGDOLL', '宠物陪伴·布偶猫 30 分钟', 'PET_CAT', 'PET_COMPANION', 'CAT', '布偶猫', NULL, 30, '性格温顺，适合包房安静陪伴。', 32.00, 4, 'ENABLED', 220),
  (18, 'PET-CAT-BRITISH', '宠物陪伴·英短猫 30 分钟', 'PET_CAT', 'PET_COMPANION', 'CAT', '英国短毛猫', NULL, 30, '圆脸安静，适合轻互动陪伴。', 30.00, 5, 'ENABLED', 230),
  (19, 'PET-DOG-LABRADOR', '宠物陪伴·拉布拉多 30 分钟', 'PET_DOG', 'PET_COMPANION', 'DOG', '拉布拉多', NULL, 30, '友好稳定，适合多人包房互动。', 30.00, 4, 'ENABLED', 240),
  (20, 'PET-DOG-CORGI', '宠物陪伴·柯基 30 分钟', 'PET_DOG', 'PET_COMPANION', 'DOG', '柯基', NULL, 30, '活跃可爱，适合休闲娱乐。', 26.00, 5, 'ENABLED', 250),
  (21, 'PET-DOG-BORDER', '宠物陪伴·边牧 30 分钟', 'PET_DOG', 'PET_COMPANION', 'DOG', '边境牧羊犬', NULL, 30, '聪明亲人，适合互动小游戏。', 34.00, 3, 'ENABLED', 260),
  (22, 'PET-REP-GECKO', '宠物陪伴·豹纹守宫 20 分钟', 'PET_REPTILE', 'PET_COMPANION', 'REPTILE', '豹纹守宫', NULL, 20, '安静观赏型爬宠，由工作人员陪同展示。', 35.00, 3, 'ENABLED', 270),
  (23, 'PET-REP-DRAGON', '宠物陪伴·鬃狮蜥 20 分钟', 'PET_REPTILE', 'PET_COMPANION', 'REPTILE', '鬃狮蜥', NULL, 20, '温顺观赏互动，适合拍照体验。', 36.00, 2, 'ENABLED', 280),
  (24, 'PET-REP-SNAKE', '宠物陪伴·玉米蛇 20 分钟', 'PET_REPTILE', 'PET_COMPANION', 'REPTILE', '玉米蛇', NULL, 20, '观赏体验服务，全程由工作人员看护。', 32.00, 2, 'ENABLED', 290),
  (25, 'EXPERT-FPS', '高手陪玩·FPS 枪王 30 分钟', 'EXPERT_PLAY', 'EXPERT_COMPANION', NULL, NULL, 'FPS 枪王', 30, '真人高手陪玩，适合无畏契约、CS2、APEX。', 45.00, 8, 'ENABLED', 310),
  (26, 'EXPERT-MOBA', '高手陪玩·MOBA 指挥 30 分钟', 'EXPERT_PLAY', 'EXPERT_COMPANION', NULL, NULL, 'MOBA 指挥', 30, '真人高手陪玩，适合英雄联盟开黑上分。', 42.00, 8, 'ENABLED', 320),
  (27, 'EXPERT-DUNGEON', '高手陪玩·副本速刷 30 分钟', 'EXPERT_PLAY', 'EXPERT_COMPANION', NULL, NULL, '副本速刷', 30, '真人高手协助副本机制教学与速刷。', 38.00, 6, 'ENABLED', 330),
  (28, 'EXPERT-REVIEW', '高手陪玩·战术复盘 30 分钟', 'EXPERT_PLAY', 'EXPERT_COMPANION', NULL, NULL, '战术复盘', 30, '真人高手语音复盘，适合战队训练。', 50.00, 5, 'ENABLED', 340)
ON DUPLICATE KEY UPDATE
  product_code = VALUES(product_code),
  product_name = VALUES(product_name),
  category = VALUES(category),
  product_type = VALUES(product_type),
  pet_species = VALUES(pet_species),
  pet_breed = VALUES(pet_breed),
  expert_role = VALUES(expert_role),
  service_duration_minutes = VALUES(service_duration_minutes),
  description = VALUES(description),
  price = VALUES(price),
  stock = VALUES(stock),
  status = VALUES(status),
  sort_order = VALUES(sort_order);

INSERT INTO device_info
  (id, device_code, area, area_type, room_capacity, hourly_rate_hint,
   seat_no, ip_address, config_desc, status)
VALUES
  (1, 'PC-A01', '大厅A区', 'LOBBY_A', 1, 10.00, 'A01', '192.168.1.101', 'RTX 4060 / 32G / 竞技外设', 'IN_USE'),
  (2, 'PC-A02', '大厅A区', 'LOBBY_A', 1, 10.00, 'A02', '192.168.1.102', 'RTX 4060 / 32G / 竞技外设', 'IDLE'),
  (3, 'PC-A03', '大厅A区', 'LOBBY_A', 1, 10.00, 'A03', '192.168.1.103', 'RTX 3060 / 16G', 'MAINTENANCE'),
  (4, 'PC-A04', '大厅A区', 'LOBBY_A', 1, 10.00, 'A04', '192.168.1.104', 'RTX 3060 / 16G', 'FAULT'),
  (5, 'PC-A05', '大厅A区', 'LOBBY_A', 1, 10.00, 'A05', '192.168.1.105', 'RTX 4060 / 32G / 竞技外设', 'IN_USE'),
  (6, 'PC-A06', '大厅A区', 'LOBBY_A', 1, 10.00, 'A06', '192.168.1.106', 'RTX 4060 / 32G / 竞技外设', 'IDLE'),
  (18, 'PC-A07', '大厅A区', 'LOBBY_A', 1, 10.00, 'A07', '192.168.1.107', 'RTX 3060Ti / 16G / 机械键盘', 'IDLE'),
  (19, 'PC-A08', '大厅A区', 'LOBBY_A', 1, 10.00, 'A08', '192.168.1.108', 'RTX 4070 / 32G / 高刷屏', 'IDLE'),
  (7, 'PC-B01', '大厅B区', 'LOBBY_B', 1, 12.00, 'B01', '192.168.1.121', 'RTX 4070 / 32G / 高刷屏', 'IN_USE'),
  (8, 'PC-B02', '大厅B区', 'LOBBY_B', 1, 12.00, 'B02', '192.168.1.122', 'RTX 4070 / 32G / 高刷屏', 'IDLE'),
  (9, 'PC-B03', '大厅B区', 'LOBBY_B', 1, 12.00, 'B03', '192.168.1.123', 'RTX 4070 / 32G / 高刷屏', 'IN_USE'),
  (20, 'PC-B04', '大厅B区', 'LOBBY_B', 1, 12.00, 'B04', '192.168.1.124', 'RTX 4060 / 32G / 曲面屏', 'IDLE'),
  (21, 'PC-B05', '大厅B区', 'LOBBY_B', 1, 12.00, 'B05', '192.168.1.125', 'RTX 3060 / 16G / 普通外设', 'FAULT'),
  (22, 'PC-B06', '大厅B区', 'LOBBY_B', 1, 12.00, 'B06', '192.168.1.126', 'RTX 4070 / 32G / 高刷屏', 'IDLE'),
  (10, 'PC-SVIP01', '单人豪华包房', 'ROOM_SINGLE_LUXURY', 1, 22.00, 'S01', '192.168.1.151', 'RTX 4080 / 64G / 静音包房', 'IDLE'),
  (11, 'PC-SVIP02', '单人豪华包房', 'ROOM_SINGLE_LUXURY', 1, 22.00, 'S02', '192.168.1.152', 'RTX 4080 / 64G / 静音包房', 'IDLE'),
  (23, 'PC-SVIP03', '单人豪华包房', 'ROOM_SINGLE_LUXURY', 1, 22.00, 'S03', '192.168.1.153', 'RTX 4080 / 64G / 降噪耳机', 'IN_USE'),
  (24, 'PC-SVIP04', '单人豪华包房', 'ROOM_SINGLE_LUXURY', 1, 22.00, 'S04', '192.168.1.154', 'RTX 4070Ti / 32G / 静音包房', 'IDLE'),
  (12, 'PC-DUO01', '双人包房', 'ROOM_DOUBLE', 2, 36.00, 'D01', '192.168.1.161', '双人连坐 / RTX 4070 / 32G', 'IDLE'),
  (13, 'PC-DUO02', '双人包房', 'ROOM_DOUBLE', 2, 36.00, 'D02', '192.168.1.162', '双人连坐 / RTX 4070 / 32G', 'IDLE'),
  (25, 'PC-DUO03', '双人包房', 'ROOM_DOUBLE', 2, 36.00, 'D03', '192.168.1.163', '双人包房 / RTX 4070Ti / 32G', 'IN_USE'),
  (26, 'PC-DUO04', '双人包房', 'ROOM_DOUBLE', 2, 36.00, 'D04', '192.168.1.164', '双人包房 / RTX 4060Ti / 32G', 'IDLE'),
  (14, 'PC-QUAD01', '四人包房', 'ROOM_QUAD', 4, 62.00, 'Q01', '192.168.1.171', '四人开黑 / RTX 4070Ti / 32G', 'IDLE'),
  (15, 'PC-QUAD02', '四人包房', 'ROOM_QUAD', 4, 62.00, 'Q02', '192.168.1.172', '四人开黑 / RTX 4070Ti / 32G', 'MAINTENANCE'),
  (27, 'PC-QUAD03', '四人包房', 'ROOM_QUAD', 4, 62.00, 'Q03', '192.168.1.173', '四人包房 / RTX 4080 / 64G', 'IN_USE'),
  (28, 'PC-QUAD04', '四人包房', 'ROOM_QUAD', 4, 62.00, 'Q04', '192.168.1.174', '四人包房 / RTX 4070Ti / 32G', 'IDLE'),
  (16, 'PC-FIVE01', '五人包房', 'ROOM_FIVE', 5, 78.00, 'F01', '192.168.1.181', '五人战队房 / RTX 4080 / 64G', 'IDLE'),
  (17, 'PC-FIVE02', '五人包房', 'ROOM_FIVE', 5, 78.00, 'F02', '192.168.1.182', '五人战队房 / RTX 4080 / 64G', 'IDLE'),
  (29, 'PC-FIVE03', '五人包房', 'ROOM_FIVE', 5, 78.00, 'F03', '192.168.1.183', '五人战队房 / RTX 4090 / 64G', 'IN_USE'),
  (30, 'PC-FIVE04', '五人包房', 'ROOM_FIVE', 5, 78.00, 'F04', '192.168.1.184', '五人战队房 / RTX 4080 / 64G', 'IDLE')
ON DUPLICATE KEY UPDATE
  device_code = VALUES(device_code),
  area = VALUES(area),
  area_type = VALUES(area_type),
  room_capacity = VALUES(room_capacity),
  hourly_rate_hint = VALUES(hourly_rate_hint),
  seat_no = VALUES(seat_no),
  ip_address = VALUES(ip_address),
  config_desc = VALUES(config_desc),
  status = VALUES(status),
  deleted = 0;

INSERT INTO machine_session
  (id, session_no, member_id, device_id, billing_rule_id, start_at, end_at,
   duration_minutes, estimated_amount, final_amount, status, operator_id, settled_by)
VALUES
  (1, 'S202609020001', 1, 1, 1, '2026-09-02 18:20:00', NULL, NULL, 16.50, NULL, 'RUNNING', 3, NULL),
  (2, 'S202609020002', 2, 5, 1, '2026-09-02 17:45:00', NULL, NULL, 22.00, NULL, 'RUNNING', 3, NULL),
  (3, 'S202609010012', 4, 8, 1, '2026-09-01 19:00:00', '2026-09-01 21:18:00', 138, 23.00, 23.00, 'ENDED', 3, 3),
  (4, 'S202609020003', 5, 7, 1, '2026-09-02 19:10:00', NULL, NULL, 12.00, NULL, 'RUNNING', 3, NULL),
  (5, 'S202609020004', 6, 25, 5, '2026-09-02 20:15:00', NULL, NULL, 36.00, NULL, 'RUNNING', 3, NULL),
  (6, 'S202609020005', 8, 27, 6, '2026-09-02 20:05:00', NULL, NULL, 62.00, NULL, 'RUNNING', 3, NULL),
  (7, 'S202609020006', 10, 29, 7, '2026-09-02 19:40:00', NULL, NULL, 78.00, NULL, 'RUNNING', 3, NULL),
  (8, 'S202609010013', 6, 23, 4, '2026-09-01 22:00:00', '2026-09-02 01:30:00', 210, 77.00, 77.00, 'ENDED', 3, 3),
  (9, 'S202608250011', 8, 13, 5, '2026-08-25 20:20:00', '2026-08-25 23:20:00', 180, 108.00, 108.00, 'ENDED', 3, 3),
  (10, 'S202608240009', 9, 19, 1, '2026-08-24 09:00:00', '2026-08-24 13:00:00', 240, 40.00, 40.00, 'ENDED', 3, 3)
ON DUPLICATE KEY UPDATE
  member_id = VALUES(member_id),
  device_id = VALUES(device_id),
  billing_rule_id = VALUES(billing_rule_id),
  start_at = VALUES(start_at),
  end_at = VALUES(end_at),
  duration_minutes = VALUES(duration_minutes),
  estimated_amount = VALUES(estimated_amount),
  final_amount = VALUES(final_amount),
  status = VALUES(status),
  operator_id = VALUES(operator_id),
  settled_by = VALUES(settled_by);

INSERT INTO recharge_record (id, recharge_no, member_id, amount, gift_amount, pay_method, operator_id, remark, created_at) VALUES
  (1, 'R202609020001', 1, 120.00, 10.00, 'CASH', 3, '晚间上机充值', '2026-09-02 18:00:00'),
  (2, 'R202609020002', 2, 200.00, 20.00, 'WECHAT', 3, 'VIP 会员续充', '2026-09-02 17:30:00'),
  (3, 'R202609010001', 4, 300.00, 30.00, 'ALIPAY', 3, '包房用户充值', '2026-09-01 18:30:00'),
  (4, 'R202609020003', 5, 180.00, 0.00, 'WECHAT', 3, '开黑前充值', '2026-09-02 18:55:00'),
  (5, 'R202609020004', 6, 500.00, 50.00, 'ALIPAY', 3, '战队训练充值', '2026-09-02 20:00:00'),
  (6, 'R202608120001', 7, 80.00, 0.00, 'CASH', 3, '低频会员充值', '2026-08-12 15:00:00'),
  (7, 'R202608250001', 8, 260.00, 20.00, 'WECHAT', 3, '周末组队充值', '2026-08-25 20:00:00'),
  (8, 'R202608240001', 9, 120.00, 0.00, 'CASH', 3, '上午刷本充值', '2026-08-24 08:50:00'),
  (9, 'R202609020005', 10, 800.00, 100.00, 'ALIPAY', 3, '五人房包夜充值', '2026-09-02 19:20:00'),
  (10, 'R202609010002', 1, 60.00, 0.00, 'WECHAT', 3, '饮品点单前充值', '2026-09-01 12:10:00')
ON DUPLICATE KEY UPDATE
  amount = VALUES(amount),
  gift_amount = VALUES(gift_amount),
  pay_method = VALUES(pay_method),
  operator_id = VALUES(operator_id),
  remark = VALUES(remark),
  created_at = VALUES(created_at);

INSERT INTO consume_record (id, consume_no, member_id, session_id, consume_type, amount, balance_after, operator_id, created_at) VALUES
  (1, 'C202609010001', 4, 3, 'MACHINE', 23.00, 337.00, 3, '2026-09-01 21:18:00'),
  (2, 'C202609020001', 1, 1, 'MACHINE', 16.50, 103.50, 3, '2026-09-02 19:50:00'),
  (3, 'C202609020002', 2, 2, 'MACHINE', 22.00, 198.00, 3, '2026-09-02 19:55:00'),
  (4, 'C202609020003', 1, NULL, 'SHOP', 33.00, 86.30, 5, '2026-09-02 19:05:00'),
  (5, 'C202609020004', 2, NULL, 'SHOP', 45.00, 153.00, 5, '2026-09-02 19:10:00'),
  (6, 'C202609020005', 5, NULL, 'SHOP', 38.00, 142.00, 5, '2026-09-02 19:30:00'),
  (7, 'C202609020006', 6, 5, 'MACHINE', 36.00, 514.00, 3, '2026-09-02 20:45:00'),
  (8, 'C202609020007', 6, NULL, 'SHOP', 83.00, 431.00, 5, '2026-09-02 20:50:00'),
  (9, 'C202608250001', 8, 9, 'MACHINE', 108.00, 172.00, 3, '2026-08-25 23:20:00'),
  (10, 'C202608240001', 9, 10, 'MACHINE', 40.00, 80.00, 3, '2026-08-24 13:00:00'),
  (11, 'C202609020008', 10, 7, 'MACHINE', 78.00, 742.00, 3, '2026-09-02 20:55:00'),
  (12, 'C202609020009', 10, NULL, 'SHOP', 102.00, 640.00, 5, '2026-09-02 21:00:00')
ON DUPLICATE KEY UPDATE
  member_id = VALUES(member_id),
  session_id = VALUES(session_id),
  consume_type = VALUES(consume_type),
  amount = VALUES(amount),
  balance_after = VALUES(balance_after),
  operator_id = VALUES(operator_id),
  created_at = VALUES(created_at);

INSERT INTO member_account_flow
  (id, flow_no, member_id, related_id, related_type, change_amount, balance_before,
   balance_after, operator_id, remark, created_at)
VALUES
  (1, 'F202609020001', 1, 1, 'RECHARGE', 130.00, 0.00, 130.00, 3, '晚间上机充值', '2026-09-02 18:00:00'),
  (2, 'F202609020002', 1, 4, 'PURCHASE', -33.00, 119.30, 86.30, 5, '狸花猫宠物陪伴和饮品订单', '2026-09-02 19:05:00'),
  (3, 'F202609020003', 2, 2, 'RECHARGE', 220.00, 0.00, 220.00, 3, 'VIP 会员续充', '2026-09-02 17:30:00'),
  (4, 'F202609020004', 2, 5, 'PURCHASE', -45.00, 198.00, 153.00, 5, '简餐和饮品订单', '2026-09-02 19:10:00'),
  (5, 'F202609020005', 5, 4, 'RECHARGE', 180.00, 0.00, 180.00, 3, '开黑前充值', '2026-09-02 18:55:00'),
  (6, 'F202609020006', 5, 6, 'PURCHASE', -38.00, 180.00, 142.00, 5, '零食和柯基宠物陪伴订单', '2026-09-02 19:30:00'),
  (7, 'F202609020007', 6, 5, 'RECHARGE', 550.00, 0.00, 550.00, 3, '战队训练充值', '2026-09-02 20:00:00'),
  (8, 'F202609020008', 6, 8, 'PURCHASE', -83.00, 514.00, 431.00, 5, '高手陪玩和爬宠陪伴组合订单', '2026-09-02 20:50:00'),
  (9, 'F202608250001', 8, 7, 'RECHARGE', 280.00, 0.00, 280.00, 3, '周末组队充值', '2026-08-25 20:00:00'),
  (10, 'F202608250002', 8, 9, 'CONSUME', -108.00, 280.00, 172.00, 3, '双人包房上机消费', '2026-08-25 23:20:00'),
  (11, 'F202609020009', 10, 9, 'RECHARGE', 900.00, 0.00, 900.00, 3, '五人房包夜充值', '2026-09-02 19:20:00'),
  (12, 'F202609020010', 10, 12, 'PURCHASE', -102.00, 742.00, 640.00, 5, '战队包房点单和真人高手陪玩', '2026-09-02 21:00:00')
ON DUPLICATE KEY UPDATE
  member_id = VALUES(member_id),
  related_id = VALUES(related_id),
  related_type = VALUES(related_type),
  change_amount = VALUES(change_amount),
  balance_before = VALUES(balance_before),
  balance_after = VALUES(balance_after),
  operator_id = VALUES(operator_id),
  remark = VALUES(remark),
  created_at = VALUES(created_at);

INSERT INTO service_call
  (id, call_no, member_id, device_id, call_type, message, status, handled_by, handled_at, created_at)
VALUES
  (1, 'SC202609020001', 1, 1, 'FRONT_DESK', '想确认狸花猫宠物陪伴还需要等多久。', 'PENDING', NULL, NULL, '2026-09-02 20:45:00'),
  (2, 'SC202609020002', 2, 5, 'SUPPLIES', '需要补一包纸巾和一次性耳机套。', 'PROCESSING', 3, NULL, '2026-09-02 20:38:00'),
  (3, 'SC202609020003', 5, 7, 'CLEANING', '桌面有饮料水渍，请帮忙清洁。', 'COMPLETED', 3, '2026-09-02 20:25:00', '2026-09-02 20:10:00'),
  (4, 'SC202609020004', 6, 25, 'DEVICE_HELP', '鼠标侧键无法触发宏，请协助检查。', 'PENDING', NULL, NULL, '2026-09-02 20:55:00'),
  (5, 'SC202608250001', 8, 13, 'FRONT_DESK', '双人包房想续费一小时。', 'COMPLETED', 3, '2026-08-25 22:15:00', '2026-08-25 22:05:00'),
  (6, 'SC202609020005', 10, 29, 'SUPPLIES', '五人房需要补充水和纸巾。', 'PROCESSING', 3, NULL, '2026-09-02 20:58:00')
ON DUPLICATE KEY UPDATE
  member_id = VALUES(member_id),
  device_id = VALUES(device_id),
  call_type = VALUES(call_type),
  message = VALUES(message),
  status = VALUES(status),
  handled_by = VALUES(handled_by),
  handled_at = VALUES(handled_at),
  created_at = VALUES(created_at);

INSERT INTO shop_order
  (id, order_no, member_id, device_id, total_amount, status, remark, handled_by, paid_at, completed_at, created_at)
VALUES
  (1, 'O202609020001', 1, 1, 33.00, 'PENDING', '狸花猫宠物陪伴，饮料少冰。', NULL, '2026-09-02 19:05:00', NULL, '2026-09-02 19:05:00'),
  (2, 'O202609020002', 2, 5, 45.00, 'PREPARING', '黑椒鸡排饭不要辣，咖啡常温。', 3, '2026-09-02 19:10:00', NULL, '2026-09-02 19:10:00'),
  (3, 'O202609020003', 5, 7, 38.00, 'DELIVERING', '柯基宠物陪伴，顺带一瓶冻柠茶。', 3, '2026-09-02 19:30:00', NULL, '2026-09-02 19:30:00'),
  (4, 'O202609020004', 6, 25, 83.00, 'PENDING', '需要 FPS 高手陪玩和玉米蛇观赏陪伴各一份。', NULL, '2026-09-02 20:50:00', NULL, '2026-09-02 20:50:00'),
  (5, 'O202609020005', 10, 29, 102.00, 'PREPARING', '战队房补给，高手陪玩要求冷静指挥。', 3, '2026-09-02 21:00:00', NULL, '2026-09-02 21:00:00'),
  (6, 'O202608250001', 8, 13, 37.00, 'COMPLETED', '双人包房周末套餐补给。', 3, '2026-08-25 22:05:00', '2026-08-25 22:20:00', '2026-08-25 22:05:00'),
  (7, 'O202608240001', 9, 19, 26.00, 'COMPLETED', '上午刷本简餐。', 3, '2026-08-24 11:00:00', '2026-08-24 11:15:00', '2026-08-24 11:00:00'),
  (8, 'O202609020006', 4, 8, 71.00, 'CANCELLED', '临时取消爬宠陪伴服务。', 3, '2026-09-02 18:20:00', NULL, '2026-09-02 18:20:00')
ON DUPLICATE KEY UPDATE
  member_id = VALUES(member_id),
  device_id = VALUES(device_id),
  total_amount = VALUES(total_amount),
  status = VALUES(status),
  remark = VALUES(remark),
  handled_by = VALUES(handled_by),
  paid_at = VALUES(paid_at),
  completed_at = VALUES(completed_at),
  created_at = VALUES(created_at);

INSERT INTO shop_order_item
  (id, order_id, product_id, product_name, unit_price, quantity, subtotal)
VALUES
  (1, 1, 16, '宠物陪伴·狸花猫 30 分钟', 28.00, 1, 28.00),
  (2, 1, 1, '冰镇可乐', 5.00, 1, 5.00),
  (3, 2, 13, '黑椒鸡排饭', 22.00, 1, 22.00),
  (4, 2, 3, '罐装咖啡', 8.00, 1, 8.00),
  (5, 2, 15, '火腿芝士三明治', 15.00, 1, 15.00),
  (6, 3, 20, '宠物陪伴·柯基 30 分钟', 26.00, 1, 26.00),
  (7, 3, 5, '冻柠茶', 7.00, 1, 7.00),
  (8, 3, 1, '冰镇可乐', 5.00, 1, 5.00),
  (9, 4, 25, '高手陪玩·FPS 枪王 30 分钟', 45.00, 1, 45.00),
  (10, 4, 24, '宠物陪伴·玉米蛇 20 分钟', 32.00, 1, 32.00),
  (11, 4, 6, '矿泉水', 3.00, 2, 6.00),
  (12, 5, 28, '高手陪玩·战术复盘 30 分钟', 50.00, 1, 50.00),
  (13, 5, 14, '鸡排能量套餐', 28.00, 1, 28.00),
  (14, 5, 4, '能量饮料', 9.00, 2, 18.00),
  (15, 5, 8, '烤肠', 6.00, 1, 6.00),
  (16, 6, 19, '宠物陪伴·拉布拉多 30 分钟', 30.00, 1, 30.00),
  (17, 6, 7, '薯片', 7.00, 1, 7.00),
  (18, 7, 12, '香辣牛肉面', 12.00, 1, 12.00),
  (19, 7, 2, '冰红茶', 5.00, 2, 10.00),
  (20, 7, 10, '香辣条', 4.00, 1, 4.00),
  (21, 8, 22, '宠物陪伴·豹纹守宫 20 分钟', 35.00, 1, 35.00),
  (22, 8, 23, '宠物陪伴·鬃狮蜥 20 分钟', 36.00, 1, 36.00)
ON DUPLICATE KEY UPDATE
  order_id = VALUES(order_id),
  product_id = VALUES(product_id),
  product_name = VALUES(product_name),
  unit_price = VALUES(unit_price),
  quantity = VALUES(quantity),
  subtotal = VALUES(subtotal);

DELETE FROM face_profile
WHERE feature_ref IN ('features/member-1.bin', 'features/member-2.bin');

UPDATE face_profile fp
JOIN sys_user u ON u.member_id = fp.member_id AND u.deleted = 0
SET fp.sys_user_id = u.id
WHERE fp.sys_user_id IS NULL;

INSERT INTO device_fault (id, device_id, fault_type, description, status, reported_by, reported_at) VALUES
  (1, 4, 'USER_REPORT', '耳机左声道无声音，请协助检查。', 'OPEN', 5, '2026-09-02 19:30:00'),
  (2, 21, 'HARDWARE', 'B05 鼠标滚轮回弹异常，需要更换鼠标。', 'OPEN', 3, '2026-09-02 18:40:00'),
  (3, 15, 'MAINTENANCE', '四人包房 Q02 做显卡驱动和系统镜像维护。', 'PROCESSING', 3, '2026-09-02 17:20:00'),
  (4, 3, 'PERIPHERAL', 'A03 键盘 W 键偶发失灵，已登记待备件。', 'PROCESSING', 3, '2026-09-01 22:10:00'),
  (5, 10, 'NETWORK', '单人豪华包房 S01 网络延迟波动，已完成网线重插。', 'RESOLVED', 3, '2026-09-01 16:00:00')
ON DUPLICATE KEY UPDATE
  device_id = VALUES(device_id),
  fault_type = VALUES(fault_type),
  description = VALUES(description),
  status = VALUES(status),
  reported_by = VALUES(reported_by),
  reported_at = VALUES(reported_at);

INSERT INTO client_device (id, device_id, device_code, client_token, app_version, online_status, last_heartbeat_at) VALUES
  (1, 1, 'PC-A01', 'dev-token-pc-a01', '0.2.0', 'ONLINE', '2026-09-02 20:58:00'),
  (2, 2, 'PC-A02', 'dev-token-pc-a02', '0.2.0', 'OFFLINE', NULL),
  (3, 5, 'PC-A05', 'dev-token-pc-a05', '0.2.0', 'ONLINE', '2026-09-02 20:58:00'),
  (4, 7, 'PC-B01', 'dev-token-pc-b01', '0.2.0', 'ONLINE', '2026-09-02 20:58:00'),
  (5, 23, 'PC-SVIP03', 'dev-token-pc-svip03', '0.2.0', 'ONLINE', '2026-09-02 20:58:00'),
  (6, 25, 'PC-DUO03', 'dev-token-pc-duo03', '0.2.0', 'ONLINE', '2026-09-02 20:58:00'),
  (7, 27, 'PC-QUAD03', 'dev-token-pc-quad03', '0.2.0', 'ONLINE', '2026-09-02 20:58:00'),
  (8, 29, 'PC-FIVE03', 'dev-token-pc-five03', '0.2.0', 'ONLINE', '2026-09-02 20:58:00')
ON DUPLICATE KEY UPDATE
  device_id = VALUES(device_id),
  device_code = VALUES(device_code),
  client_token = VALUES(client_token),
  app_version = VALUES(app_version),
  online_status = VALUES(online_status),
  last_heartbeat_at = VALUES(last_heartbeat_at);

DROP VIEW IF EXISTS v_dashboard_summary;

CREATE VIEW v_dashboard_summary AS
SELECT
  (SELECT COUNT(*) FROM machine_session WHERE status = 'RUNNING') AS running_sessions,
  (SELECT COUNT(*) FROM device_info WHERE status = 'IDLE' AND deleted = 0) AS idle_devices,
  (SELECT COUNT(*) FROM device_info WHERE status = 'FAULT' AND deleted = 0) AS fault_devices,
  (SELECT COALESCE(SUM(amount), 0) FROM recharge_record WHERE DATE(created_at) = CURRENT_DATE) AS today_recharge,
  (SELECT COALESCE(SUM(amount), 0) FROM consume_record WHERE DATE(created_at) = CURRENT_DATE) AS today_consume;
