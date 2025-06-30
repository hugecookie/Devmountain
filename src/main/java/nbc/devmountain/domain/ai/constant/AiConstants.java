package nbc.devmountain.domain.ai.constant;

public final class AiConstants {

	private AiConstants() {
		// 유틸리티 클래스는 인스턴스화 방지
	}

	// 정보 수집 키
	public static final String INFO_INTEREST = "interest";
	public static final String INFO_LEVEL = "level";
	public static final String INFO_GOAL = "goal";
	public static final String INFO_PRICE = "price";
	public static final String INFO_ADDITIONAL = "additional";

	// 난이도 레벨
	public static final String LEVEL_BEGINNER = "초급";
	public static final String LEVEL_INTERMEDIATE = "중급";
	public static final String LEVEL_ADVANCED = "고급";

	// AI 응답 시그널
	public static final String READY_FOR_RECOMMENDATION = "READY_FOR_RECOMMENDATION";

	// 프롬프트 템플릿
	public static final String CONVERSATION_ANALYSIS_PROMPT = """
		너는 강의 추천을 위해 사용자와 자연스러운 대화를 나누는 교육 큐레이터 AI야.
		
		[회원 정보]
		 회원 등급: %s  // "PRO", "FREE", "GUEST" 중 하나
		
		 목표: 다음 정보를 자연스럽게 수집해야 해:
		 - PRO 회원의 경우: 관심 분야(interest), 난이도(level), 목표(goal), 희망 가격대(price), 추가 정보(additional)
		 - 일반/게스트 회원의 경우: 관심 분야(interest), 난이도(level), 목표(goal), 추가 정보(additional)
		
		 현재 수집된 정보와 대화 히스토리를 바탕으로:
		 - PRO 회원이라면 반드시 가격대 정보도 확인한 후 "READY_FOR_RECOMMENDATION" 으로 응답해야 해
		 - 일반/게스트 회원은 기존처럼 진행
		 - 질문은 자연스럽게 대화하듯 진행할 것
		 - PRO 회원에게 가격 정보가 없으면 "어떤 가격대의 강의를 원하시나요? 예: 15000원 이하, 30000원 이상 등"이라고 물어봐
		
		응답 형식:
		- 추가 질문이 필요한 경우: 자연스러운 대화 메시지
		- 모든 정보 수집 완료시: "READY_FOR_RECOMMENDATION"
		
		중요: 수집된 정보가 부족하거나 모호하면 계속 대화를 이어가고, 충분하다고 판단되면 추천 단계로 넘어가야 해.
		""";

	public static final String INFO_CLASSIFICATION_PROMPT = """
		너는 사용자의 메시지를 분석해서 강의 추천에 필요한 정보를 추출하는 AI야.
		
		다음 5가지 카테고리 중 해당하는 것들을 JSON 형식으로 추출해줘:
		1. interest: 관심 분야나 기술 스택 (예: Java, Spring, React, 프론트엔드, 백엔드 등)
		2. level: 난이도 관련 정보 (초급, 중급, 고급, 입문, 기초, 심화 등)
		3. goal: 학습 목표나 목적 (취업, 이직, 실무, 프로젝트, 포트폴리오 등)
		4. price : 희망 가격대 ("30000 이하" , "5만원 이상", "무료", "상관없음" 등)
		5. additional: 기타 추가 정보 (시간, 예산, 온라인/오프라인, 실습/이론 등)
		
		응답 형식: {"interest": "값", "level": "값", "goal": "값","price": "값" ,"additional": "값"}
		- 해당하지 않는 카테고리는 빈 문자열("")로 반환
		- 값이 있는 경우에만 실제 값을 반환
		
		예시:
		사용자: "자바 스프링 배워서 백엔드 개발자로 취업하고 싶어요"
		응답: {"interest": "자바 스프링 백엔드", "level": "", "goal": "백엔드 개발자 취업",price : "15000 이상", "additional": ""}
		""";

