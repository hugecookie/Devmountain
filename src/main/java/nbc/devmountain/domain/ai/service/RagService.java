package nbc.devmountain.domain.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import nbc.devmountain.domain.lecture.model.Lecture;
import nbc.devmountain.domain.lecture.model.LectureSkillTag;
import nbc.devmountain.domain.lecture.model.SkillTag;
import nbc.devmountain.domain.lecture.repository.LectureRepository;
import nbc.devmountain.domain.lecture.repository.LectureSkillTagRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

	private final VectorStore vectorStore;
	private final LectureRepository lectureRepository;
	private final JdbcTemplate jdbcTemplate;
	private final LectureSkillTagRepository lectureSkillTagRepository;

	/**
	 * Lecture DB의 강의들을 벡터 DB에 저장하는 메서드
	 * DB에서 모든 강의를 꺼냄
	 * 각 강의를 벡터로 바꾼 후 Document 처럼 만듬
	 * vectorStore에 저장
	 */
/*	public void saveEmbeddedLecturesToVectorStore() {
		List<Lecture> embeddedLectures = lectureRepository.findAll();

		if (embeddedLectures.isEmpty()) {
			log.warn("저장할 강의가 없습니다.");
			return;
		}
		try {
			clearVectorStore();
			List<Document> documents = new ArrayList<>();
			int successCount = 0;
			int failCount = 0;

			for (Lecture lecture : embeddedLectures) {
				try {
					Document document = convertLectureToDocument(lecture);
					documents.add(document);
					successCount++;
				} catch (Exception e) {
					log.error("Document 변환 실패: lectureId={}, error={}", lecture.getLectureId(), e.getMessage());
					failCount++;
				}
			}
			//한 번에 벡터 스토어에 저장
			if (!documents.isEmpty()) {
				vectorStore.add(documents);
				log.info("벡터 스토어 재구축 완료 - 성공: {}개, 실패: {}개", successCount, failCount);
			} else {
				log.warn("저장할 Document가 없습니다.");
			}

		} catch (Exception e) {
			log.error("벡터 스토어 재구축 실패: {}", e.getMessage(), e);
			throw new RuntimeException("벡터 스토어 재구축 실패", e);
		}
	}

	private void clearVectorStore() {
		try {//벡터스토어 초기화
			jdbcTemplate.execute("TRUNCATE TABLE vector_store");
			log.info("vector_store 테이블 초기화 완료");
		} catch (Exception e) {
			log.error("vector_store 테이블 초기화 실패: {}", e.getMessage());
			throw e;
		}
	}

	*//**
	 * @param lecture 정보의 title,instructor,description,tag 등으로 임베딩 값 생성
	 * *//*
	private Document convertLectureToDocument(Lecture lecture) {
		String tags = "";
		try {
			tags = lectureSkillTagRepository.findByLecture(lecture).stream()
				.map(LectureSkillTag::getSkillTag)
				.map(SkillTag::getTitle)
				.collect(Collectors.joining(","));
		} catch (Exception e) {
			log.debug("스킬 태그 로드 실패: lectureId={}", lecture.getLectureId());
		}

		// Document  content에 담을 필드값 매핑
		String content = String.format(
			"제목: %s\n강사: %s\n설명: %s\n기술 태그: %s",
			lecture.getTitle() != null ? lecture.getTitle() : "",
			lecture.getInstructor() != null ? lecture.getInstructor() : "",
			lecture.getDescription() != null ? lecture.getDescription() : "",
			tags
		);

		Map<String, Object> metadata = Map.of(
			//메타데이터가 강의 ID만 가지고 있도록 수정
			"lectureId", lecture.getLectureId()
		);
		String id = UUID.randomUUID().toString();
		return new Document(id, content, metadata);
	}*/
	/**
	 * 유사한 강의를 검색하는 메서드
	 * @param query 검색 쿼리
	 * @return 유사한 강의 리스트
	 */
	public List<Lecture> searchSimilarLectures(String query) {
		try {
			SearchRequest searchRequest = SearchRequest.query(query)
				.withTopK(3)
				.withSimilarityThreshold(0.7);

			List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);
			log.info("검색 쿼리: {}", query);
			log.info("검색된 문서 수: {}", similarDocuments.size());

			return similarDocuments.stream()
				.map(doc -> {
					Long lectureId = Long.valueOf(doc.getMetadata().get("lectureId").toString());
					return lectureRepository.findById(lectureId).orElse(null);
				})
				.filter(Objects::nonNull)
				.toList();
		} catch (Exception e) {
			log.error("벡터 검색 실패: {}", e.getMessage(), e);
			return fallbackSearch(query);
		}
	}

	/**
	 * 벡터 검색 실패시 대체실행
	 * */
	private List<Lecture> fallbackSearch(String query) {
		log.info("키워드로 대체 검색 : {}", query);
		return lectureRepository.findTop5ByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
	}
}