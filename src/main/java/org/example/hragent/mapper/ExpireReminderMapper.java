package org.example.hragent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.hragent.entity.ExpireReminder;

/**
 * 到期提醒 Mapper
 */
@Mapper
public interface ExpireReminderMapper extends BaseMapper<ExpireReminder> {
}
