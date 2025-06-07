package nbc.devmountain.domain.lecture.client;

import java.net.URI;
import java.rmi.ServerException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import nbc.devmountain.domain.lecture.dto.InflearnResponse;

@Component
@RequiredArgsConstructor
public class LectureClient {

	private final RestTemplate restTemplate;

	/*
	예외 처리는 나중에 수정 예정
	 */
	public InflearnResponse getLecture(int pageNumber) {
		ResponseEntity<InflearnResponse> responseEntity = restTemplate.getForEntity(getInflearnUri(pageNumber),
			InflearnResponse.class);

		if (!HttpStatus.OK.equals(responseEntity.getStatusCode())) {
			throw new RuntimeException("인프런 강의 데이터를 가져오는데 실패했습니다. 상태 코드:" + responseEntity.getStatusCode());
		}

		return responseEntity.getBody();
	}


	/*
	api 요청할 url 빌더로 만듬
	 */

	private URI getInflearnUri(int pageNumber) {
		return UriComponentsBuilder
			.fromUriString("https://course-api.inflearn.com")
			.path("/client/api/v1/course/search")
			.queryParam("categories", "")
			.queryParam("isBot", false)
			.queryParam("isDiscounted", false)
			.queryParam("isEarlybirdDiscounted", false)
			.queryParam("keyword", "")
			.queryParam("lang", "ko")
			.queryParam("pageNumber", pageNumber)
			.queryParam("pageSize", 40)
			.queryParam("sort", "RECOMMEND")
			.queryParam("types", "ONLINE")
			.encode()
			.build()
			.toUri();
	}

}
