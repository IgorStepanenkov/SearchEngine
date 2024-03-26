package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    Optional<PageEntity> findBySiteAndPath(SiteEntity siteEntity, String path);
    List<PageEntity> findAllByIdIn(List<Integer> pageIds);
    @Transactional
    @Modifying
    @Query("DELETE FROM PageEntity p WHERE p.site.id = :siteId")
    void deleteAllBySiteId(Integer siteId);
    long countBySite(SiteEntity site);

}
