package nbc.devmountain.domain.lecture.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import nbc.devmountain.domain.lecture.client.LectureClient;
import nbc.devmountain.domain.lecture.dto.InflearnResponse;
import nbc.devmountain.domain.lecture.dto.Item;
import nbc.devmountain.domain.lecture.model.Lecture;
import nbc.devmountain.domain.lecture.model.LectureMapper;
import nbc.devmountain.domain.lecture.repository.LectureRepository;

@Service
@RequiredArgsConstructor
public class LectureService {

	private final LectureClient lectureClient;
	private final LectureRepository lectureRepository;
	private final LectureMapper lectureMapper;

	public void getLecture() {

		InflearnResponse firstPage = lectureClient.getLecture(1);

		savePage(firstPage);

		for (int i = 2; i <= firstPage.data().totalPage(); i++) {
			InflearnResponse page = lectureClient.getLecture(i);

			savePage(page);
		}

	}

	/*
	Dto -> Entity -> db 저장
	 */
	private void savePage(InflearnResponse page) {

		for (Item item : page.data().items()) {

		Lecture lecture	= lectureMapper.toEntity(item);

		lectureRepository.save(lecture);

		}

	}
}





