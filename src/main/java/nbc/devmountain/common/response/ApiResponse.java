package nbc.devmountain.common.response;

import org.aspectj.bridge.IMessage;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
	boolean success,
	int status,
	String message,
	T result
) {
	public static <T> ApiResponse<T> of(boolean success, String message, int status, T result) {
		return ApiResponse.<T>builder()
			.success(success)
			.status(status)
			.message(message)
			.result(result)
			.build();
	}

	public static ApiResponse<Void> error(String message, int status) {
		return ApiResponse.<Void>builder()
			.success(false)
			.status(status)
			.message(message)
			.build();
	}

	public static <T> ApiResponse<T> success(T result) {
		return ApiResponse.<T>builder()
			.success(true)
			.status(200)
			.message("요청이 성공했습니다.")
			.result(result)
			.build();
	}

	public static ApiResponse<Void> success(String message, int status) {
		return ApiResponse.<Void>builder()
			.success(true)
			.status(status)
			.message(message)
			.build();
	}

}