package nbc.devmountain.domain.ai.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import nbc.devmountain.domain.ai.constant.AiConstants;
import nbc.devmountain.domain.chat.dto.ChatMessageResponse;
import nbc.devmountain.domain.chat.model.MessageType;
import nbc.devmountain.domain.chat.repository.ChatRoomRepository;
import nbc.devmountain.domain.chat.service.ChatRoomService;
import nbc.devmountain.domain.lecture.model.Lecture;
import nbc.devmountain.domain.search.dto.BraveSearchResponseDto;
import nbc.devmountain.domain.search.sevice.BraveSearchService;
import nbc.devmountain.domain.user.model.User;

@Slf4j
@Service
@RequiredArgsConstructor
public class LectureRecommendationService {
	private final RagService ragService;
	private final AiService aiService;
	private final BraveSearchService braveSearchService;
	private final CacheService cacheService;
	private final ChatRoomService chatRoomService;
	private final ChatRoomRepository chatRoomRepository;

	// 대화 히스토리를 저장 (chatRoomId -> 대화 내용들)
	private final Map<Long, StringBuilder> conversationHistory = new ConcurrentHashMap<>();
	// 수집된 정보 저장 (chatRoomId -> 수집된 정보)
	private final Map<Long, Map<String, String>> collectedInfo = new ConcurrentHashMap<>();
	// 추천 완료 상태 추적 (chatRoomId -> 추천 완료 여부)
	private final Map<Long, Boolean> recommendationCompleted = new ConcurrentHashMap<>();
	// 마지막 추천 정보 저장 (chatRoomId -> 마지막 추천 조건)
	private final Map<Long, String> lastRecommendationCriteria = new ConcurrentHashMap<>();

	public ChatMessageResponse recommendationResponse(String query, User.MembershipLevel membershipLevel,
		Long chatRoomId, WebSocketSession session) {
		if (query == null || query.trim().isEmpty()) {
			log.warn("빈 쿼리 수신: chatRoomId={}", chatRoomId);
			return createErrorResponse(AiConstants.ERROR_EMPTY_QUERY);
		}

		if (chatRoomId == null) {
			log.error("chatRoomId가 null 입니다.");
			return createErrorResponse(AiConstants.ERROR_NO_CHATROOM);
		}

		try {
			return processConversation(query, chatRoomId, membershipLevel, session);
		} catch (Exception e) {
			log.error("강의 추천 처리 중 오류 발생: chatRoomId={}, error={}", chatRoomId, e.getMessage(), e);
			resetChatState(chatRoomId);
			return createErrorResponse(AiConstants.ERROR_PROCESSING_FAILED);
		}
	}

	private ChatMessageResponse processConversation(String userMessage, Long chatRoomId,
		User.MembershipLevel membershipLevel, WebSocketSession session) {
		// 대화 히스토리 업데이트
		StringBuilder history = conversationHistory.computeIfAbsent(chatRoomId, k -> new StringBuilder());
		history.append("사용자: ").append(userMessage).append("\n");

		// 수집된 정보 맵 초기화
		Map<String, String> info = collectedInfo.computeIfAbsent(chatRoomId, k -> new HashMap<>());

		// 첫 번째 메시지인 경우
		if (history.toString().trim().equals("사용자: " + userMessage)) {
			return handleFirstConversation(userMessage, chatRoomId, membershipLevel, session);
		}

		// 추천 완료 후 대화인지 확인
		Boolean isRecommendationCompleted = recommendationCompleted.get(chatRoomId);
		if (Boolean.TRUE.equals(isRecommendationCompleted)) {
			return handlePostRecommendationConversation(userMessage, chatRoomId, membershipLevel, session, history,
				info);
		}

		// AI에게 대화 분석 및 다음 단계 결정 요청
		ChatMessageResponse analysisResponse = aiService.analyzeConversationAndDecideNext(history.toString(), info,
			userMessage, membershipLevel, session, chatRoomId);

		// AI 응답을 히스토리에 추가
		if (analysisResponse.getMessage() != null) {
			history.append("AI: ").append(analysisResponse.getMessage()).append("\n");
		}

		// 충분한 정보가 수집되었는지 확인
		if (analysisResponse.getMessageType() == MessageType.RECOMMENDATION) {
			// 최종 추천 단계 - RAG 검색 및 추천 생성
			return generateFinalRecommendation(info, chatRoomId, membershipLevel);
		}

		return analysisResponse;
	}

