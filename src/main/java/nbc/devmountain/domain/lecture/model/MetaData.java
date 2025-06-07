package nbc.devmountain.domain.lecture.model;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "metaData")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaData {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long metaDataId;

	private String levelCode;

	@Builder
	public MetaData (String levelCode) {
		this.levelCode = levelCode;
	}



}

