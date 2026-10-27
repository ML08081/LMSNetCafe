package com.lms.netcafe.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DailyReportService {
    private final JdbcTemplate db;
    private final BusinessMetricsService metrics;
    private final AiModelClient model;
    private final ObjectMapper json;
    private final ReentrantLock generation = new ReentrantLock();
    public DailyReportService(JdbcTemplate db, BusinessMetricsService metrics, AiModelClient model, ObjectMapper json) {
        this.db = db; this.metrics = metrics; this.model = model; this.json = json;
    }

    public List<Map<String, Object>> history() {
        return db.queryForList("""
                SELECT report_date AS reportDate, generation_mode AS mode, generated_at AS generatedAt
                FROM ai_daily_report ORDER BY report_date DESC LIMIT 30
                """);
    }

    public Map<String, Object> get(LocalDate date) {
        validate(date);
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM ai_daily_report WHERE report_date=?", date.toString());
        if (rows.isEmpty()) return null;
        Map<String, Object> stored = rows.get(0), result = new LinkedHashMap<>();
        try { result.put("metrics", json.readTree(stored.get("metrics_json").toString())); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "日报快照无法读取，请重新生成"); }
        result.put("date", date.toString()); result.put("analysis", stored.get("analysis"));
        result.put("mode", stored.get("generation_mode")); result.put("model", stored.get("model_name"));
        result.put("notice", stored.get("notice")); result.put("generatedAt", stored.get("generated_at"));
        return result;
    }

    public Map<String, Object> generate(LocalDate date, Long operatorId) {
        validate(date);
        if (!generation.tryLock()) throw new ResponseStatusException(HttpStatus.CONFLICT, "已有日报正在生成，请稍后查看");
        try {
            Map<String, Object> snapshot = metrics.snapshot(date);
            String fallback = "经营摘要（" + date + "）\n\n净消费 ¥" + snapshot.get("netConsumption")
                    + "，其中上机消费 ¥" + snapshot.get("machineConsumption") + "、商品与服务消费 ¥" + snapshot.get("shopConsumption")
                    + "；退款 ¥" + snapshot.get("refundAmount") + "。充值实收 ¥" + snapshot.get("rechargeCash")
                    + "，充值赠送 ¥" + snapshot.get("rechargeGifts") + "，两者不计入净消费。\n"
                    + "当日新开会话 " + snapshot.get("startedSessions") + " 次，完成结算 " + snapshot.get("settledSessions")
                    + " 次，到店会员 " + snapshot.get("visitingMembers") + " 人，新增会员 " + snapshot.get("newMembers") + " 人。\n"
                    + "按当前机位数量估算的机时利用率 " + snapshot.get("utilizationPercent") + "%；当前故障机位 "
                    + snapshot.get("currentFaultDevices") + " 台，未解决故障 " + snapshot.get("openFaultsNow")
                    + " 条，待处理订单 " + snapshot.get("pendingOrdersNow") + " 笔。\n\n"
                    + "建议：核对消费与退款流水，优先处理未解决故障及待处理订单，结合各区域机时安排人员。"
                    + "没有成本资料，不能据此判断利润；数据为零时请核对所选日期及是否实际开展业务，不推断经营原因。";
            AiModelClient.Completion completion = model.complete("你是网咖经营分析助手。用中文按“经营概况、异常与风险、可执行建议、数据局限”四段生成日报。"
                    + "只能引用服务端汇总快照中的数字，严格区分净消费、充值实收、赠送和退款，不把充值当营收相加，不推断利润。"
                    + "当前状态必须标明生成时，历史利用率仅是当前机位规模下的估算，不声称已完成促销或维修。没有数据和基期时说明不足。", snapshot, List.of(), "分析所选日期的门店经营情况。", fallback);
            db.update("""
                    INSERT INTO ai_daily_report (report_date,metrics_json,analysis,generation_mode,model_name,notice,generated_by,generated_at)
                    VALUES (?,?,?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE
                      metrics_json=VALUES(metrics_json),analysis=VALUES(analysis),generation_mode=VALUES(generation_mode),
                      model_name=VALUES(model_name),notice=VALUES(notice),generated_by=VALUES(generated_by),generated_at=NOW()
                    """, date.toString(), json.writeValueAsString(snapshot), completion.text(), completion.mode(),
                    completion.model(), completion.notice(), operatorId);
            return get(date);
        } catch (ResponseStatusException ex) { throw ex; }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("日报快照序列化失败", ex); }
        finally { generation.unlock(); }
    }

    private void validate(LocalDate date) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (date == null || date.isAfter(today) || date.isBefore(today.minusYears(5))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择最近五年内且不晚于今天的日期");
        }
    }
}
