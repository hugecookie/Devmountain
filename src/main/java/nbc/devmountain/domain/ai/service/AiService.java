package nbc.devmountain.domain.ai.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import nbc.devmountain.common.monitering.CustomMetrics;
import nbc.devmountain.domain.user.model.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nbc.devmountain.domain.ai.constant.AiConstants;
import nbc.devmountain.domain.ai.dto.RecommendationDto;
import nbc.devmountain.domain.chat.dto.ChatMessageResponse;
import nbc.devmountain.domain.chat.model.MessageType;
import nbc.devmountain.domain.chat.service.ChatMessageService;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
@AllArgsConstructor
@Slf4j
public class AiService {
	private final ChatModel chatModel;
	private final ObjectMapper objectMapper;
	private final StreamingChatModel streamingChatModel;
	private final ChatMessageService chatMessageService;
	private final CustomMetrics customMetrics;

	public ChatMessageResponse analyzeConversationAndDecideNext(
		String conversationHistory,
		Map<String, String> collectedInfo,
		String latestUserMessage,
		User.MembershipLevel membershipLevel,
		WebSocketSession session,
		Long chatRoomId) {

		customMetrics.incrementAiRequest(); // 모니터링(ai 요청수 체크)

		//AI 기반 정보 추출 및 업데이트
		extractAndUpdateInfoByAI(collectedInfo, latestUserMessage);

		//수집된 정보를 바탕으로 추천 준비 여부 확인
		if (isReadyForRecommendation(collectedInfo, membershipLevel)) {
			return ChatMessageResponse.builder()
				.message(AiConstants.SUCCESS_READY_FOR_RECOMMENDATION)
				.isAiResponse(true)
				.messageType(MessageType.RECOMMENDATION)
				.build();
		}
		// 추천 준비가 안됐으면 일반 대화 스트리밍 처리
		// 프롬프트 생성
		SystemMessage systemMessage = new SystemMessage(AiConstants.CONVERSATION_ANALYSIS_PROMPT);
		String promptText = String.format(
			"현재 대화 히스토리:\n%s\n\n현재 수집된 정보:\n%s\n\n최신 사용자 메시지: %s",
			conversationHistory,
			formatCollectedInfo(collectedInfo),
			latestUserMessage
		);
		Prompt prompt = new Prompt(List.of(systemMessage, new UserMessage(promptText)));
		log.info("[AiService] 대화 스트리밍 프롬프트 전송 >>>\n{}", promptText);

		// 스트리밍 메소드 호출
		return streamChatResponse(prompt, session, chatRoomId, membershipLevel);
	}

