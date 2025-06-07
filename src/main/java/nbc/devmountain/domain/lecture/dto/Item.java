package nbc.devmountain.domain.lecture.dto;

import java.util.List;

import nbc.devmountain.domain.lecture.model.ListPrice;

public record Item(
	int id,
	Course course,
	Instructor instructor,
	ListPrice listPrice

) {
}
