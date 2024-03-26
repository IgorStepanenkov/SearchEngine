package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexStatusType;
import searchengine.model.SiteEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    List<SiteEntity> findAllByStatus(IndexStatusType status);
    long countAllByStatus(IndexStatusType status);
    Optional<SiteEntity> findByUrl(String url);

    @Transactional
    @Modifying
    @Query(value = "UPDATE SiteEntity s SET s.status = :newStatus, s.statusTime = :statusTime," +
            " s.lastError = :lastError WHERE s.status = :oldStatus")
    void updateAllSitesStatus(IndexStatusType oldStatus, IndexStatusType newStatus,
                              Instant statusTime, String lastError);

    @Transactional
    @Modifying
    @Query(value = "UPDATE SiteEntity s SET s.status = :newStatus, s.statusTime = :statusTime," +
            " s.lastError = :lastError WHERE s.id = :siteId")
    void updateSiteStatusBySiteId(Integer siteId, IndexStatusType newStatus,
                                  Instant statusTime, String lastError);

    @Transactional
    @Modifying
    @Query(value = "UPDATE SiteEntity s SET s.statusTime = :statusTime WHERE s.id = :siteId")
    void updateSiteStatusTimeBySiteId(Integer siteId, Instant statusTime);
}
