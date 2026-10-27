package com.lms.netcafe.module.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessMetricsService {
    private final JdbcTemplate db;
    public BusinessMetricsService(JdbcTemplate db) { this.db = db; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> snapshot(LocalDate date) {
        String start = date + " 00:00:00", end = date.plusDays(1) + " 00:00:00", previous = date.minusDays(1) + " 00:00:00";
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("date", date.toString()); metrics.put("asOf", OffsetDateTime.now().toString());
        Map<String, Object> amounts = db.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN amount>0 THEN amount ELSE 0 END),0) AS gross,
                  COALESCE(SUM(CASE WHEN amount<0 THEN -amount ELSE 0 END),0) AS refunds,
                  COALESCE(SUM(amount),0) AS net,
                  COALESCE(SUM(CASE WHEN consume_type='MACHINE' THEN amount ELSE 0 END),0) AS machine,
                  COALESCE(SUM(CASE WHEN consume_type='SHOP' THEN amount ELSE 0 END),0) AS shop
                FROM consume_record WHERE created_at>=? AND created_at<?
                """, start, end);
        for (String[] pair : List.of(new String[]{"grossConsumption","gross"}, new String[]{"refundAmount","refunds"},
                new String[]{"netConsumption","net"}, new String[]{"machineConsumption","machine"}, new String[]{"shopConsumption","shop"})) {
            metrics.put(pair[0], decimal(amounts.get(pair[1])));
        }
        Map<String, Object> recharges = db.queryForMap("""
                SELECT COALESCE(SUM(amount),0) AS cash, COALESCE(SUM(gift_amount),0) AS gifts,
                  COUNT(*) AS transactions FROM recharge_record WHERE created_at>=? AND created_at<?
                """, start, end);
        metrics.put("rechargeCash", decimal(recharges.get("cash")));
        metrics.put("rechargeGifts", decimal(recharges.get("gifts")));
        metrics.put("rechargeTransactions", recharges.get("transactions"));
        BigDecimal previousNet = db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM consume_record WHERE created_at>=? AND created_at<?",
                BigDecimal.class, previous, start);
        metrics.put("previousNetConsumption", previousNet);
        metrics.put("netChange", decimal(metrics.get("netConsumption")).subtract(previousNet));
        metrics.put("netChangePercent", previousNet.signum() > 0 ? decimal(metrics.get("netChange")).multiply(BigDecimal.valueOf(100))
                .divide(previousNet, 2, RoundingMode.HALF_UP) : null);
        metrics.put("newMembers", count("SELECT COUNT(*) FROM member_info WHERE deleted=0 AND registered_at>=? AND registered_at<?", start, end));
        metrics.put("startedSessions", count("SELECT COUNT(*) FROM machine_session WHERE start_at>=? AND start_at<?", start, end));
        metrics.put("settledSessions", count("SELECT COUNT(*) FROM machine_session WHERE status='ENDED' AND end_at>=? AND end_at<?", start, end));
        metrics.put("visitingMembers", count("""
                SELECT COUNT(DISTINCT member_id) FROM machine_session
                WHERE start_at<? AND COALESCE(end_at,NOW())>?
                """, end, start));
        metrics.put("paidOrders", count("SELECT COUNT(*) FROM shop_order WHERE paid_at>=? AND paid_at<?", start, end));
        metrics.put("cancelledOrders", count("SELECT COUNT(*) FROM shop_order WHERE status='CANCELLED' AND paid_at>=? AND paid_at<?", start, end));
        metrics.put("newFaults", count("SELECT COUNT(*) FROM device_fault WHERE reported_at>=? AND reported_at<?", start, end));

        List<Map<String, Object>> currentDevices = db.queryForList("""
                SELECT COALESCE(area,'未分区') AS area, COUNT(*) AS devices,
                  SUM(CASE WHEN status='FAULT' THEN 1 ELSE 0 END) AS faultDevices,
                  SUM(CASE WHEN status='IDLE' THEN 1 ELSE 0 END) AS idleDevices
                FROM device_info WHERE deleted=0 GROUP BY COALESCE(area,'未分区') ORDER BY area
                """);
        Long elapsedSeconds = db.queryForObject("""
                SELECT GREATEST(0,TIMESTAMPDIFF(SECOND,CAST(? AS DATETIME),LEAST(NOW(),CAST(? AS DATETIME))))
                """, Long.class, start, end);
        List<Map<String, Object>> usage = db.queryForList("""
                SELECT COALESCE(d.area,'未分区') AS area,
                  SUM(GREATEST(0,TIMESTAMPDIFF(SECOND,
                    GREATEST(s.start_at,CAST(? AS DATETIME)),
                    LEAST(COALESCE(s.end_at,NOW()),CAST(? AS DATETIME),NOW())))) AS usedSeconds
                FROM machine_session s JOIN device_info d ON d.id=s.device_id AND d.deleted=0
                WHERE s.start_at<? AND COALESCE(s.end_at,NOW())>?
                GROUP BY COALESCE(d.area,'未分区')
                """, start, end, end, start);
        List<Map<String, Object>> areas = new ArrayList<>();
        long totalDevices = 0, totalFaults = 0, totalUsed = 0;
        for (Map<String, Object> area : currentDevices) {
            long devices = ((Number) area.get("devices")).longValue();
            long used = usage.stream().filter(row -> row.get("area").equals(area.get("area")))
                    .mapToLong(row -> ((Number) row.get("usedSeconds")).longValue()).sum();
            long capacity = elapsedSeconds * devices;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("area", area.get("area")); entry.put("devices", devices);
            entry.put("currentFaultDevices", area.get("faultDevices")); entry.put("currentIdleDevices", area.get("idleDevices"));
            entry.put("usedMinutes", BigDecimal.valueOf(used).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
            entry.put("utilizationPercent", percentage(used, capacity)); areas.add(entry);
            totalDevices += devices; totalUsed += used; totalFaults += ((Number) area.get("faultDevices")).longValue();
        }
        metrics.put("areas", areas); metrics.put("currentDeviceCount", totalDevices);
        metrics.put("currentFaultDevices", totalFaults);
        metrics.put("utilizationPercent", percentage(totalUsed, elapsedSeconds * totalDevices));
        metrics.put("openFaultsNow", count("SELECT COUNT(*) FROM device_fault WHERE status<>'RESOLVED'"));
        metrics.put("pendingOrdersNow", count("SELECT COUNT(*) FROM shop_order WHERE status IN ('PENDING','PREPARING','DELIVERING')"));
        List<Map<String, Object>> hours = db.queryForList("""
                SELECT HOUR(created_at) AS hourOfDay, SUM(amount) AS net
                FROM consume_record WHERE created_at>=? AND created_at<? GROUP BY HOUR(created_at) ORDER BY hourOfDay
                """, start, end);
        metrics.put("hourlyConsumption", hours.stream().map(row -> Map.of("hour", row.get("hourOfDay"), "amount", decimal(row.get("net")))).toList());
        metrics.put("definitions", List.of(
                "净消费=消费流水正向金额减退款；充值实收和赠送分别展示，不与消费重复相加。消费按入账日期归属，不是利润。",
                "会话跨天机时按所选日期截取，今日只计算到生成时；利用率使用当前未删除机位数量估算，不能还原历史增减设备。",
                "当前故障、空闲和待处理订单为生成时实时状态，不能解读为历史日期当天状态。退款按退款入账日计，无法按原订单拆分退款品类。",
                "日报是生成时的数据快照，后续补录、退款及今日新增业务需重新生成。前日净消费为零或负数时不计算增长百分比。"));
        return metrics;
    }

    private long count(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private BigDecimal decimal(Object value) { return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal percentage(long used, long capacity) {
        return capacity == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(used).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
    }
}
