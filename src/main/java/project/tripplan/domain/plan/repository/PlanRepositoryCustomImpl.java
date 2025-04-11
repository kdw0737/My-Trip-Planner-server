package project.tripplan.domain.plan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import project.tripplan.domain.category.placeCategory.entity.QPlaceCategory;
import project.tripplan.domain.category.transportationCategory.entitiy.QTransportationCategory;
import project.tripplan.domain.category.transportationCategory.enums.TransportationName;
import project.tripplan.domain.plan.dto.PlanContentAndTotalCountRes;
import project.tripplan.domain.plan.dto.PlanNoOffsetReq;
import project.tripplan.domain.plan.entity.Plan;
import project.tripplan.domain.plan.entity.QPlan;
import project.tripplan.domain.plan.entity.QPlanPlaceCategory;
import project.tripplan.domain.plan.entity.QPlanTransportationCategory;
import project.tripplan.domain.plan.enums.PlanStatus;
import project.tripplan.domain.planDay.entity.QPlanDay;
import project.tripplan.domain.planDayDetail.entity.QPlanDayDetail;
import project.tripplan.domain.user.dto.UserPlanRes;
import project.tripplan.domain.user.entity.QUser;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryCustomImpl implements PlanRepositoryCustom {

	private final JPAQueryFactory qf;
	private final QPlan plan = QPlan.plan;
	private final QUser user = QUser.user;
	private final QTransportationCategory transportationCategory = QTransportationCategory.transportationCategory;
	private final QPlanPlaceCategory planPlaceCategory = QPlanPlaceCategory.planPlaceCategory;
	private final QPlanTransportationCategory planTransport = QPlanTransportationCategory.planTransportationCategory;
	private final QPlanDay planDay = QPlanDay.planDay;
	private final QPlanDayDetail planDayDetail = QPlanDayDetail.planDayDetail;

	@Override
	public Optional<Plan> findByPlanIdWithUser(Long planId) {

		return Optional.ofNullable(
			qf.selectFrom(plan)
				.join(plan.user, user).fetchJoin()
				.where(plan.id.eq(planId))
				.fetchOne());
	}

	@Override
	public PlanContentAndTotalCountRes searchPlanNoOffset(PlanNoOffsetReq req) {
		List<Plan> content = getContent(req);
		long totalCount = getTotalCount(req);
		return new PlanContentAndTotalCountRes(content, totalCount);
	}

	private List<Plan> getContent(PlanNoOffsetReq req) {
		int limit = req.getSize() + 1;

		// 1) 필터용 alias
		QPlanPlaceCategory ppcFilter = new QPlanPlaceCategory("ppcFilter");
		QPlaceCategory pcFilter = new QPlaceCategory("pcFilter");

		// 2) 전체 fetch용 alias
		QPlanPlaceCategory ppcFetch = new QPlanPlaceCategory("ppcFetch");
		QPlaceCategory pcFetch = new QPlaceCategory("pcFetch");

		// 필터링 조건
		BooleanBuilder builder = buildSearchCondition(req, ppcFilter, pcFilter);
		builder.and(plan.status.eq(PlanStatus.PUBLIC));

		// 쿼리 생성
		JPAQuery<Plan> query = qf
			.selectDistinct(plan)
			.from(plan)
			.leftJoin(plan.planPlaceCategories, ppcFilter)
			.leftJoin(ppcFilter.placeCategory, pcFilter)
			.leftJoin(plan.planPlaceCategories, ppcFetch).fetchJoin()
			.leftJoin(ppcFetch.placeCategory, pcFetch).fetchJoin()
			.leftJoin(plan.planTransportationCategories, planTransport).fetchJoin()
			.leftJoin(planTransport.transportationCategory, transportationCategory).fetchJoin()
			.where(builder);

		// (D) No-Offset 커서 처리 + ORDER BY
		applyNoOffset(query, req);

		// (E) limit
		query.limit(limit);

		// (F) fetch
		return query.fetch();
	}

	private BooleanBuilder buildSearchCondition(PlanNoOffsetReq req, QPlanPlaceCategory ppcFilter,
		QPlaceCategory pcFilter) {
		BooleanBuilder builder = new BooleanBuilder();

		BooleanBuilder orBuilder = new BooleanBuilder();

		// 1) 제목 검색 && 장소 카테고리 같이 검색
		if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
			if (checkKeywordExistsInDB(req.getKeyword())) {
				orBuilder.or(plan.title.likeIgnoreCase("%" + req.getKeyword() + "%"));
			} else {
				// categoryNamecategoryIds가 null이면 결과가 안 나오도록 처리
				if (req.getCategoryNamecategoryIds() == null) {
					builder.and(Expressions.FALSE);
					return builder;
				}
			}
		} else {
			// keyword가 null이고 categoryNamecategoryIds가 null이면 결과가 안 나오도록 처리
			if (req.getCategoryNamecategoryIds() == null) {
				builder.and(Expressions.FALSE);
				return builder;
			}
		}

		if (req.getCategoryNamecategoryIds() != null && !req.getCategoryNamecategoryIds().isEmpty()) {
			log.info("CategoryNamecategoryIds != null");
			builder.or(pcFilter.id.in(req.getCategoryNamecategoryIds()));
		}

		if (orBuilder.hasValue()) {
			builder.and(orBuilder);
		}

		// 2) day
		if (req.getDay() != null && req.getDay() > 0) {
			builder.and(
				Expressions.numberTemplate(Integer.class, "DATEDIFF({0}, {1})", plan.endDate, plan.startDate)
					.add(1)
					.eq(req.getDay())
			);
		}

		// 3) 교통수단
		if (req.getTransportCategoryName() != null && !req.getTransportCategoryName().isEmpty()) {
			TransportationName enumValue = TransportationName.valueOf(req.getTransportCategoryName());
			builder.and(transportationCategory.name.eq(enumValue));
		}

		// 4) 인원
		if (req.getPeople() != null && req.getPeople() > 0) {
			builder.and(plan.people.eq(req.getPeople()));
		}

		return builder;
	}

	private void applyNoOffset(JPAQuery<Plan> query, PlanNoOffsetReq req) {
		String sortBy = (req.getSortBy() != null) ? req.getSortBy() : "id";
		String direction = (req.getDirection() != null) ? req.getDirection().toUpperCase() : "DESC";
		String lastValue = req.getLastValue();
		Long lastId = req.getLastId();

		// (A) 커서 조건
		if (lastValue != null && lastId != null) {
			switch (sortBy) {
				case "viewCount":
					long lastViewCount = Long.parseLong(lastValue);
					if ("DESC".equals(direction)) {
						// viewCount < lastViewCount OR (== and plan.id < lastId)
						query.where(
							plan.viewCount.lt(lastViewCount)
								.or(
									plan.viewCount.eq(lastViewCount)
										.and(plan.id.lt(lastId))
								)
						);
					} else {
						// viewCount > lastViewCount OR (== and plan.id > lastId)
						query.where(
							plan.viewCount.gt(lastViewCount)
								.or(
									plan.viewCount.eq(lastViewCount)
										.and(plan.id.gt(lastId))
								)
						);
					}
					break;

				case "id":
				default:
					long lastPk = Long.parseLong(lastValue);
					if ("DESC".equals(direction)) {
						query.where(plan.id.lt(lastPk));
					} else {
						query.where(plan.id.gt(lastPk));
					}
					break;
			}
		}

		// (B) ORDER BY
		switch (sortBy) {
			case "viewCount":
				if ("DESC".equals(direction)) {
					// viewCount DESC, id DESC
					query.orderBy(plan.viewCount.desc(), plan.id.desc());
				} else {
					// viewCount ASC, id ASC
					query.orderBy(plan.viewCount.asc(), plan.id.asc());
				}
				break;

			case "id":
			default:
				if ("DESC".equals(direction)) {
					query.orderBy(plan.id.desc());
				} else {
					query.orderBy(plan.id.asc());
				}
				break;
		}
	}

	private long getTotalCount(PlanNoOffsetReq req) {
		// 필터용 alias
		QPlanPlaceCategory ppcFilter = new QPlanPlaceCategory("ppcFilter");
		QPlaceCategory pcFilter = new QPlaceCategory("pcFilter");

		BooleanBuilder builder = buildSearchCondition(req, ppcFilter, pcFilter);

		builder.and(plan.status.eq(PlanStatus.PUBLIC));

		Long countResult = qf
			.select(plan.countDistinct())
			.from(plan)

			// 동일한 필터 조인
			.leftJoin(plan.planPlaceCategories, ppcFilter)
			.leftJoin(ppcFilter.placeCategory, pcFilter)
			.leftJoin(plan.planTransportationCategories, planTransport)
			.leftJoin(planTransport.transportationCategory, transportationCategory)

			.where(builder)
			.fetchOne();

		return (countResult != null) ? countResult : 0;
	}

	@Override
	public List<Plan> findMostRecentPlans(int limit) {
		return qf.selectFrom(plan)
			.where(plan.status.eq(PlanStatus.PUBLIC))
			.orderBy(plan.createdAt.desc(), plan.id.desc())
			.limit(limit)
			.fetch();
	}

	@Override
	public List<Plan> findMostViewedPlans(int limit) {
		return qf.selectFrom(plan)
			.where(plan.status.eq(PlanStatus.PUBLIC))
			.orderBy(plan.viewCount.desc(), plan.id.asc())
			.limit(limit)
			.fetch();
	}

	@Override
	public Page<UserPlanRes> findPlansByUserId(Long userId, Pageable pageable) {
		List<UserPlanRes> content = qf
			.select(Projections.constructor(
				UserPlanRes.class,
				plan.id,
				plan.title,
				plan.createdAt,
				plan.imageUrl.as("thumbnail"),
				Expressions.stringTemplate(
					"group_concat(DISTINCT {0})",
					planPlaceCategory.placeCategory.name
				)
				,
				plan.status.stringValue()
			))
			.from(plan)
			.leftJoin(plan.planPlaceCategories, planPlaceCategory)
			.where(plan.user.id.eq(userId))
			.orderBy(plan.createdAt.desc())
			.groupBy(plan.id)
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();

		long total = qf.select(plan.count())
			.from(plan)
			.where(plan.user.id.eq(userId))
			.fetchOne();

		return new PageImpl<>(content, pageable, total);
	}

	@Override
	public Optional<Plan> findPlanWithAllChildren(Long planId) {

		Plan result = qf
			.select(plan)
			.from(plan)
			.leftJoin(plan.planDays, planDay).fetchJoin()
			.leftJoin(planDay.planDayDetails, planDayDetail).fetchJoin()
			.leftJoin(plan.planPlaceCategories, planPlaceCategory).fetchJoin()
			.leftJoin(plan.planTransportationCategories, planTransport).fetchJoin()
			.where(plan.id.eq(planId))
			.fetchOne();

		return Optional.ofNullable(result);
	}

	@Override
	public List<Plan> findAllByIds(List<Long> planIds) {
		if (planIds == null || planIds.isEmpty()) return List.of();

		return qf
			.selectFrom(plan)
			.where(plan.id.in(planIds))
			.fetch();
	}

	private boolean checkKeywordExistsInDB(String keyword) {
		// plan은 Q클래스(QPlan)라고 가정
		// 만약 plan.title likeIgnoreCase '%keyword%' 결과가 하나라도 있으면 true
		Integer fetchOne = qf
			.selectOne()
			.from(plan)
			.where(plan.title.likeIgnoreCase("%" + keyword + "%"))
			.fetchFirst(); // 결과값이 하나라도 있으면 not null

		// fetchOne != null 이면 키워드가 존재한다는 뜻
		return (fetchOne != null);
	}
}
