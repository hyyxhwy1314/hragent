package org.example.hragent.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.agent.rag.QdrantVectorClient;
import org.example.hragent.agent.rag.RagProperties;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.ExpireReminder;
import org.example.hragent.entity.KnowledgeChunk;
import org.example.hragent.mapper.ExpireReminderMapper;
import org.example.hragent.service.EmailService;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.service.KnowledgeChunkService;
import org.example.hragent.service.KnowledgeChunkService.DocState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 知识库时效巡检任务：每日扫描文档有效期与复审截止日。
 * <p>
 * 两类动作：
 * <ul>
 *   <li><b>失效</b>：超过失效日期，或复审截止日 + 宽限期仍未复审的文档置 EXPIRED。
 *       检索层本身按日期硬过滤，即使任务未及时执行，过期文档也不会被召回；本任务负责同步 MySQL 状态用于审计；</li>
 *   <li><b>复审提醒</b>：复审截止日前 N 天邮件提醒 HR/HRBP/ADMIN，按（员工, 类型, 复审日）去重，避免重复打扰。</li>
 * </ul>
 */
@Slf4j
@Component
public class KnowledgeReviewJob {

    /** 提醒类型：知识文档复审 */
    private static final String REMIND_TYPE = "KNOWLEDGE_REVIEW";

    /** 需要收到复审提醒的角色 */
    private static final Set<String> HR_ROLES = Set.of("HR", "HRBP", "ADMIN");

    private final KnowledgeChunkService chunkService;
    private final EmployeeService employeeService;
    private final ExpireReminderMapper expireReminderMapper;
    private final EmailService emailService;
    private final QdrantVectorClient vectorClient;
    private final RagProperties props;

    public KnowledgeReviewJob(KnowledgeChunkService chunkService,
                              EmployeeService employeeService,
                              ExpireReminderMapper expireReminderMapper,
                              EmailService emailService,
                              QdrantVectorClient vectorClient,
                              RagProperties props) {
        this.chunkService = chunkService;
        this.employeeService = employeeService;
        this.expireReminderMapper = expireReminderMapper;
        this.emailService = emailService;
        this.vectorClient = vectorClient;
        this.props = props;
    }

    @Scheduled(cron = "0 30 3 * * ?")
    public void scanKnowledgeFreshness() {
        if (!props.isEnabled() || !props.getFreshness().isEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now();
        int expired = 0;
        int reminded = 0;
        List<Employee> reviewers = null;
        for (DocState doc : chunkService.latestDocStates()) {
            if (!KnowledgeChunk.STATUS_ACTIVE.equals(doc.status())) {
                continue;
            }
            if (isExpired(doc, today)) {
                chunkService.markStatus(doc.docCode(), KnowledgeChunk.STATUS_EXPIRED);
                // 向量库同步状态：过期文档保留向量但不再参与正常检索，仅用于“已失效提示”；
                // 同步失败不影响正确性——检索层本身按日期硬过滤，同步仅影响提示可发现性
                syncVectorStatus(doc);
                expired++;
                log.warn("知识文档《{}》v{} 已失效（失效日 {}, 复审日 {}），状态置 EXPIRED",
                        doc.docTitle(), doc.version(), doc.effectiveTo(), doc.reviewDueAt());
                continue;
            }
            if (shouldRemind(doc, today)) {
                if (reviewers == null) {
                    reviewers = loadReviewers();
                }
                reminded += remindReview(doc, today, reviewers);
            }
        }
        log.info("知识库时效巡检完成：失效 {} 篇，复审提醒 {} 人次", expired, reminded);
    }

    /** 同步向量库状态，失败仅告警，避免单篇文档异常中断整轮巡检 */
    private void syncVectorStatus(DocState doc) {
        try {
            vectorClient.updateStatusByDocCode(doc.docCode(), KnowledgeChunk.STATUS_EXPIRED);
        } catch (Exception e) {
            log.warn("同步向量库失效状态失败：{} - {}", doc.docCode(), e.getMessage());
        }
    }

    /** 失效判定：超过失效日期，或复审截止日 + 宽限期已过 */
    private boolean isExpired(DocState doc, LocalDate today) {
        if (doc.effectiveTo() != null && today.isAfter(doc.effectiveTo())) {
            return true;
        }
        return doc.reviewDueAt() != null
                && today.isAfter(doc.reviewDueAt().plusDays(props.getFreshness().getGraceDays()));
    }

    /** 复审提醒判定：进入「复审截止日 - 提前提醒天数」窗口 */
    private boolean shouldRemind(DocState doc, LocalDate today) {
        return doc.reviewDueAt() != null
                && !today.isBefore(doc.reviewDueAt().minusDays(props.getFreshness().getReminderDays()));
    }

    private List<Employee> loadReviewers() {
        return employeeService.list(new LambdaQueryWrapper<Employee>()
                .in(Employee::getRole, HR_ROLES)
                .isNotNull(Employee::getEmail));
    }

    /** 逐个提醒 HR 角色；同员工同复审日只提醒一次 */
    private int remindReview(DocState doc, LocalDate today, List<Employee> reviewers) {
        int count = 0;
        for (Employee reviewer : reviewers) {
            LambdaQueryWrapper<ExpireReminder> check = new LambdaQueryWrapper<>();
            check.eq(ExpireReminder::getEmpId, reviewer.getId())
                    .eq(ExpireReminder::getRemindType, REMIND_TYPE)
                    .eq(ExpireReminder::getExpireDate, doc.reviewDueAt());
            if (expireReminderMapper.selectCount(check) > 0) {
                continue;
            }
            ExpireReminder reminder = new ExpireReminder();
            reminder.setEmpId(reviewer.getId());
            reminder.setRemindType(REMIND_TYPE);
            reminder.setExpireDate(doc.reviewDueAt());
            reminder.setRemindDate(today);
            reminder.setSent(1);
            expireReminderMapper.insert(reminder);

            emailService.sendSimpleMail(reviewer.getEmail(),
                    "知识库文档复审提醒",
                    String.format("您好，知识文档《%s》（%s，v%d）的复审截止日为 %s，请及时复审；"
                                    + "逾期超过 %d 天未复审，该文档将从知识库检索中自动失效。",
                            doc.docTitle(), doc.docCode(), doc.version(), doc.reviewDueAt(),
                            props.getFreshness().getGraceDays()));
            count++;
        }
        return count;
    }
}
