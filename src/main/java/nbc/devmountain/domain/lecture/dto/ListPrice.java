package nbc.devmountain.domain.lecture.dto;

public record ListPrice(
	boolean isDiscount,
	int payPrice,
	int regularPrice,
	boolean isFree,
	int discountRate

) {
}
