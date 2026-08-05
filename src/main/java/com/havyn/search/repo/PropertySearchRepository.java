package com.havyn.search.repo;

import com.havyn.amenities.domain.Amenity;
import com.havyn.properties.domain.Availability;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.search.service.SearchCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Dynamic search query building via the JPA Criteria API, operating directly against
 * {@code properties}' entities without a Spring Data repository — prompt 11's file
 * scope doesn't include {@code properties/}, so this stays entirely inside {@code
 * search/} rather than adding a {@code JpaSpecificationExecutor} to {@code
 * PropertyRepository}. See project-docs/prompts/11-search-discovery.md.
 */
@Repository
public class PropertySearchRepository {

    // Field injection, not the codebase's usual constructor injection — @PersistenceContext
    // is a JPA container-injection point (processed by PersistenceAnnotationBeanPostProcessor),
    // not a regular bean lookup, so it needs a field/setter target.
    @PersistenceContext
    private EntityManager entityManager;

    public Page<Property> search(SearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Property> selectQuery = cb.createQuery(Property.class);
        Root<Property> selectRoot = selectQuery.from(Property.class);
        // Fetch join, not a plain join — SearchResultItem.from() reads
        // property.getType().getCode() for every row, and `type` is FetchType.LAZY, so
        // without this every page of results was N+1: one query for the page of
        // Properties, then one more per row to lazy-load its PropertyType. Safe with
        // pageable's setFirstResult/setMaxResults below specifically because `type` is
        // a @ManyToOne (to-one) — Hibernate only applies pagination in-memory (wrong
        // results) for a *collection* fetch join, which this isn't.
        selectRoot.fetch("type", JoinType.INNER);
        selectQuery.select(selectRoot)
                .where(predicates(cb, selectQuery, selectRoot, criteria))
                .orderBy(order(cb, selectRoot, criteria));

        List<Property> content = entityManager.createQuery(selectQuery)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Property> countRoot = countQuery.from(Property.class);
        countQuery.select(cb.count(countRoot)).where(predicates(cb, countQuery, countRoot, criteria));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Property> root, SearchCriteria c) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("status"), PropertyStatus.ACTIVE));

        if (c.destination() != null) {
            String pattern = "%" + c.destination().trim().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("city")), pattern),
                    cb.like(cb.lower(root.get("state")), pattern),
                    cb.like(cb.lower(root.get("country")), pattern)));
        }
        if (c.minPrice() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), c.minPrice()));
        }
        if (c.maxPrice() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), c.maxPrice()));
        }
        if (c.guests() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("capacity"), c.guests()));
        }
        if (c.bedrooms() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("bedrooms"), c.bedrooms()));
        }
        if (c.minRating() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("ratingAvg"), c.minRating()));
        }
        if (c.typeCode() != null) {
            predicates.add(cb.equal(root.get("type").get("code"), c.typeCode()));
        }
        for (String amenityCode : c.amenityCodes()) {
            predicates.add(cb.exists(hasAmenitySubquery(cb, query, root, amenityCode)));
        }
        if (c.checkIn() != null && c.checkOut() != null) {
            predicates.add(cb.not(cb.exists(unavailableInRangeSubquery(cb, query, root, c))));
        }

        return predicates.toArray(new Predicate[0]);
    }

    private Subquery<Long> hasAmenitySubquery(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Property> outer, String amenityCode) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Property> correlated = subquery.correlate(outer);
        Join<Property, Amenity> amenities = correlated.join("amenities");
        subquery.select(cb.literal(1L)).where(cb.equal(amenities.get("code"), amenityCode));
        return subquery;
    }

    private Subquery<Long> unavailableInRangeSubquery(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Property> outer, SearchCriteria c) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Availability> availability = subquery.from(Availability.class);
        Predicate sameProperty = cb.equal(availability.get("property").get("id"), outer.get("id"));
        // Checkout night itself isn't occupied — the exclusive upper bound is checkOut - 1 day.
        Predicate inRange = cb.between(availability.get("date"), c.checkIn(), c.checkOut().minusDays(1));
        Predicate unavailable = cb.or(cb.isTrue(availability.get("blocked")), availability.get("bookingId").isNotNull());
        subquery.select(cb.literal(1L)).where(cb.and(sameProperty, inRange, unavailable));
        return subquery;
    }

    private Order[] order(CriteriaBuilder cb, Root<Property> root, SearchCriteria c) {
        return switch (c.sort()) {
            case PRICE_ASC -> new Order[] {cb.asc(root.get("basePrice"))};
            case PRICE_DESC -> new Order[] {cb.desc(root.get("basePrice"))};
            case RATING_DESC -> new Order[] {cb.desc(root.get("ratingAvg")), cb.desc(root.get("createdAt"))};
            case NEWEST -> new Order[] {cb.desc(root.get("createdAt"))};
        };
    }
}
