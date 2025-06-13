package nbc.devmountain.domain.lecture.model;

import jakarta.persistence.Column;
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
@Table(name = "skill_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillTag {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long skillTagId;
	@Column(unique = true, nullable = false)
	private String title;



	@Builder
	public SkillTag(String title){
		this.title = title;
	}

}
