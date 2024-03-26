package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.List;
import java.util.Set;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    @Transactional
    @Modifying
    @Query("DELETE FROM IndexEntity i WHERE i.page.id = :pageId")
    void deleteAllByPageId(Integer pageId);

//    @Transactional
//    @Modifying
//    @Query("DELETE FROM IndexEntity i WHERE i.page.id IN (:pageIds)")
//    void deleteAllByPageIdIn(List<Integer> pageIds);

    @Transactional
    @Modifying
    @Query("DELETE FROM IndexEntity i WHERE i.page.id IN (SELECT p.id FROM PageEntity p WHERE p.site.id = :siteId) " +
            "OR i.lemma.id IN (SELECT l.id FROM LemmaEntity l WHERE l.site.id = :siteId)")
    void deleteAllBySiteId(Integer siteId);

    List<IndexEntity> findAllByPage(PageEntity pageEntity);

    List<IndexEntity> findAllByLemma(LemmaEntity lemmaEntity);

    List<IndexEntity> findAllByPageInAndLemma(Set<PageEntity> pageEntities, LemmaEntity lemmaEntity);
}
