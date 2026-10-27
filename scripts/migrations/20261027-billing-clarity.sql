-- Run against the existing selected database. Does not reset accounts or sessions.
INSERT INTO billing_rule (rule_name, price_per_hour, min_minutes, billing_unit_minutes, low_balance_threshold, status)
SELECT '大厅B区规则', 12.00, 15, 15, 10.00, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM billing_rule WHERE rule_name = '大厅B区规则');

UPDATE billing_rule SET rule_name = '长时段规则（按小时计费）'
WHERE rule_name = '包夜规则' AND price_per_hour = 50.00 AND min_minutes = 360 AND billing_unit_minutes = 360;
