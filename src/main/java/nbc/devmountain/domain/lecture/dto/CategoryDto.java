package nbc.devmountain.domain.lecture.dto;

public record CategoryDto(
	int id,
	String title,
	String slug,
	CategoryDto parent
) {
}
