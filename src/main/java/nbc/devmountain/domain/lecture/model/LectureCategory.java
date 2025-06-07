package nbc.devmountain.domain.lecture.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "LectureCategory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LectureCategory {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long categoryId;
	private int id;
	private String title;

	@ManyToOne
	@JoinColumn(name = "metaData_id")
	private MetaData metaData;

	@ManyToOne
	@JoinColumn(name = "LectureCategory_id")
	private LectureCategory parent; // 이거 좀 생각해 봐야할 듯

	@Builder
	public LectureCategory(int id, String title, MetaData metaData, LectureCategory parent){
		this.id = id;
		this.title = title;
		this.metaData = metaData;
		this.parent = parent;
	}

}
