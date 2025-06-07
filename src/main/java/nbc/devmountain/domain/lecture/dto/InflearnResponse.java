package nbc.devmountain.domain.lecture.dto;


public record InflearnResponse(
	String statusCode,

	String message,

	String errorCode,

	Data data
		)

{

}