	private ChatMessageResponse handleFirstConversation(String userMessage, Long chatRoomId,
		User.MembershipLevel membershipLevel, WebSocketSession session) {
		// 첫 대화에서도 AI가 자연스럽게 응답하도록 처리
		Map<String, String> emptyInfo = new HashMap<>();
		ChatMessageResponse response = aiService.analyzeConversationAndDecideNext("사용자: " + userMessage + "\n",
			emptyInfo, userMessage, membershipLevel, session, chatRoomId);

		// AI 응답을 히스토리에 추가
		if (response.getMessage() != null) {
			conversationHistory.get(chatRoomId).append("AI: ").append(response.getMessage()).append("\n");
		}

		return response;
	}

	private ChatMessageResponse handlePostRecommendationConversation(String userMessage, Long chatRoomId,
		User.MembershipLevel membershipLevel, WebSocketSession session, StringBuilder history,
		Map<String, String> info) {
		// AI 기반 재추천 판단
		if (aiService.isRerecommendationByAI(userMessage)) {
			log.info("AI가 재추천 요청으로 판단: chatRoomId={}, message={}", chatRoomId, userMessage);
			recommendationCompleted.put(chatRoomId, false);
			aiService.extractAndUpdateInfoByAI(info, userMessage);
			return generateFinalRecommendation(info, chatRoomId, membershipLevel);
		}
		// 일반적인 대화 처리
		return aiService.handlePostRecommendationConversation(userMessage, membershipLevel, session, chatRoomId);
	}

	private ChatMessageResponse generateFinalRecommendation(Map<String, String> collectedInfo, Long chatRoomId,
		User.MembershipLevel membershipLevel) {
		try {
			// 수집된 정보로 검색 쿼리 생성
			String searchQuery = buildSearchQuery(collectedInfo);

			//cache에 저장된 정보가 있는지 확인
			List<Lecture> cachedLecture = cacheService.search(searchQuery);

			if (cachedLecture != null && !cachedLecture.isEmpty()) {
				return respondWithLectures(cachedLecture, collectedInfo, searchQuery, membershipLevel, chatRoomId);
			}

			List<Lecture> similarLectures = ragService.searchSimilarLectures(searchQuery);

			// 유료회원(Pro 회원) 가격 필터
			if (User.MembershipLevel.PRO.equals(membershipLevel)) {
				String priceCondition = collectedInfo.getOrDefault(AiConstants.INFO_PRICE, " ").trim();
				similarLectures = applyPriceFilter(similarLectures, priceCondition);
			}

			if (similarLectures.isEmpty()) {
				resetChatState(chatRoomId);
				return createErrorResponse(AiConstants.ERROR_NO_LECTURES_FOUND);
			}

			//cache에 강의 없을 때 저장
			cacheService.storeVector(searchQuery, similarLectures);
			ChatMessageResponse response = respondWithLectures(similarLectures, collectedInfo, searchQuery,
				membershipLevel, chatRoomId);

			// 추천 완료 상태 설정
			recommendationCompleted.put(chatRoomId, true);
			lastRecommendationCriteria.put(chatRoomId, formatCollectedInfo(collectedInfo));

			return response;

		} catch (Exception e) {
			log.error("강의 검색 중 오류 발생: chatRoomId={}, error={}", chatRoomId, e.getMessage(), e);
			resetChatState(chatRoomId);
			return createErrorResponse(AiConstants.ERROR_LECTURE_SEARCH_FAILED);
		}
	}

