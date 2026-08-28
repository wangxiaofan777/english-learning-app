package com.lingo.app.vocab;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingo.app.common.ApiException;
import com.lingo.app.study.StudyService;
import com.lingo.app.vocab.mapper.ReviewLogMapper;
import com.lingo.app.vocab.mapper.VocabEntryMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VocabService {

  private final VocabEntryMapper vocabMapper;
  private final ReviewLogMapper reviewLogMapper;
  private final StudyService studyService;

  public VocabEntryEntity add(Long userId, String word, String phonetic, String meaningZh,
                              String exampleEn, String exampleZh, String source,
                              Long scenarioId) {
    String normalized = word.trim();
    VocabEntryEntity existing = vocabMapper.selectOne(
        new LambdaQueryWrapper<VocabEntryEntity>()
            .eq(VocabEntryEntity::getUserId, userId)
            .eq(VocabEntryEntity::getWord, normalized));
    if (existing != null) {
      if (meaningZh != null && !meaningZh.isBlank()
          && (existing.getMeaningZh() == null || existing.getMeaningZh().isBlank())) {
        existing.setMeaningZh(meaningZh);
        vocabMapper.updateById(existing);
      }
      return existing;
    }
    VocabEntryEntity entry = new VocabEntryEntity();
    entry.setUserId(userId);
    entry.setWord(normalized);
    entry.setPhonetic(phonetic);
    entry.setMeaningZh(meaningZh);
    entry.setExampleEn(exampleEn);
    entry.setExampleZh(exampleZh);
    entry.setSource(source == null ? "manual" : source);
    entry.setScenarioId(scenarioId);
    entry.setFsrsState("new");
    entry.setFsrsReps(0);
    entry.setFsrsLapses(0);
    entry.setDueAt(LocalDateTime.now());
    try {
      vocabMapper.insert(entry);
    } catch (DuplicateKeyException e) {
      return vocabMapper.selectOne(new LambdaQueryWrapper<VocabEntryEntity>()
          .eq(VocabEntryEntity::getUserId, userId)
          .eq(VocabEntryEntity::getWord, normalized));
    }
    return entry;
  }

  public List<VocabEntryEntity> list(Long userId, long page, long size) {
    return vocabMapper.selectPage(Page.of(page, size),
            new LambdaQueryWrapper<VocabEntryEntity>()
                .eq(VocabEntryEntity::getUserId, userId)
                .orderByDesc(VocabEntryEntity::getCreatedAt))
        .getRecords();
  }

  public List<VocabEntryEntity> queue(Long userId, int limit) {
    return vocabMapper.selectList(new LambdaQueryWrapper<VocabEntryEntity>()
        .eq(VocabEntryEntity::getUserId, userId)
        .le(VocabEntryEntity::getDueAt, LocalDateTime.now())
        .orderByAsc(VocabEntryEntity::getDueAt)
        .last("limit " + Math.max(1, Math.min(limit, 50))));
  }

  public long dueCount(Long userId) {
    return vocabMapper.selectCount(new LambdaQueryWrapper<VocabEntryEntity>()
        .eq(VocabEntryEntity::getUserId, userId)
        .le(VocabEntryEntity::getDueAt, LocalDateTime.now()));
  }

  public ReviewResult grade(Long userId, Long vocabId, int rating) {
    VocabEntryEntity entry = vocabMapper.selectById(vocabId);
    if (entry == null || !entry.getUserId().equals(userId)) {
      throw ApiException.notFound("生词不存在");
    }
    FsrsScheduler.Card card = new FsrsScheduler.Card(entry.getFsrsState(),
        entry.getFsrsStability() == null ? 0 : entry.getFsrsStability(),
        entry.getFsrsDifficulty() == null ? 0 : entry.getFsrsDifficulty(),
        entry.getFsrsReps() == null ? 0 : entry.getFsrsReps(),
        entry.getFsrsLapses() == null ? 0 : entry.getFsrsLapses());

    LocalDateTime now = LocalDateTime.now();
    FsrsScheduler.Result result = FsrsScheduler.review(card, rating, now, entry.getLastReviewAt());

    entry.setFsrsState(result.card().state());
    entry.setFsrsStability(result.card().stability());
    entry.setFsrsDifficulty(result.card().difficulty());
    entry.setFsrsReps(result.card().reps());
    entry.setFsrsLapses(result.card().lapses());
    entry.setDueAt(result.dueAt());
    entry.setLastReviewAt(now);
    vocabMapper.updateById(entry);

    ReviewLogEntity log = new ReviewLogEntity();
    log.setUserId(userId);
    log.setVocabId(vocabId);
    log.setRating(rating);
    log.setStability(result.card().stability());
    log.setDifficulty(result.card().difficulty());
    log.setReviewedAt(now);
    reviewLogMapper.insert(log);

    studyService.record(userId, "review", 0, 1);
    return new ReviewResult(entry.getId(), entry.getWord(), result.dueAt());
  }

  public record ReviewResult(Long id, String word, LocalDateTime nextDueAt) {
  }
}
