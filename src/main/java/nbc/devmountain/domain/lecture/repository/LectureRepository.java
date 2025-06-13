package nbc.devmountain.domain.lecture.repository;


import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import nbc.devmountain.domain.lecture.model.Lecture;
@Repository
public interface LectureRepository extends JpaRepository<Lecture, Long> {

	@Query("SELECT l FROM Lecture l WHERE l.title LIKE %:keyword% OR l.description LIKE %:keyword%")
	List<Lecture> findRelevantLectures(@Param("keyword") String keyword);

	List<Lecture> findByLectureEmbeddingIsNull();

	void deleteByCrawledAtBefore(LocalDateTime today);

}