	private ChatMessageResponse respondWithLectures(List<Lecture> lectureList, Map<String, String> collectedInfo,
		String searchQuery, User.MembershipLevel membershipLevel, Long chatRoomId) {
		String lectureInfo = lectureList.stream()
			.map(l -> """
				{
				    "lectureId": "%d",
				    "title": "%s",
				    "description": "%s",
				    "instructor": "%s",
				    "level": "%s",
				    "thumbnailUrl": "%s",
				    "url": "https://www.inflearn.com/search?s=%s",
				    "payPrice" : "%s",
				    "isFree" : "%s"
				}
				""".formatted(
				l.getLectureId(), l.getTitle(), l.getDescription(), l.getInstructor(),
				l.getLevelCode(), l.getThumbnailUrl(), l.getTitle(),
				l.isFree() ? "0" : (l.getPayPrice() != null ? l.getPayPrice().toPlainString() : "0"),
				l.isFree() ? "true" : "false")
			)
			.collect(Collectors.joining(",\n"));

		StringBuilder promptText = new StringBuilder();
		promptText.append(String.format("""
				[수집된 사용자 정보]
				%s
				
				[유사한 강의 정보]
				{
				    "recommendations": [
				        %s
				    ]
				}""",
			formatCollectedInfo(collectedInfo),
			lectureInfo
		));

		if (!User.MembershipLevel.GUEST.equals(membershipLevel)) {
			BraveSearchResponseDto braveResponse = braveSearchService.search(searchQuery);
			log.info("Brave API 요청 쿼리: {}", searchQuery);
			List<BraveSearchResponseDto.Result> braveResults = braveResponse.web().results();
			if (braveResults != null && !braveResults.isEmpty()) {
				String braveInfo = braveResults.stream()
					.map(r -> """
						{
						    "lectureId": null,
						    "title": "%s",
						    "description": "%s",
						    "instructor": null,
						    "level": null,
						    "thumbnailUrl": "%s",
						    "url": "%s"
						}
						""".formatted(
						r.title(), r.description(), r.thumbnail(), r.url()))
					.collect(Collectors.joining(",\n"));

				promptText.append("\n\n[브레이브 검색 결과]\n")
					.append("{\n    \"recommendations\": [\n")
					.append(braveInfo)
					.append("\n    ]\n}");
			}
			if (chatRoomId != null) {
				maybeUpdateChatRoomName(chatRoomId);
			}
		}

		ChatMessageResponse recommendationResponse = aiService.getRecommendations(promptText.toString(), true,
			membershipLevel);

		// 추천 완료 후 followup 메시지 추가
		if (recommendationResponse.getMessageType() == MessageType.RECOMMENDATION) {
			String followupMessage = String.format(AiConstants.RECOMMENDATION_FOLLOWUP,
				collectedInfo.getOrDefault(AiConstants.INFO_INTEREST, "요청하신 조건"));

			// followup 메시지를 대화 히스토리에 추가
			StringBuilder history = conversationHistory.get(chatRoomId);
			if (history != null) {
				history.append("AI: ").append(followupMessage).append("\n");
			}

			// followup 메시지를 recommendationResponse에 포함하여 반환
			// 메시지 필드에 followup 메시지를 포함
			recommendationResponse = ChatMessageResponse.builder()
				.message(followupMessage)
				.recommendations(recommendationResponse.getRecommendations())
				.isAiResponse(true)
				.messageType(MessageType.RECOMMENDATION)
				.build();
		}

		return recommendationResponse;
	}

	private List<Lecture> applyPriceFilter(List<Lecture> lectures, String priceCondition) {
		if (priceCondition.isBlank()) {
			log.info("가격 필터 없음 - 전체 강의 사용");
			return lectures;
		}

		Integer minPrice = null;
		Integer maxPrice = null;

		Pattern p = Pattern.compile("(\\d + )(만원)?\\s*(이하||이상)?");
		Matcher m = p.matcher(priceCondition);

		if (m.find()) {
			int price = Integer.parseInt(m.group(1)) * 10000;
			String condition = m.group(3);

			if ("이하".equals(condition)) {
				maxPrice = price;
			} else if ("이상".equals(condition)) {
				minPrice = price;
			} else {
				maxPrice = price;
			}
		}

		final Integer finalMinPrice = minPrice;
		final Integer finalMaxPrice = maxPrice;

		log.info("적용할 가격 필터 - minPrice: {} , maxPrice: {}", finalMinPrice, finalMaxPrice);

		return lectures.stream()
			.filter(l -> {
				BigDecimal lecturePrice = l.isFree() ? BigDecimal.ZERO : l.getPayPrice();
				if (finalMinPrice != null && lecturePrice.compareTo(BigDecimal.valueOf(finalMinPrice)) < 0) {
					return false;
				}
				if (finalMaxPrice != null && lecturePrice.compareTo(BigDecimal.valueOf(finalMaxPrice)) > 0) {
					return false;
				}
				return true;
			})
			.collect(Collectors.toList());
	}