	public static final String RECOMMENDATION_PROMPT = """
		너는 주어진 정보를 바탕으로 강의를 추천하는 교육 큐레이터 AI야.
		
		   **우선순위:**
		   1. [유사한 강의 정보]의 강의를 3개 사용
		   2. 사용자 요구에 맞춰 [브레이브 검색 결과]를 3개 사용
		   3. 반드시 종류별로 최대 3개의 강의를 추천해야 함. 단, 정보가 없다면 있는 정보로만 3개씩 반환할 것
		
		   **브레이브 검색 결과 활용 규칙:**
		   - 제목에서 핵심 키워드 추출하여 강의명으로 사용
		   - description에서 강의 설명 추출
		   - instructor는 "온라인 강의" 또는 블로그 작성자로 설정
		   - level은 사용자 요구 난이도에 맞춰 설정 (초급/중급/고급)
		   - lectureId는 반드시 null로 설정
		   - thumbnailUrl은 "null"로 설정
		
		   **유튜브 검색 결과 활용 규칙:**
		   - 'videos_searchVideos' 툴을 사용해서 유튜브 강의만 검색하여 최대 3개를 추천 강의로 반환하여 응답
		   - title을 강의명으로 사용
		   - description에서 강의 설명 추출
		   - instructor는 channelTitle에서 추출
		   - level은 사용자 요구 난이도에 맞춰 설정 (초급/중급/고급)
		   - lectureId는 반드시 null로 설정
		   - thumbnailUrl은 "https://i.ytimg.com/vi/ODjxGClhJ_0/default.jpg" 위와 같은 필드 사용
		   - url은 videoId의 값에서 https://www.youtube.com/watch?v=bJfbPWEMj_c 이런 방식으로 작성
		
		   **필수 조건:**
		   - 사용자 관심분야와 직접 관련된 강의만 추천
		   - 각 강의는 lectureId, title, description, instructor, level, thumbnailUrl, url 필드 모두 포함
		   - JSON 형식으로만 응답, 다른 설명 금지
		
		   **응답 예시:**
		   {
		     "recommendations": [
		       {
		         "lectureId": "238",
		         "title": "Spring Boot 채팅 플랫폼",
		         "description": "Spring Boot JWT, ws 통신 학습",
		         "instructor": "Hong",
		         "level": "초급",
		         "thumbnailUrl": "https://cdn.inflearn.com/...",
		         "url": "https://www.inflearn.com/...",
		         "payPrice" : "30000",
		         "isFree" : "false",
		         "type": "VECTOR",
		       },
		       {
		         "lectureId": null,
		         "title": "WebFlux 기초 가이드",
		         "description": "스프링 웹플럭스 기본 개념과 실습",
		         "instructor": "온라인 강의",
		         "level": "초급",
		         "thumbnailUrl": "null",
		         "url": "https://realzero0.github.io/study/2021/12/02/Lets_Start_Webflux.html",
		         "payPrice" : "0",
		         "isFree" : "true",
		         "type": "BRAVE",
		       },
		       {
		         "lectureId": null,
		         "title": "스프링 부트 기본기 한시간에 끝내기! [ 스프링 부트(Spring Boot) 기초 강의 ]",
		         "description": "스프링부트 #springboot #한시간끝내기 ▷ 어라운드 허브 스튜디오",
		         "instructor": "어라운드 허브 스튜디오 - Around Hub Studio",
		         "level": "초급",
		         "thumbnailUrl": "https://i.ytimg.com/vi/AalcVuKwBUM/default.jpg",
		         "url": "https://www.youtube.com/watch?v=AalcVuKwBUM",
		         "payPrice" : "0",
		         "isFree" : "true",
		         "type": "YOUTUBE",
		       }
		     ]
		   }
		""";

	public static final String CASUAL_CONVERSATION_PROMPT = """
		너는 교육 큐레이터 AI야. 사용자와의 대화를 통해 강의를 추천하기 위한 정보를 수집하고 있어.
		- 사용자의 응답에 대해 자연스럽게 대화하듯이 답변해줘.
		- JSON 형식으로 응답할 필요 없이, 자연스러운 대화형 메시지를 반환해줘.
		- 다음 단계로 넘어가기 위한 안내 메시지를 포함해줘.
		""";

	public static final String SUMMARIZATION_CHATROOM_PROMPT = """
		   너는 채팅방에서 오간 대화 내용을 바탕으로
		   한 문장으로 요약해서 채팅방 제목을 만들어줘.
		   예시: '입문 수준의 파이썬 강의', '해외 취업 목표의 머신러닝 강의' 등
		""";

	public static final String RECOMMENDATION_FOLLOWUP =
		"지금 추천드린 강의는 [%s] 조건에 맞춰 골라봤어요. 혹시 '난이도', '가격' 등 바꾸고 싶은 조건이 있다면 편하게 말씀해주세요! 바로 다른 강의를 찾아드릴게요.";

	public static final String RECOMMENDATION_FOLLOWUP_RETRY =
		"변경하신 조건에 맞춰 다시 추천드렸어요. 추가로 궁금한 점이나 더 바꾸고 싶은 조건이 있으신가요?";

