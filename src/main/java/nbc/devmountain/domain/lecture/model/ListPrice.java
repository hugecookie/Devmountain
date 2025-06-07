package nbc.devmountain.domain.lecture.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ListPrice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListPrice {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long ListPriceId;
	private boolean isDiscount;
	private int payPrice;
	private int regularPrice;
	private boolean isFree;
	private int discountRate;

	@Builder
	public ListPrice(boolean isDiscount, int payPrice, int regularPrice, boolean isFree, int discountRate){
		this.isDiscount = isDiscount;
		this.payPrice = payPrice;
		this.regularPrice = regularPrice;
		this.isFree = isFree;
		this.discountRate = discountRate;
	}
}
