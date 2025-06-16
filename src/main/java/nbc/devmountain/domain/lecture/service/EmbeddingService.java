package nbc.devmountain.domain.lecture.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbc.devmountain.domain.lecture.model.Lecture;
import nbc.devmountain.domain.lecture.model.LectureSkillTag;
import nbc.devmountain.domain.lecture.model.SkillTag;
import nbc.devmountain.domain.lecture.repository.LectureRepository;
import nbc.devmountain.domain.lecture.repository.LectureSkillTagRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingService {
	private final LectureRepository lectureRepository;
	private final LectureSkillTagRepository lectureSkillTagRepository;
	private final EmbeddingModel embeddingModel;
	private final VectorStore vectorStore;
	private final JdbcTemplate jdbcTemplate;

	public void embedLecture() {
		log.info("embeddingModel class: {}", embeddingModel.getClass().getName());
		//벡터스토어 초기화
		clearVectorStore();

		List<Lecture> lectureList = lectureRepository.findAll();
		log.info("저장 대상 강의 수: {}", lectureList.size());

		if (lectureList.isEmpty())
			return;

		int batchSize = 500;
		int totalBatches = (int)Math.ceil((double)lectureList.size() / batchSize);
		int delayMs = 5000;

		for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
			int startIndex = batchIndex * batchSize;
			int endIndex = Math.min(startIndex + batchSize, lectureList.size());

			log.info("배치 {}/{} 처리 시작", batchIndex + 1, totalBatches);
			List<Document> documents = new ArrayList<>();

			for (int i = startIndex; i < endIndex; i++) {
				Lecture lecture = lectureList.get(i);

				try {
					String tag = lectureSkillTagRepository.findByLecture(lecture).stream()
						.map(LectureSkillTag::getSkillTag)
						.map(SkillTag::getTitle)
						.collect(Collectors.joining(","));

					String content = """
						제목: %s
						강사: %s
						설명: %s
						기술 태그: %s
						""".formatted(lecture.getTitle(), lecture.getInstructor(), lecture.getDescription(), tag);

					Map<String, Object> metadata = Map.of(
						"lectureId", lecture.getLectureId()
					);

					Document document = new Document(UUID.randomUUID().toString(), content, metadata);

					documents.add(document);
				} catch (Exception e) {
					log.warn("임베딩 실패 (lectureId: {}): {}", lecture.getLectureId(), e.getMessage());
				}

				if ((i + 1) % 50 == 0) {
					log.info("진행률 {}/{}", i + 1, lectureList.size());
				}
			}

			try {
				vectorStore.add(documents);
				log.info("벡터 스토어 저장 완료: {}개", documents.size());
			} catch (Exception e) {
				log.error("벡터 스토어 저장 실패 (배치 {}): {}", batchIndex + 1, e.getMessage());
			}

			if (batchIndex < totalBatches - 1) {
				try {
					Thread.sleep(delayMs);
				} catch (Exception e) {
					log.error("딜레이 초과로 실패 {}: {}", batchIndex + 1, e.getMessage());
					throw new RuntimeException(e.getMessage());
				}
			}
		}
	}

	/**
	 * 벡터 스토어를 초기화하는 메서드
	 */
	private void clearVectorStore() {
		try {
			jdbcTemplate.execute("TRUNCATE TABLE vector_store");
			log.info("vector_store 테이블 초기화 완료");
		} catch (Exception e) {
			log.error("vector_store 테이블 초기화 실패: {}", e.getMessage());
			throw new RuntimeException("벡터 스토어 초기화 실패", e);
		}
	}
}