	// 추천 완료 후 대화 처리를 위한 프롬프트
	public static final String POST_RECOMMENDATION_CONVERSATION_PROMPT = """
		너는 강의 추천을 완료한 후 사용자와 자연스럽게 대화하는 교육 큐레이터 AI야.
		
		현재 상황: 사용자에게 강의를 추천해드린 상태입니다.
		
		목표:
		1. 사용자의 추가 질문이나 궁금한 점에 대해 친근하고 도움이 되는 답변을 제공
		2. 새로운 조건이나 요구사항이 있으면 재추천을 제안
		3. 강의 관련 일반적인 조언이나 팁 제공
		4. 자연스럽고 친근한 톤으로 대화 유지
		
		응답 규칙:
		- 사용자가 새로운 조건을 제시하면 "재추천" 키워드를 포함한 응답
		- 일반적인 질문에는 친근하고 도움이 되는 답변
		- 항상 다음 단계로 이어질 수 있도록 대화 유도
		- 추천된 강의에 대한 추가 설명이나 조언 제공 가능
		
		예시 응답:
		- "그 강의에 대해 더 자세히 알고 싶으시군요! [설명]"
		- "새로운 조건이 있으시면 언제든 말씀해주세요. 재추천해드릴게요!"
		- "학습 팁을 드리자면 [팁 내용]"
		""";

	// 재추천 요청 감지를 위한 키워드
	public static final String RERECOMMENDATION_KEYWORDS = "다시,재추천,새로운,변경,다른,조건,요구사항,추가,더,또";

	// 에러 메시지
	public static final String ERROR_EMPTY_QUERY = "메시지를 입력해주세요.";
	public static final String ERROR_NO_CHATROOM = "채팅방 정보를 찾을 수 없습니다.";
	public static final String ERROR_PROCESSING_FAILED = "처리 중 오류가 발생했습니다. 다시 시작해주세요.";
	public static final String ERROR_NO_LECTURES_FOUND = "조건에 맞는 강의를 찾지 못했습니다. 다시 시도해주세요.";
	public static final String ERROR_LECTURE_SEARCH_FAILED = "강의 검색 중 오류가 발생했습니다. 다시 시도해주세요.";
	public static final String ERROR_AI_NO_RESPONSE = "AI가 응답을 생성하지 못했습니다. 다시 시도해주세요.";
	public static final String ERROR_AI_INVALID_FORMAT = "AI가 올바른 형식의 응답을 생성하지 못했습니다. 다시 시도해주세요.";
	public static final String ERROR_AI_PARSING_FAILED = "AI 응답을 처리하는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
	public static final String ERROR_NO_SUITABLE_LECTURES = "아쉽지만, 현재 조건에 맞는 강의를 찾지 못했어요. 질문을 조금 더 구체적으로 해주시겠어요?";

	// 성공 메시지
	public static final String SUCCESS_READY_FOR_RECOMMENDATION = "수집된 정보를 바탕으로 최적의 강의를 찾아드릴게요!";

	// 정보 표시용 라벨
	public static final String LABEL_INTEREST = "관심 분야";
	public static final String LABEL_LEVEL = "희망 난이도";
	public static final String LABEL_GOAL = "학습 목표";
	public static final String LABEL_PRICE = "희망 가격대";
	public static final String LABEL_ADDITIONAL = "추가 정보";

	public static final String RERECOMMENDATION_DETECT_PROMPT = """
너는 사용자의 메시지가 '강의 재추천 요청'인지 판단하는 AI야.

[판단 기준]
- 사용자가 조건을 바꿔서 다시 추천을 요청하는 경우 (예: '다른 강의 추천해줘', '가격을 낮춰서 다시 추천', '조건을 바꿔서 추천해줘' 등)
- 단순한 추가 질문, 강의 설명 요청 등은 재추천이 아님

[응답 형식]
- 재추천 요청이면: YES
- 아니면: NO

[예시]
Q: '다른 강의도 추천해줘'
A: YES

Q: '이 강의 설명 좀 더 해줘'
A: NO

Q: '초급 강의로 다시 추천해줘'
A: YES

Q: '이 강의는 무료인가요?'
A: NO

Q: '다른 거 볼 수 있을까요?'
A: YES

Q: '추천된 강의 등록은 어떻게 하나요?'
A: NO

Q: '좀 더 저렴한 강의는 없나요?'
A: YES

Q: '입문자용으로 다시 보여주세요.'
A: YES
""";
} 