	private ChatMessageResponse streamChatResponse(Prompt prompt, WebSocketSession session, Long chatRoomId,
		User.MembershipLevel membershipLevel) {
		final StringBuilder fullMessage = new StringBuilder();

		try {
			Flux<String> contentStream = Optional.ofNullable(streamingChatModel.stream(prompt))
				.orElse(Flux.empty())
				.map(chatResponse -> {
					// ChatResponse 에서 텍스트 컨텐츠 추출
					if (chatResponse != null && chatResponse.getResult() != null
						&& chatResponse.getResult().getOutput() != null) {
						String content = chatResponse.getResult().getOutput().getText();
						return content != null ? content : "";
					}
					return "";
				})
				.filter(chunk -> !chunk.isBlank());

			contentStream
				.doOnNext(chunk -> {
					// 각 청크 클라이언트에 실시간 전송
					try {
						fullMessage.append(chunk);
						boolean isFirstChunk = fullMessage.length() == chunk.length();
						ChatMessageResponse message = ChatMessageResponse.builder()
							.message(chunk)
							.isAiResponse(true)
							.messageType(MessageType.CHAT)
							.isFirst(isFirstChunk)
							.isLast(false)
							.build();

						String json = objectMapper.writeValueAsString(message);
						if (json != null && !json.trim().isEmpty()) { // null 체크
							session.sendMessage(new TextMessage(json));
							customMetrics.incrementAiRequest(); // 모니터링(ai 요청수 체크)
						}
					} catch (Exception e) {
						log.warn("[AiService] 메시지 청크 전송 실패", e);
					}
				})
				.publishOn(Schedulers.boundedElastic()) // DB 저장 등 블로킹 작업 스레드 전환
				.doOnComplete(() -> {
					// 스트리밍 완료 후 처리
					try {
						// 마지막 메세지
						ChatMessageResponse lastMessage = ChatMessageResponse.builder()
							.message("")
							.isAiResponse(true)
							.messageType(MessageType.CHAT)
							.isFirst(false)
							.isLast(true)
							.build();

						String lastJson = objectMapper.writeValueAsString(lastMessage);
						if (lastJson != null && !lastJson.trim().isEmpty()) { // null 체크
							session.sendMessage(new TextMessage(lastJson));
						}

						// 로그인한 회원, DB에 전체 메시지 저장
						if (chatRoomId != null && membershipLevel != User.MembershipLevel.GUEST) {
							ChatMessageResponse fullResponse = ChatMessageResponse.builder()
								.message(fullMessage.toString())
								.isAiResponse(true)
								.messageType(MessageType.CHAT)
								.build();
							chatMessageService.createAIMessage(chatRoomId, fullResponse);
						} else {
							log.info("비회원 또는 roomId 없음: AI 메시지 저장 생략");
						}
					} catch (Exception e) {
						log.warn("[AiService] 마지막 메시지 전송/저장 실패", e);
					}
				})
				.doOnError(e -> log.error("[AiService] 스트리밍 중 오류 발생", e))
				.blockLast();// 스트림이 완전히 끝날 때까지 현재 스레드를 차단하고 대기

			// 스트리밍 완료 후 AI 응답 검증
			String aiResponse = fullMessage.toString();
			if (aiResponse.contains(AiConstants.READY_FOR_RECOMMENDATION)) {
				return ChatMessageResponse.builder()
					.message(AiConstants.SUCCESS_READY_FOR_RECOMMENDATION)
					.isAiResponse(true)
					.messageType(MessageType.RECOMMENDATION)
					.build();
			}

			return ChatMessageResponse.builder()
				.message(fullMessage.toString())
				.isAiResponse(true)
				.messageType(MessageType.CHAT)
				.build();

		} catch (Exception e) {
			log.error("[AiService] 스트리밍 처리 중 심각한 오류 발생", e);
			throw new RuntimeException("AI 응답 처리 중 실패했습니다.", e);
		}
	}

	public void extractAndUpdateInfoByAI(Map<String, String> collectedInfo, String userMessage) {
		SystemMessage systemMessage = new SystemMessage(AiConstants.INFO_CLASSIFICATION_PROMPT);

		String promptText = String.format(
			"사용자 메시지: %s\n\n현재 수집된 정보:\n%s",
			userMessage,
			formatCollectedInfo(collectedInfo)
		);

		Prompt prompt = new Prompt(List.of(systemMessage, new UserMessage(promptText)));
		log.info("[AiService] 정보 분류 프롬프트 전송 >>>\n{}", promptText);

		try {
			ChatResponse response = chatModel.call(prompt);
			String aiResponse = response.getResult().getOutput().getText();

			log.info("[AiService] 정보 분류 AI 응답 >>>\n{}", aiResponse);

			// JSON 추출 및 파싱
			String jsonString = extractJsonString(aiResponse);
			if (!jsonString.isEmpty()) {
				JsonNode jsonNode = objectMapper.readTree(jsonString);

				// 각 정보 업데이트 (빈 값이 아닌 경우에만)
				updateInfoIfNotEmpty(collectedInfo, jsonNode, AiConstants.INFO_INTEREST);
				updateInfoIfNotEmpty(collectedInfo, jsonNode, AiConstants.INFO_LEVEL);
				updateInfoIfNotEmpty(collectedInfo, jsonNode, AiConstants.INFO_GOAL);
				updateInfoIfNotEmpty(collectedInfo, jsonNode, AiConstants.INFO_PRICE);
				updateInfoIfNotEmpty(collectedInfo, jsonNode, AiConstants.INFO_ADDITIONAL);

				log.info("[AiService] 정보 업데이트 완료: {}", collectedInfo);
			}
		} catch (Exception e) {
			log.warn("[AiService] AI 정보 분류 실패, 기존 정보 유지: {}", e.getMessage());
		}
	}

