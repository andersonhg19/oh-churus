package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.enums.CategoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndActiveTrue(Long id);

    List<Category> findByUserIdAndActiveTrue(Long userId);

    List<Category> findByUserIdAndParentIdIsNullAndActiveTrue(Long userId);

    List<Category> findByParentIdAndActiveTrue(Long parentId);

    boolean existsByUserIdAndNameAndParentIdAndActiveTrue(Long userId, String name, Long parentId);

    boolean existsByUserIdAndNameAndParentIdAndActiveTrueAndIdNot(Long userId, String name, Long parentId, Long id);

    boolean existsByParentIdAndActiveTrue(Long parentId);

    @Query("SELECT c FROM Category c WHERE c.active = true " +
            "AND (:userId IS NULL OR c.userId = :userId) " +
            "AND (:name IS NULL OR LOWER(CAST(c.name AS string)) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
            "AND (:type IS NULL OR c.type = :type)")
    Page<Category> findAllWithFilters(@Param("userId") Long userId,
                                      @Param("name") String name,
                                      @Param("type") CategoryType type,
                                      Pageable pageable);

    List<Category> findByHouseholdIdAndActiveTrue(Long householdId);

    // Get personal categories (no household) + household categories for a user's households
    @Query("SELECT c FROM Category c WHERE c.active = true AND " +
            "(c.userId = :userId OR c.householdId IN :householdIds)")
    List<Category> findPersonalAndHousehold(@Param("userId") Long userId,
                                            @Param("householdIds") List<Long> householdIds);

    @Query("SELECT c FROM Category c WHERE c.active = true " +
            "AND (c.userId = :userId OR c.householdId IN :householdIds) " +
            "AND (:name IS NULL OR LOWER(CAST(c.name AS string)) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
            "AND (:type IS NULL OR c.type = :type)")
    Page<Category> findAllWithFiltersAndHousehold(@Param("userId") Long userId,
                                                   @Param("householdIds") List<Long> householdIds,
                                                   @Param("name") String name,
                                                   @Param("type") CategoryType type,
                                                   Pageable pageable);
}
