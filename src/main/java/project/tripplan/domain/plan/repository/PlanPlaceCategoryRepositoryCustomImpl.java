package project.tripplan.domain.plan.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import project.tripplan.domain.category.placeCategory.entity.QPlaceCategory;
import project.tripplan.domain.plan.entity.PlanPlaceCategory;
import project.tripplan.domain.plan.entity.QPlan;
import project.tripplan.domain.plan.entity.QPlanPlaceCategory;
import project.tripplan.domain.plan.enums.PlanStatus;
import project.tripplan.domain.user.entity.QUser;

@Repository
@RequiredArgsConstructor
public class PlanPlaceCategoryRepositoryCustomImpl implements PlanPlaceCategoryRepositoryCustom {

	private final JPAQueryFactory qf;
	private final QPlan plan = QPlan.plan;
	private final QUser user = QUser.user;
	private final QPlanPlaceCategory planPlaceCategory = QPlanPlaceCategory.planPlaceCategory;
	private final QPlaceCategory placeCategory = QPlaceCategory.placeCategory;

	@Override
	public List<PlanPlaceCategory> findAllByPlanIdWithPlanAndPlace(Long planId) {
		return qf.selectFrom(planPlaceCategory)
			.join(planPlaceCategory.plan, plan).fetchJoin()
			.join(planPlaceCategory.placeCategory, placeCategory).fetchJoin()
			.where(planPlaceCategory.plan.id.eq(planId))
			.fetch();
	}

	@Override
	public List<PlanPlaceCategory> findAllByPlanIds(Collection<Long> planIds) {
		if (planIds == null || planIds.isEmpty()) return List.of();

		return qf.selectFrom(planPlaceCategory)
			.join(planPlaceCategory.placeCategory, placeCategory).fetchJoin()
			.where(planPlaceCategory.plan.id.in(planIds))
			.fetch();
	}


	@Override
	public List<Long> findPlanIdsByPlaceName(String keyword, int limit) {
		return qf
			.select(plan.id)
			.from(planPlaceCategory)
			.join(planPlaceCategory.placeCategory, placeCategory)
			.join(planPlaceCategory.plan, plan)
			.where(placeCategory.name.eq(keyword).and(plan.status.eq(PlanStatus.PUBLIC)))
			.distinct()
			.limit(limit)
			.fetch();
	}
}
