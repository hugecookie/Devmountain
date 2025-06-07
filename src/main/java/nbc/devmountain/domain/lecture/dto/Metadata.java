package nbc.devmountain.domain.lecture.dto;

import java.util.List;

public record Metadata(
	String levelCode,
	List<CategoryDto> categories

) {
}
