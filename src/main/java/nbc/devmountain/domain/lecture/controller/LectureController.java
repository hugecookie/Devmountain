package nbc.devmountain.domain.lecture.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nbc.devmountain.common.response.ApiResponse;
import nbc.devmountain.domain.lecture.service.EmbeddingService;
import nbc.devmountain.domain.lecture.service.LectureService;

@RestController
@RequiredArgsConstructor
@RequestMapping("lectures")
public class LectureController {

	private final LectureService lectureService;
	private final EmbeddingService embeddingService;

	@GetMapping("inflearn")
	public ResponseEntity<ApiResponse<Void>>getLecture(){
		lectureService.getLecture();
		return ResponseEntity.ok(ApiResponse.success("데이터를 저장에 성공했습니다.", 200));
	}

	@PostMapping("embedding")
	public ResponseEntity<ApiResponse<Void>> embedLecture(){
		embeddingService.embedLecture();
		return ResponseEntity.ok(ApiResponse.success("임베딩에 성공했습니다", 200));
	}



}
