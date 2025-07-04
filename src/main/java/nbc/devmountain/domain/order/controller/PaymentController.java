package nbc.devmountain.domain.order.controller;

import lombok.RequiredArgsConstructor;
import nbc.devmountain.common.util.security.CustomUserPrincipal;
import nbc.devmountain.domain.order.dto.ConfirmPaymentRequest;
import nbc.devmountain.domain.order.dto.PaymentResponseDto;
import nbc.devmountain.domain.order.dto.TossPaymentResponse;
import nbc.devmountain.domain.order.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/confirm-payment")
    public ResponseEntity<?> confirmPayment(
            @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal CustomUserPrincipal user
    ) {
        TossPaymentResponse response = paymentService.confirmPayment(request, user.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<PaymentResponseDto> get(@PathVariable Long paymentId,
                                                  @AuthenticationPrincipal CustomUserPrincipal user) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId, user.getUserId()));
    }
}
