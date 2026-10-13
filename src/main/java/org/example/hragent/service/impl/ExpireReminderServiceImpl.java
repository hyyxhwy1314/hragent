package org.example.hragent.service.impl;

import org.example.hragent.entity.ExpireReminder;
import org.example.hragent.mapper.ExpireReminderMapper;
import org.example.hragent.service.ExpireReminderService;
import org.springframework.stereotype.Service;

/**
 * 到期提醒 Service 实现
 */
@Service
public class ExpireReminderServiceImpl extends BaseServiceImpl<ExpireReminderMapper, ExpireReminder>
        implements ExpireReminderService {
}
