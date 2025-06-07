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
@Getter
@Table(name = "Instructor")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instructor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long InstructorId;
	private String name;

	@Builder
	public Instructor(String name){
		this.name=name;
	}
}
