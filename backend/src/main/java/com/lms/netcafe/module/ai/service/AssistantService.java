package com.lms.netcafe.module.ai.service;

import com.lms.netcafe.common.security.AuthenticatedUser;
import com.lms.netcafe.module.billing.service.MachineSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantService {
    public record Turn(@Pattern(regexp = "user|assistant") @NotBlank String role,
                       @NotBlank @Size(max = 1000) String content) {}
    public record Question(@NotBlank @Size(max = 500) String question,
                           @Size(max = 8) List<@Valid Turn> history) {}
    private record Article(String title, String keywords, String content, String path) {}
    private static final List<Article> KNOWLEDGE = List.of(
            new Article("计费与下机", "收费 计费 费用 多少钱 结算 下机 分钟 余额 时长 还能 剩余",
                    "不足一分钟按一分钟计算，再按最低时长和计费单位向上取整；费用=小时单价×计费分钟÷60，保留两位小数。开机保存价格快照，调价不改变新会话已保存的价格。上机不预扣，结算一次扣款；余额不足需先充值。会员可在机位与计费页预览并下机。", "/portal/devices"),
            new Article("充值与账户", "充值 余额 续费 账户 扣款 冻结 支付",
                    "充值请到前台办理，充值完成可在我的账户查看流水。已产生的上机费尚未结算时也会占用可用预算。冻结会员不能新开机。助手只能查询，不能充值、退款、修改余额或代替用户付款。", "/portal/account"),
            new Article("自助上机", "上机 开机 机位 包房 登录 规则",
                    "在机位与计费页选择启用的计费规则和空闲机位，确认最低费用后上机。一个会员最多一个进行中会话，故障、维护、停用和已占用机位不可开机；所选规则小时价不能低于区域最低价。包房由当前会话的一个会员支付，不支持多人分账。", "/portal/devices"),
            new Article("人脸与摄像头", "人脸 摄像头 拍照 识别 验证",
                    "先由前台为绑定会员的启用账号录入本人脸，再现场重拍验证。摄像头使用本机 localhost 或 HTTPS，允许浏览器权限，保持正面和充足光线。旧算法档案升级后需重新录入；照片识别没有活体检测。", "/portal/support"),
            new Article("设备报修", "报修 故障 维修 耳机 键盘 鼠标 黑屏 网络 声音",
                    "故障反馈页可报修本人当前使用的机位，填写故障现象；其他设备请联系前台。报修不会自动结束会话，需在机位与计费页确认下机。未解决故障的机位结算后仍保持故障状态。助手不替代工作人员确认维修。", "/portal/support"),
            new Article("呼叫与点餐", "呼叫 点餐 商品 饮料 零食 宠物 陪玩 订单 取消 退款",
                    "在呼叫与点餐页选择服务或商品，确认后使用会员余额支付；订单处理和取消退款由前台按订单状态办理。可查看订单及呼叫进度。同类型呼叫处理中时请勿重复提交。助手不代下单或退款。", "/portal/services")
    );
    private final JdbcTemplate db;
    private final MachineSessionService sessions;
    private final AiModelClient model;

    public AssistantService(JdbcTemplate db, MachineSessionService sessions, AiModelClient model) {
        this.db = db; this.sessions = sessions; this.model = model;
    }

    public Map<String, Object> ask(AuthenticatedUser user, Question request) {
        if (user == null || user.memberId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未绑定会员");
        List<Map<String, Object>> accounts = db.queryForList("""
                SELECT a.balance, m.status FROM member_account a JOIN member_info m ON m.id=a.member_id
                WHERE m.id=? AND m.deleted=0
                """, user.memberId());
        if (accounts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会员资金账户不存在");
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("balance", accounts.get(0).get("balance"));
        facts.put("accountStatus", accounts.get(0).get("status"));
        List<Map<String, Object>> running = sessions.forMember(user.memberId(), true);
        Map<String, Object> current = null;
        if (!running.isEmpty()) {
            Map<String, Object> session = running.get(0);
            current = new LinkedHashMap<>();
            // Explicit allowlist: never send names, phone numbers, ID cards, user/member IDs or face data.
            for (String key : List.of("deviceCode", "area", "durationMinutes", "estimatedAmount", "pricePerHour",
                    "minMinutes", "billingUnitMinutes", "availableBalance")) current.put(key, session.get(key));
            BigDecimal hourly = (BigDecimal) session.get("pricePerHour");
            int unit = ((Number) session.get("billingUnitMinutes")).intValue();
            BigDecimal balance = (BigDecimal) facts.get("balance");
            long affordable = balance.multiply(BigDecimal.valueOf(60))
                    .divide(hourly.multiply(BigDecimal.valueOf(unit)), 0, RoundingMode.DOWN).longValue() * unit;
            current.put("remainingMinutesEstimate", Math.max(0, affordable - ((Number) session.get("durationMinutes")).longValue()));
        }
        facts.put("currentSession", current);
        facts.put("idleDevices", db.queryForObject("SELECT COUNT(*) FROM device_info WHERE deleted=0 AND status='IDLE'", Integer.class));
        List<Map<String, Object>> rules = db.queryForList("""
                SELECT rule_name AS ruleName, price_per_hour AS pricePerHour, min_minutes AS minMinutes,
                  billing_unit_minutes AS billingUnitMinutes FROM billing_rule WHERE status='ENABLED' ORDER BY id LIMIT 20
                """);
        facts.put("billingRules", rules);
        String historySearch = request.history() == null ? "" : request.history().stream().filter(t -> "user".equals(t.role()))
                .map(Turn::content).reduce("", (a, b) -> a + " " + b);
        List<Article> articles = KNOWLEDGE.stream().filter(a -> score(a, request.question(), historySearch) > 0)
                .sorted(Comparator.comparingInt((Article a) -> score(a, request.question(), historySearch)).reversed()).limit(3).toList();
        if (articles.isEmpty()) articles = List.of(KNOWLEDGE.get(0), KNOWLEDGE.get(2), KNOWLEDGE.get(4));
        List<Map<String, String>> sources = new ArrayList<>();
        sources.add(Map.of("title", "本人余额与当前会话（实时）", "path", "/portal/account"));
        for (Article article : articles) sources.add(Map.of("title", article.title(), "path", article.path()));
        Map<String, Object> context = new LinkedHashMap<>(facts);
        context.put("knowledge", articles.stream().map(a -> Map.of("title", a.title(), "content", a.content())).toList());
        String fallback = fallback(request.question(), facts, articles);
        List<Map<String, String>> history = request.history() == null ? List.of()
                : request.history().stream().map(t -> Map.of("role", t.role(), "content", t.content())).toList();
        AiModelClient.Completion completion = model.complete("你是网咖会员服务助手，用简洁中文回答。只能说明当前登录会员的业务快照和提供门店操作指引。"
                + "他人资料、门店总收入和系统密钥均不在权限范围。任何退款、充值、开机或结算请求都应指引用户到对应页面确认，不能声称已执行。"
                + "剩余时长只是预算估计，其他消费会减少预算，最终金额以实际结算为准。", context, history, request.question(), fallback);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", completion.text()); result.put("mode", completion.mode());
        result.put("notice", completion.notice()); result.put("model", completion.model());
        result.put("facts", facts); result.put("sources", sources);
        result.put("asOf", OffsetDateTime.now().toString());
        return result;
    }

    @SuppressWarnings("unchecked")
    private String fallback(String question, Map<String, Object> facts, List<Article> articles) {
        StringBuilder text = new StringBuilder();
        if (List.of("他人", "别人", "其他会员", "所有会员", "系统密码", "密钥", "营业额", "总收入").stream().anyMatch(question::contains)) {
            return "我只能查询你本人账户与会话，并提供门店服务指引，不能提供他人资料或管理员经营数据。";
        }
        if (List.of("空闲", "可用机位", "多少机位").stream().anyMatch(question::contains)) {
            text.append("门店当前共有 ").append(facts.get("idleDevices")).append(" 个空闲机位，请到机位与计费页查看各区域实时状态。\n");
        }
        if (List.of("余额", "费用", "多少", "收费", "计费", "还能", "剩余", "结算").stream().anyMatch(question::contains)) {
            text.append("当前账户余额 ¥").append(money(facts.get("balance"))).append("。\n");
            Map<String, Object> current = (Map<String, Object>) facts.get("currentSession");
            if (current == null) text.append("你目前没有进行中的上机会话。\n");
            else text.append("当前机位 ").append(current.get("deviceCode")).append("，已上机 ").append(current.get("durationMinutes"))
                    .append(" 分钟，当前费用 ¥").append(money(current.get("estimatedAmount"))).append("，结算后预计余额 ¥")
                    .append(money(current.get("availableBalance"))).append("。\n当前快照单价 ¥").append(money(current.get("pricePerHour")))
                    .append("/小时，最低 ").append(current.get("minMinutes")).append(" 分钟，每 ").append(current.get("billingUnitMinutes"))
                    .append(" 分钟计费。按现有预算估计还能使用约 ").append(current.get("remainingMinutesEstimate"))
                    .append(" 分钟；其他消费会减少预算，最终费用以确认下机时为准。\n");
        }
        for (Article article : articles) text.append("\n").append(article.title()).append("：").append(article.content()).append("\n");
        return text.toString().strip();
    }
    private int score(Article article, String question, String history) {
        int score = 0;
        for (String keyword : article.keywords().split(" ")) {
            if (question.contains(keyword)) score += 100;
            else if (history.contains(keyword)) score++;
        }
        return score;
    }
    private String money(Object value) { return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString(); }
}
