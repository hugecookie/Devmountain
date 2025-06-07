package nbc.devmountain.domain.lecture.dto;

import java.util.List;

public record Data(
	String attributionToken,
	int totalPage,
	int totalCount,
	int pageNumber,
	int pageSize,
	List<Item> items
) {
}
