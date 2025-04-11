package project.tripplan.domain.plan.repository;

import java.util.Collection;
import java.util.List;

import project.tripplan.domain.plan.entity.PlanPlaceCategory;

public interface PlanPlaceCategoryRepositoryCustom {
	List<PlanPlaceCategory> findAllByPlanIdWithPlanAndPlace(Long planId);

	List<PlanPlaceCategory> findAllByPlanIds(Collection<Long> planIds);

	List<Long> findPlanIdsByPlaceName(String keyword, int limit);
}
