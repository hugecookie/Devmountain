package nbc.devmountain.domain.lecture.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import nbc.devmountain.domain.lecture.model.ListPrice;

@Repository
public interface ListPriceRepository extends JpaRepository<ListPrice, Long> {
}
