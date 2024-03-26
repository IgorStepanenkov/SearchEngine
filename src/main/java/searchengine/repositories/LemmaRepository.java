package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Set;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    long countBySite(SiteEntity site);

    List<LemmaEntity> findAllByLemmaInAndFrequencyGreaterThan(Set<String> lemmas, int frequencyGreaterThan);

    List<LemmaEntity> findAllBySiteAndLemmaInAndFrequencyGreaterThan(SiteEntity site, Set<String> lemmas,
                                                                     int frequencyGreaterThan);
    @Transactional
    @Modifying
    @Query("DELETE FROM LemmaEntity l WHERE l.site.id = :siteId")
    void deleteAllBySiteId(Integer siteId);

    @Transactional
    @Query(value = "FROM LemmaEntity l WHERE l.site.id = :siteId AND l.lemma IN (:lemmas)")
    List<LemmaEntity> findAllBySiteIdAndLemmaIn(Integer siteId, List<String> lemmas);

    @Transactional
    @Modifying
    @Query(value = "UPDATE LemmaEntity l SET l.frequency = l.frequency + 1 WHERE l.id IN (:lemmaIds)")
    void incrementFrequencyAllByLemmaIdIn(List<Integer> lemmaIds);

    @Transactional
    @Modifying
    @Query(value = "UPDATE LemmaEntity l SET l.frequency = l.frequency - 1 WHERE l.id IN (:lemmaIds)")
    void decrementFrequencyAllByLemmaIdIn(List<Integer> lemmaIds);
}
