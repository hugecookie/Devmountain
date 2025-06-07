package nbc.devmountain.domain.lecture.dto;

import java.util.List;

public record Course(
	int id,
	String slug,
	String thumbnailUrl,
	String title,
	String description,
	int reviewCount,
	int studentCount,
	int likeCount,
	int star,
	Metadata metadata

) {
}
