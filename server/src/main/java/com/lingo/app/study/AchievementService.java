package com.lingo.app.study;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingo.app.course.LessonProgressEntity;
import com.lingo.app.course.mapper.LessonProgressMapper;
import com.lingo.app.user.UserProfileEntity;
import com.lingo.app.user.mapper.UserProfileMapper;
import com.lingo.app.vocab.VocabEntryEntity;
import com.lingo.app.vocab.mapper.VocabEntryMapper;
import com.lingo.app.study.mapper.StudyLogMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 成就徽章：全部由学习数据实时计算，不需要额外状态存储。
 */
@Service
@RequiredArgsConstructor
public class AchievementService {

  private final StudyLogMapper studyLogMapper;
  private final UserProfileMapper profileMapper;
  private final VocabEntryMapper vocabMapper;
  private final LessonProgressMapper progressMapper;

  public List<Badge> badges(Long userId) {
    UserProfileEntity profile = profileMapper.selectOne(
        new LambdaQueryWrapper<UserProfileEntity>().eq(UserProfileEntity::getUserId, userId));
    int streak = profile == null || profile.getStreakDays() == null ? 0 : profile.getStreakDays();
    int xp = profile == null || profile.getXp() == null ? 0 : profile.getXp();

    long vocabCount = vocabMapper.selectCount(new LambdaQueryWrapper<VocabEntryEntity>()
        .eq(VocabEntryEntity::getUserId, userId));
    long lessonDone = progressMapper.selectCount(new LambdaQueryWrapper<LessonProgressEntity>()
        .eq(LessonProgressEntity::getUserId, userId));
    Integer maxShadowScore = progressMapper.selectList(new LambdaQueryWrapper<LessonProgressEntity>()
            .eq(LessonProgressEntity::getUserId, userId)
            .isNotNull(LessonProgressEntity::getScore))
        .stream().mapToInt(LessonProgressEntity::getScore).max().orElse(0);
    int listenMinutes = kindMinutes(userId, "listening");
    int speakMinutes = kindMinutes(userId, "shadowing");

    List<Badge> badges = new ArrayList<>();
    badges.add(new Badge("first_lesson", "初出茅庐", "完成第一节课时", "🌱", lessonDone > 0));
    badges.add(new Badge("streak_3", "三日坚持", "连续学习 3 天", "🔥", streak >= 3));
    badges.add(new Badge("streak_7", "七日之约", "连续学习 7 天", "⚡", streak >= 7));
    badges.add(new Badge("streak_30", "月度恒心", "连续学习 30 天", "🏆", streak >= 30));
    badges.add(new Badge("vocab_30", "词汇收藏家", "收藏 30 个生词", "📚", vocabCount >= 30));
    badges.add(new Badge("vocab_100", "词库大师", "收藏 100 个生词", "🎓", vocabCount >= 100));
    badges.add(new Badge("listen_30", "金耳朵", "累计精听 30 分钟", "👂", listenMinutes >= 30));
    badges.add(new Badge("speak_25", "开口达人", "累计跟读 25 分钟", "🗣️", speakMinutes >= 25));
    badges.add(new Badge("shadow_90", "满分跟读", "跟读评分达到 90 分", "💯", maxShadowScore >= 90));
    badges.add(new Badge("xp_500", "小有所成", "累计获得 500 经验", "⭐", xp >= 500));
    badges.add(new Badge("xp_2000", "英语老手", "累计获得 2000 经验", "🌟", xp >= 2000));
    return badges;
  }

  private int kindMinutes(Long userId, String kind) {
    return studyLogMapper.selectList(new LambdaQueryWrapper<StudyLogEntity>()
            .eq(StudyLogEntity::getUserId, userId)
            .eq(StudyLogEntity::getKind, kind))
        .stream().mapToInt(r -> r.getMinutes() == null ? 0 : r.getMinutes()).sum();
  }

  public record Badge(String code, String name, String description, String icon, boolean earned) {
  }
}
