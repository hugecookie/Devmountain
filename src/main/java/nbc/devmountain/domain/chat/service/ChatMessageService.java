package nbc.devmountain.domain.chat.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbc.devmountain.domain.ai.dto.RecommendationDto;
import nbc.devmountain.domain.chat.model.ChatMessage;
import nbc.devmountain.domain.chat.model.ChatRoom;
import nbc.devmountain.domain.chat.dto.ChatMessageResponse;
import nbc.devmountain.domain.chat.model.MessageType;
import nbc.devmountain.domain.chat.repository.ChatMessageRepository;
import nbc.devmountain.domain.chat.repository.ChatRoomRepository;
import nbc.devmountain.domain.lecture.model.Lecture;
import nbc.devmountain.domain.lecture.repository.LectureRepository;
import nbc.devmountain.domain.recommendation.model.Recommendation;
import nbc.devmountain.domain.recommendation.repository.RecommendationRepository;
import nbc.devmountain.domain.user.model.User;
import nbc.devmountain.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {

	private final ChatMessageRepository chatMessageRepository;
	private final ChatRoomRepository chatRoomRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;
	private final RecommendationRepository recommendationRepository;
	private final LectureRepository lectureRepository;

	@Transactional
	public ChatMessageResponse createMessage(Long userId, Long chatRoomId, String message) {

		if (message == null || message.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지 내용이 비어있습니다.");
		}
		if (message.length() > 1000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지가 너무 깁니다. (최대 1000자)");
		}

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

		ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

		if (!chatRoom.getUser().getUserId().equals(user.getUserId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}

		ChatMessage chatMessage = ChatMessage.builder()
			.chatRoom(chatRoom)
			.user(user)
			.message(message)
			.isAiResponse(false)
			.build();

		chatRoom.addMessages(chatMessage);
		ChatMessage savedMsg = chatMessageRepository.save(chatMessage);

		return ChatMessageResponse.from(savedMsg);
	}

	@Transactional
	public ChatMessageResponse createAIMessage(Long chatRoomId, ChatMessageResponse aiResponse) {
		ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

		User user = chatRoom.getUser();

		try {
			String messageContent;
			MessageType messageType;
			if (aiResponse.getRecommendations() != null && !aiResponse.getRecommendations().isEmpty()) {
				messageContent = objectMapper.writeValueAsString(aiResponse.getRecommendations());
				messageType = MessageType.RECOMMENDATION;

				log.info("추천 데이터 JSON 직렬화 완료: {}", messageContent);

				// AI 메시지를 생성
				ChatMessage aiChatMessage = ChatMessage.builder()
					.chatRoom(chatRoom)
					.user(null)
					.message(messageContent)
					.isAiResponse(true)
					.messageType(messageType)
					.build();

				chatRoom.addMessages(aiChatMessage);
				ChatMessage savedChatMessage = chatMessageRepository.save(aiChatMessage);

				for (RecommendationDto recDto : aiResponse.getRecommendations()) {
					Lecture lecture = null;

					// DB에 저장된 강의인 경우
					if (recDto.lectureId() != null) {
						lecture = lectureRepository.findById(recDto.lectureId()).orElse(null);
						if (lecture != null) {
							log.info("DB 강의 검색 성공: lectureId={}", recDto.lectureId());
						} else {
							log.warn("DB 강의 검색 실패: lectureId={}", recDto.lectureId());
						}
					} else {
						log.info("브레이브 검색 결과 추천: title={}", recDto.title());
					}
					// 추천 기록 저장
					Recommendation recommendation = Recommendation.builder()
						.chatMessage(savedChatMessage)
						.user(user)
						.lecture(lecture)
						.score(null)
						.build();
					recommendationRepository.save(recommendation);

					if (lecture != null) {
						log.info("DB 강의 추천 저장 성공: lectureId={}, userId={}", lecture.getLectureId(), user.getUserId());
					} else {
						log.info("브레이브 검색 결과 추천 저장 성공: title={}, userId={}", recDto.title(), user.getUserId());
					}
				}

				log.info("AI 메시지 생성 완료 - 타입: {}", aiResponse.getMessageType());

				return ChatMessageResponse.builder()
					.chatroomId(savedChatMessage.getChatRoom().getChatroomId())
					.chatId(savedChatMessage.getChatId())
					.userId(null)
					.message(null)
					.recommendations(aiResponse.getRecommendations())
					.isAiResponse(true)
					.messageType(MessageType.RECOMMENDATION)
					.createdAt(savedChatMessage.getCreatedAt())
					.updatedAt(savedChatMessage.getUpdatedAt())
					.build();
			} else {
				// 일반 AI 메시지 처리
				messageContent = aiResponse.getMessage();
				messageType = aiResponse.getMessageType();

				ChatMessage aiChatMessage = ChatMessage.builder()
					.chatRoom(chatRoom)
					.user(null)
					.message(messageContent)
					.isAiResponse(true)
					.messageType(messageType)
					.build();

				chatRoom.addMessages(aiChatMessage);
				ChatMessage savedChatMessage = chatMessageRepository.save(aiChatMessage);
				log.info("AI 메시지 생성 완료 - 타입: {}", aiResponse.getMessageType());

				return ChatMessageResponse.builder()
					.chatroomId(savedChatMessage.getChatRoom().getChatroomId())
					.chatId(savedChatMessage.getChatId())
					.userId(null)
					.message(messageContent)
					.recommendations(Collections.emptyList())
					.isAiResponse(true)
					.messageType(messageType)
					.createdAt(savedChatMessage.getCreatedAt())
					.updatedAt(savedChatMessage.getUpdatedAt())
					.build();
			}
		} catch (JsonProcessingException e) {
			log.error("AI 메시지 생성 중 오류 발생: {}", e.getMessage());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI 메시지 생성 실패");
		}
	}

	public List<ChatMessageResponse> getMessages(Long userId, Long roomId) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!chatRoom.getUser().getUserId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
		List<ChatMessage> chatMessages = chatMessageRepository.findByChatRoomId(roomId);

		return chatMessages.stream()
			.map(ChatMessageResponse::from)
			.collect(Collectors.toList());
	}
}