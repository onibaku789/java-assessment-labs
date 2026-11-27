package study.locker.directory.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface CompartmentRows extends JpaRepository<CompartmentRow, String> {
    @Query(
            "select c from CompartmentRow c where (:site='' or c.site=:site) and (:size='' or"
                    + " c.size=:size) order by c.id")
    List<CompartmentRow> search(String site, String size);
}