	private String buildSearchQuery(Map<String, String> info) {
		StringBuilder query = new StringBuilder();
		if (info.containsKey(AiConstants.INFO_INTEREST)) {
			query.append(info.get(AiConstants.INFO_INTEREST)).append(" ");
		}
		if (info.containsKey(AiConstants.INFO_LEVEL)) {
			query.append(info.get(AiConstants.INFO_LEVEL)).append(" ");
		}
		if (info.containsKey(AiConstants.INFO_GOAL)) {
			query.append(info.get(AiConstants.INFO_GOAL)).append(" ");
		}
		if (info.containsKey(AiConstants.INFO_ADDITIONAL)) {
			query.append(info.get(AiConstants.INFO_ADDITIONAL)).append(" ");
		}
		return query.toString().trim();
	}

	private String formatCollectedInfo(Map<String, String> info) {
		StringBuilder formatted = new StringBuilder();
		if (info.containsKey(AiConstants.INFO_INTEREST)) {
			formatted.append(AiConstants.LABEL_INTEREST)
				.append(": ")
				.append(info.get(AiConstants.INFO_INTEREST))
				.append("\n");
		}
		if (info.containsKey(AiConstants.INFO_LEVEL)) {
			formatted.append(AiConstants.LABEL_LEVEL)
				.append(": ")
				.append(info.get(AiConstants.INFO_LEVEL))
				.append("\n");
		}
		if (info.containsKey(AiConstants.INFO_GOAL)) {
			formatted.append(AiConstants.LABEL_GOAL).append(": ").append(info.get(AiConstants.INFO_GOAL)).append("\n");
		}
		if (info.containsKey(AiConstants.INFO_ADDITIONAL)) {
			formatted.append(AiConstants.LABEL_ADDITIONAL)
				.append(": ")
				.append(info.get(AiConstants.INFO_ADDITIONAL))
				.append("\n");
		}
		return formatted.toString();
	}

	private void resetChatState(Long chatRoomId) {
		conversationHistory.remove(chatRoomId);
		collectedInfo.remove(chatRoomId);
		recommendationCompleted.remove(chatRoomId);
		lastRecommendationCriteria.remove(chatRoomId);
	}

	private ChatMessageResponse createErrorResponse(String errorMessage) {
		return ChatMessageResponse.builder()
			.message(errorMessage)
			.isAiResponse(true)
			.messageType(MessageType.ERROR)
			.build();
	}

	private void maybeUpdateChatRoomName(Long chatRoomId) {
		chatRoomRepository.findById(chatRoomId).ifPresent(chatRoom -> {
			if ("새 채팅방".equals(chatRoom.getChatroomName())) {
				String chatHistory = conversationHistory.get(chatRoomId).toString();
				String summarizedName = aiService.summarizeChatRoomName(chatHistory);
				log.info("요약된 채팅방 이름 : {}", summarizedName);
				chatRoomService.updateChatRoomName(chatRoom.getUser().getUserId(), chatRoomId, summarizedName);
			}
		});
	}

	private boolean isReadyForRecommendation(Map<String, String> collectedInfo, User.MembershipLevel membershipLevel) {
		// 기본 필수 정보: 관심분야, 목표, 난이도
		boolean hasBasicInfo = collectedInfo.containsKey(AiConstants.INFO_INTEREST) &&
			collectedInfo.containsKey(AiConstants.INFO_GOAL) &&
			collectedInfo.containsKey(AiConstants.INFO_LEVEL);

		// PRO 회원의 경우 가격 정보도 필수
		if (User.MembershipLevel.PRO.equals(membershipLevel)) {
			return hasBasicInfo && collectedInfo.containsKey(AiConstants.INFO_PRICE);
		}

		return hasBasicInfo;
	}
}
