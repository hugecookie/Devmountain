package nbc.devmountain.domain.chat.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import nbc.devmountain.common.util.security.SessionUser;
import nbc.devmountain.domain.ai.service.LectureRecommendationService;
import nbc.devmountain.domain.chat.dto.ChatMessageResponse;

import nbc.devmountain.domain.chat.model.MessageType;
import nbc.devmountain.domain.chat.websocket.WebSocketMessageSender;
import nbc.devmountain.domain.user.model.User;

@RequiredArgsConstructor
@Service
@Slf4j
public class ChatService {
	private final ChatMessageService chatMessageService;
	private final LectureRecommendationService recommendationService;
	private final WebSocketMessageSender messageSender;

	public void handleMessage(WebSocketSession session, Long roomId, String payload) {
		SessionUser sessionUser = (SessionUser)session.getAttributes().get("user");
		User.MembershipLevel membershipType = (User.MembershipLevel)session.getAttributes().get("membershipType");

		if (membershipType == User.MembershipLevel.GUEST) {
			Integer guestCount = (Integer)session.getAttributes().getOrDefault("guestCount", 0);
			if (guestCount >= 10) {
				ChatMessageResponse limitMsg = ChatMessageResponse.builder()
					.message("비회원은 최대 10개의 메세지만 보낼 수 있습니다. 더 이용하려면 로그인을 해주세요.")
					.isAiResponse(true)
					.messageType(MessageType.ERROR)
					.build();
				messageSender.sendMessage(session, limitMsg);
				return;
			}
			session.getAttributes().put("guestCount", guestCount + 1);
		}
		if (membershipType != User.MembershipLevel.GUEST) {
			ChatMessageResponse userMsg = chatMessageService.createMessage(sessionUser.getUserId(), roomId, payload);
			log.info("회원 메세지 생성 완료");
			messageSender.sendMessageToRoom(roomId, userMsg);
		}

		// AI 추천 응답 처리
		ChatMessageResponse aiResponse = recommendationService.recommendationResponse(payload, membershipType, roomId,
			session);

		if (aiResponse.getMessageType() == MessageType.RECOMMENDATION) {
			ChatMessageResponse aiMsg;
			if (membershipType != User.MembershipLevel.GUEST) {
				aiMsg = chatMessageService.createAIMessage(roomId, aiResponse);
				log.info("AI 추천 메시지 생성 완료");
			} else {
				aiMsg = aiResponse;
			}
			messageSender.sendMessageToRoom(roomId, aiMsg);

		} else if (aiResponse.getMessageType() == MessageType.CHAT) {
			// 일반 대화 메시지 처리
			if (membershipType != User.MembershipLevel.GUEST) {
				ChatMessageResponse aiMsg = chatMessageService.createAIMessage(roomId, aiResponse);
				messageSender.sendMessageToRoom(roomId, aiMsg);
			} else {
				messageSender.sendMessageToRoom(roomId, aiResponse);
			}
		}
	}

	private void sendFollowupMessage(Long roomId, User.MembershipLevel membershipType, String followupMessage) {
		try {
			// followup 메시지 생성
			ChatMessageResponse followupResponse = ChatMessageResponse.builder()
				.message(followupMessage)
				.isAiResponse(true)
				.messageType(MessageType.CHAT)
				.build();

			// 회원인 경우 DB에 저장
			if (membershipType != User.MembershipLevel.GUEST) {
				ChatMessageResponse savedFollowup = chatMessageService.createAIMessage(roomId, followupResponse);
				messageSender.sendMessageToRoom(roomId, savedFollowup);
			} else {
				messageSender.sendMessageToRoom(roomId, followupResponse);
			}

			log.info("추천 완료 후 followup 메시지 전송 완료: roomId={}", roomId);
		} catch (Exception e) {
			log.error("Followup 메시지 전송 실패: roomId={}, error={}", roomId, e.getMessage(), e);
		}
	}

	public List<ChatMessageResponse> getChatHistory(Long userId, Long roomId) {
		return chatMessageService.getMessages(userId, roomId);
	}

	public ChatMessageResponse getGuestWelcomeMsg() {
		return ChatMessageResponse.builder()
			.message("안녕하세요. 비회원으로 접속하셨습니다.")
			.isAiResponse(true)
			.messageType(MessageType.WELCOME)
			.build();
	}
}
