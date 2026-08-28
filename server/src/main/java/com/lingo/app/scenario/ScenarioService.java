package com.lingo.app.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingo.app.common.ApiException;
import com.lingo.app.scenario.mapper.ScenarioLineMapper;
import com.lingo.app.scenario.mapper.ScenarioMapper;
import com.lingo.app.scenario.mapper.ScenarioVocabMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScenarioService {

  private final ScenarioMapper scenarioMapper;
  private final ScenarioLineMapper lineMapper;
  private final ScenarioVocabMapper vocabMapper;

  public List<ScenarioCard> list(String track, long page, long size, Set<Long> practicedIds) {
    LambdaQueryWrapper<ScenarioEntity> qw = new LambdaQueryWrapper<ScenarioEntity>()
        .eq(ScenarioEntity::getStatus, "published")
        // 课程大纲的模板场景只通过课时进入，不进入自由练习大厅
        .ne(ScenarioEntity::getSource, "template")
        .eq(track != null && !track.isBlank(), ScenarioEntity::getTrack, track)
        .orderByAsc(ScenarioEntity::getSortNo)
        .orderByAsc(ScenarioEntity::getId);
    Page<ScenarioEntity> result = scenarioMapper.selectPage(Page.of(page, size), qw);
    return result.getRecords().stream()
        .map(s -> toCard(s, practicedIds.contains(s.getId())))
        .toList();
  }

  public ScenarioDetail detail(Long id) {
    ScenarioEntity s = scenarioMapper.selectById(id);
    if (s == null) {
      throw ApiException.notFound("场景不存在");
    }
    List<LineView> lines = lineMapper.selectList(
            new LambdaQueryWrapper<ScenarioLineEntity>()
                .eq(ScenarioLineEntity::getScenarioId, id)
                .orderByAsc(ScenarioLineEntity::getIdx))
        .stream().map(l -> new LineView(l.getIdx(), l.getSpeaker(), l.getEn(), l.getZh(), l.getAudioUrl()))
        .toList();
    List<VocabView> vocab = vocabMapper.selectList(
            new LambdaQueryWrapper<ScenarioVocabEntity>()
                .eq(ScenarioVocabEntity::getScenarioId, id))
        .stream().map(v -> new VocabView(v.getWord(), v.getPhonetic(), v.getMeaningZh(),
            v.getExampleEn(), v.getExampleZh()))
        .toList();
    return new ScenarioDetail(s.getId(), s.getTrack(), s.getTopic(), s.getTitleZh(), s.getTitleEn(),
        s.getCefr(), s.getIntroZh(), s.getRoleSetting(), lines, vocab);
  }

  /**
   * 推荐今天要学的场景：该轨道下用户还没练过的第一个；都练过则返回第一个。
   *
   * @param practicedIds 用户练过（产生过对话）的场景 id
   */
  public ScenarioCard recommend(String track, Set<Long> practicedIds) {
    LambdaQueryWrapper<ScenarioEntity> qw = new LambdaQueryWrapper<ScenarioEntity>()
        .eq(ScenarioEntity::getStatus, "published")
        .eq(ScenarioEntity::getTrack, track)
        .orderByAsc(ScenarioEntity::getSortNo)
        .orderByAsc(ScenarioEntity::getId);
    List<ScenarioEntity> all = scenarioMapper.selectList(qw);
    if (all.isEmpty()) {
      return null;
    }
    return all.stream()
        .filter(s -> !practicedIds.contains(s.getId()))
        .findFirst()
        .map(s -> toCard(s, false))
        .orElse(toCard(all.get(0), true));
  }

  public ScenarioEntity require(Long id) {
    ScenarioEntity s = scenarioMapper.selectById(id);
    if (s == null) {
      throw ApiException.notFound("场景不存在");
    }
    return s;
  }

  public List<String> aiLines(Long scenarioId) {
    return lineMapper.selectList(new LambdaQueryWrapper<ScenarioLineEntity>()
            .eq(ScenarioLineEntity::getScenarioId, scenarioId)
            .eq(ScenarioLineEntity::getSpeaker, "ai")
            .orderByAsc(ScenarioLineEntity::getIdx))
        .stream().map(ScenarioLineEntity::getEn).toList();
  }

  public List<ScenarioVocabEntity> vocabOf(Long scenarioId) {
    return vocabMapper.selectList(new LambdaQueryWrapper<ScenarioVocabEntity>()
        .eq(ScenarioVocabEntity::getScenarioId, scenarioId));
  }

  private ScenarioCard toCard(ScenarioEntity s, boolean practiced) {
    long lines = lineMapper.selectCount(
        new LambdaQueryWrapper<ScenarioLineEntity>().eq(ScenarioLineEntity::getScenarioId, s.getId()));
    long vocab = vocabMapper.selectCount(
        new LambdaQueryWrapper<ScenarioVocabEntity>().eq(ScenarioVocabEntity::getScenarioId, s.getId()));
    return new ScenarioCard(s.getId(), s.getTrack(), s.getTopic(), s.getTitleZh(), s.getTitleEn(),
        s.getCefr(), s.getIntroZh(), (int) lines, (int) vocab, practiced);
  }

  public record ScenarioCard(Long id, String track, String topic, String titleZh, String titleEn,
                             String cefr, String introZh, int lineCount, int vocabCount,
                             boolean practiced) {
  }

  public record LineView(Integer idx, String speaker, String en, String zh, String audioUrl) {
  }

  public record VocabView(String word, String phonetic, String meaningZh,
                          String exampleEn, String exampleZh) {
  }

  public record ScenarioDetail(Long id, String track, String topic, String titleZh, String titleEn,
                               String cefr, String introZh, String roleSetting,
                               List<LineView> lines, List<VocabView> vocab) {
  }
}
