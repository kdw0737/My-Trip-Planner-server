package project.tripplan.domain.plan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import project.tripplan.domain.plan.dto.PlanContentAndTotalCountRes;
import project.tripplan.domain.plan.dto.PlanNoOffsetReq;
import project.tripplan.domain.plan.entity.Plan;
import project.tripplan.domain.user.dto.UserPlanRes;

public interface PlanRepositoryCustom {
	Optional<Plan> findByPlanIdWithUser(Long planId);

	PlanContentAndTotalCountRes searchPlanNoOffset(PlanNoOffsetReq req);

	List<Plan> findMostViewedPlans(int limit);

	List<Plan> findMostRecentPlans(int limit);

	Page<UserPlanRes> findPlansByUserId(Long userId, Pageable pageable);

	Optional<Plan> findPlanWithAllChildren(Long planId);

	List<Plan> findAllByIds(List<Long> planIds);
}
