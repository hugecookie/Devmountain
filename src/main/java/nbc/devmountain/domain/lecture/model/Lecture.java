package nbc.devmountain.domain.lecture.model;

import jakarta.persistence.*;
import lombok.*;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import nbc.devmountain.domain.category.model.*;

@Entity
@Table(name = "Lecture")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Lecture {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long lectureId; // DB pk
	private int itemId;
	@Column(columnDefinition = "TEXT")
	private String thumbnailUrl;
	private String title;
	@Column(columnDefinition = "TEXT")
	private String description;
	private int reviewCount;
	private int studentCount;
	private int likeCount;
	private int star;

	@ManyToOne
	@JoinColumn(name = "instructor_id")
	private Instructor instructor;

	@OneToOne
	@JoinColumn(name = "metaData_id")
	private MetaData metaData;

	@OneToOne
	@JoinColumn(name = "ListPrice_id")
	private ListPrice listPrice;

	@Builder
	public Lecture(int itemId, String thumbnailUrl, String title, String description, int reviewCount, int studentCount, int likeCount, int star, Instructor instructor, ListPrice listPrice, MetaData metaData) {
		this.itemId = itemId;
		this.thumbnailUrl = thumbnailUrl;
		this.title = title;
		this.description = description;
		this.reviewCount = reviewCount;
		this.studentCount = studentCount;
		this.likeCount = likeCount;
		this.star = star;
		this.instructor = instructor;
		this.listPrice = listPrice;
		this.metaData = metaData;
	}

}