	private void updateInfoIfNotEmpty(Map<String, String> collectedInfo, JsonNode jsonNode, String key) {
		JsonNode valueNode = jsonNode.get(key);
		if (valueNode != null && !valueNode.asText().trim().isEmpty()) {
			String newValue = valueNode.asText().trim();

			// 기존 값이 있는 경우 추가 정보는 합치고, 다른 정보는 더 구체적인 것으로 업데이트
			if (AiConstants.INFO_ADDITIONAL.equals(key) && collectedInfo.containsKey(key)) {
				String existingValue = collectedInfo.get(key);
				collectedInfo.put(key, existingValue + " " + newValue);
			} else {
				collectedInfo.put(key, newValue);
			}
		}
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

	private String formatCollectedInfo(Map<String, String> info) {
		StringBuilder formatted = new StringBuilder();
		info.forEach((key, value) -> {
			String keyName = switch (key) {
				case AiConstants.INFO_INTEREST -> AiConstants.LABEL_INTEREST;
				case AiConstants.INFO_LEVEL -> AiConstants.LABEL_LEVEL;
				case AiConstants.INFO_GOAL -> AiConstants.LABEL_GOAL;
				case AiConstants.INFO_ADDITIONAL -> AiConstants.LABEL_ADDITIONAL;
				default -> key;
			};
			formatted.append(keyName).append(": ").append(value).append("\n");
		});
		return formatted.toString();
	}

	public ChatMessageResponse getRecommendations(String promptText, boolean isFinalRecommendation,
		User.MembershipLevel membershipLevel) {
		customMetrics.incrementAiRequest(); // 모니터링(ai 요청수 체크)
		SystemMessage systemMessage = new SystemMessage(AiConstants.RECOMMENDATION_PROMPT);
		Prompt prompt = new Prompt(List.of(systemMessage, new UserMessage(promptText)));
		log.info("[AiService] 추천 프롬프트 전송 >>>\n{}", promptText);

		String rawAiResponse;

		if (!User.MembershipLevel.GUEST.equals(membershipLevel)) {
			log.info("[AiService] 회원 유저 추천 호출");
			// PRO 유저: Tool을 활용한 ChatClient 호출

			rawAiResponse = ChatClient.create(chatModel)
				.prompt(prompt)
				.tools("videos_searchVideos")
				.call()
				.content();
			log.info("[AiService] ai 응답값 >>>\n{}", rawAiResponse);
		} else {
			log.info("[AiService] 게스트 추천 호출");
			// GUEST 유저: 기존 ChatModel 호출 방식
			ChatResponse response = chatModel.call(prompt);
			rawAiResponse = response.getResult().getOutput().getText();
		}

		log.info("[AiService] AI 추천 응답 >>>\n{}", rawAiResponse);
		if (!isFinalRecommendation) {
			// 일반 대화인 경우 텍스트 그대로 반환
			return ChatMessageResponse.builder()
				.message(rawAiResponse)
				.isAiResponse(true)
				.messageType(MessageType.CHAT)
				.build();
		}

		// JSON 문자열 추출
		String pureJson = extractJsonString(rawAiResponse);
		if (pureJson.isEmpty()) {
			log.warn("[AiService] AI 응답에서 JSON을 찾을 수 없음: {}", rawAiResponse);
			return createErrorResponse(AiConstants.ERROR_AI_INVALID_FORMAT);
		}

		try {
			JsonNode root = objectMapper.readTree(pureJson);
			JsonNode recNode = root.get("recommendations");

			if (recNode == null || !recNode.isArray()) {
				log.warn("[AiService] AI 응답에 'recommendations' 배열이 없거나 형식이 올바르지 않음.");
				return createErrorResponse(AiConstants.ERROR_AI_INVALID_FORMAT);
			}
			List<RecommendationDto> recommendations = objectMapper.convertValue(recNode,
				objectMapper.getTypeFactory().constructCollectionType(List.class, RecommendationDto.class));

			if (recommendations.isEmpty()) {
				log.info("[AiService] AI가 추천할 강의를 찾지 못함.");
				return createErrorResponse(AiConstants.ERROR_NO_SUITABLE_LECTURES);
			}

			// 추천 결과 검증 및 로깅
			for (RecommendationDto rec : recommendations) {
				if (rec.lectureId() == null) {
					log.info("[AiService] 브레이브 검색 결과 추천: title={}", rec.title());
				} else {
					log.info("[AiService] DB 강의 추천: lectureId={}, title={}", rec.lectureId(), rec.title());
				}
			}

			return ChatMessageResponse.builder()
				.message(null)
				.recommendations(recommendations)
				.isAiResponse(true)
				.messageType(MessageType.RECOMMENDATION)
				.build();

		} catch (JsonProcessingException e) {
			log.error("[AiService] AI 응답 파싱 실패!\n원본: {}\n추출된 JSON: {}\n에러: {}\n{}",
				rawAiResponse, pureJson, e.toString(), e.getMessage());
			return createErrorResponse(AiConstants.ERROR_AI_PARSING_FAILED);
		}
	}

	private String extractJsonString(String rawResponse) {
		if (rawResponse == null || rawResponse.trim().isEmpty()) {
			return "";
		}
		Pattern pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(rawResponse);

		if (matcher.find()) {
			return matcher.group();
		}
		return "";
	}

	private ChatMessageResponse createErrorResponse(String errorMessage) {
		return ChatMessageResponse.builder()
			.message(errorMessage)
			.recommendations(Collections.emptyList())
			.isAiResponse(true)
			.messageType(MessageType.ERROR)
			.build();
	}

	//강의 추천정보 요약
	public String summarizeChatRoomName(String chatHistory) {
		String prompt = AiConstants.SUMMARIZATION_CHATROOM_PROMPT + "\n\n[대화 내용]\n" + chatHistory;
		return chatModel.call(prompt);
	}

	public ChatMessageResponse handlePostRecommendationConversation(String userMessage, User.MembershipLevel membershipLevel,
		WebSocketSession session, Long chatRoomId) {
		
		SystemMessage systemMessage = new SystemMessage(AiConstants.POST_RECOMMENDATION_CONVERSATION_PROMPT);
		String promptText = String.format(
			"사용자 메시지: %s\n\n회원 등급: %s",
			userMessage,
			membershipLevel
		);
		Prompt prompt = new Prompt(List.of(systemMessage, new UserMessage(promptText)));
		log.info("[AiService] 추천 완료 후 대화 프롬프트 전송 >>>\n{}", promptText);

		// 스트리밍 메소드 호출
		return streamChatResponse(prompt, session, chatRoomId, membershipLevel);
	}



	public boolean isRerecommendationByAI(String userMessage) {
		SystemMessage systemMessage = new SystemMessage(AiConstants.RERECOMMENDATION_DETECT_PROMPT);
		Prompt prompt = new Prompt(List.of(systemMessage, new UserMessage("Q: '" + userMessage + "'\nA:")));
		try {
			ChatResponse response = chatModel.call(prompt);
			String aiAnswer = response.getResult().getOutput().getText().trim();
			return aiAnswer.equalsIgnoreCase("YES");
		} catch (Exception e) {
			log.warn("[AiService] 재추천 판단 AI 호출 실패: {}", e.getMessage());
			return false;
		}
	}
}
