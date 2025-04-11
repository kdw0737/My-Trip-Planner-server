package project.tripplan.domain.plan.service;

import static project.tripplan.domain.plan.enums.PlanStatus.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import project.tripplan.domain.bookmark.entity.Bookmark;
import project.tripplan.domain.bookmark.repository.BookmarkRepositoryCustom;
import project.tripplan.domain.category.placeCategory.entity.PlaceCategory;
import project.tripplan.domain.category.placeCategory.service.PlaceCategoryService;
import project.tripplan.domain.category.planCategory.entity.PlanCategory;
import project.tripplan.domain.category.planCategory.repository.PlanCategoryRepository;
import project.tripplan.domain.category.transportationCategory.entitiy.TransportationCategory;
import project.tripplan.domain.category.transportationCategory.enums.TransportationName;
import project.tripplan.domain.category.transportationCategory.repository.TransportationCategoryRepository;
import project.tripplan.domain.comment.entity.PlanComment;
import project.tripplan.domain.comment.repository.PlanCommentRepositoryCustom;
import project.tripplan.domain.like.entity.PlanLike;
import project.tripplan.domain.like.repository.PlanLikeRepositoryCustom;
import project.tripplan.domain.plan.dto.HomeRes;
import project.tripplan.domain.plan.dto.PlaceCategoryNamesReq;
import project.tripplan.domain.plan.dto.PlanCommentsRes;
import project.tripplan.domain.plan.dto.PlanContentAndTotalCountRes;
import project.tripplan.domain.plan.dto.PlanDataReq;
import project.tripplan.domain.plan.dto.PlanDayDetailReq;
import project.tripplan.domain.plan.dto.PlanDayReq;
import project.tripplan.domain.plan.dto.PlanDetailRes;
import project.tripplan.domain.plan.dto.PlanNoOffsetReq;
import project.tripplan.domain.plan.dto.PlanNoOffsetRes;
import project.tripplan.domain.plan.dto.PlanReq;
import project.tripplan.domain.plan.dto.PlanSearchRes;
import project.tripplan.domain.plan.dto.PlanStatusReq;
import project.tripplan.domain.plan.dto.PlanUpdateReq;
import project.tripplan.domain.plan.entity.Plan;
import project.tripplan.domain.plan.entity.PlanPlaceCategory;
import project.tripplan.domain.plan.entity.PlanTransportationCategory;
import project.tripplan.domain.plan.enums.PlanStatus;
import project.tripplan.domain.plan.repository.PlanPlaceCategoryRepositoryCustom;
import project.tripplan.domain.plan.repository.PlanRepository;
import project.tripplan.domain.plan.repository.PlanRepositoryCustom;
import project.tripplan.domain.plan.repository.PlanTransCategoryRepositoryCustom;
import project.tripplan.domain.planDay.entity.PlanDay;
import project.tripplan.domain.planDayDetail.entity.PlanDayDetail;
import project.tripplan.domain.point.entity.Point;
import project.tripplan.domain.point.enums.PointStatus;
import project.tripplan.domain.point.enums.PointType;
import project.tripplan.domain.point.repository.PointRepository;
import project.tripplan.domain.point.repository.PointRepositoryCustom;
import project.tripplan.domain.user.entity.User;
import project.tripplan.domain.user.enums.UserRole;
import project.tripplan.domain.user.repository.UserRepository;
import project.tripplan.global.common.exception.CustomException;
import project.tripplan.global.common.response.BaseResponseCode;
import project.tripplan.global.file.S3Service;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PlanService {

	@Value("${cloud.prefix}")
	private String prefix;

	private final PlanRepository planRepository;
	private final PlanCategoryRepository planCategoryRepository;
	private final PlanPlaceCategoryRepositoryCustom planPlaceCategoryRepositoryCustom;
	private final PlanTransCategoryRepositoryCustom planTransCategoryRepositoryCustom;
	private final PlanRepositoryCustom planRepositoryCustom;
	private final PlanLikeRepositoryCustom planLikeRepositoryCustom;
	private final S3Service s3Service;
	private final PlaceCategoryService placeCategoryService;
	private final TransportationCategoryRepository transportationCategoryRepository;
	private final PlanCommentRepositoryCustom planCommentRepositoryCustom;
	private final UserRepository userRepository;
	private final BookmarkRepositoryCustom bookmarkRepositoryCustom;
	private final StringRedisTemplate redisTemplate;
	private final PointRepository pointRepository;
	private final PointRepositoryCustom pointRepositoryCustom;

	/**
	 * 계획 저장 메서드
	 */
	@Transactional
	public void savePlan(User user, PlanReq planReq, MultipartFile thumbnail) throws IOException {
		Plan plan = Plan.builder()
			.user(user)
			.status(PUBLIC)
			.build();

		applyPlanData(plan, planReq);

		if (thumbnail != null && !thumbnail.isEmpty()) {
			String savedThumbnail = s3Service.uploadFile(thumbnail);
			plan.setImageUrl(savedThumbnail);
		}

		planRepository.save(plan);

		Point point = Point.builder()
			.user(user)
			.pointType(PointType.PLAN)
			.pointTypeId(plan.getId())
			.pointStatus(PointStatus.PENDING)
			.point(100)
			.build();

		pointRepository.save(point);
	}

	@Transactional
	public void updatePlanStatus(User user, Long planId, @Valid PlanStatusReq planStatusReq) {
		Plan findPlan = planRepositoryCustom.findByPlanIdWithUser(planId)
			.orElseThrow(() -> new CustomException(BaseResponseCode.PLAN_NOT_EXIST));

		if (findPlan.getUser().getId() != user.getId()) {
			throw new CustomException(BaseResponseCode.UNAUTHORIZED_POST_UPDATE_STATUS);
		}

		findPlan.updateStatus(planStatusReq.getStatus());
	}

	@Transactional
	public PlanDetailRes getPlanInfoDetails(User user, Long planId) {
		List<PlanPlaceCategory> findPlanPlaceCategories = planPlaceCategoryRepositoryCustom.findAllByPlanIdWithPlanAndPlace(
			planId);

		Plan findPlan = planRepositoryCustom.findByPlanIdWithUser(planId)
			.orElseThrow(() -> new CustomException(BaseResponseCode.PLAN_NOT_EXIST));

		Optional<PlanLike> findPlanLike = planLikeRepositoryCustom.findPlanLikeWithUserAndPlan(user.getId(),
			planId);

		Optional<Bookmark> findBookmark = bookmarkRepositoryCustom.findByUserAndPlan(user.getId(), planId);

		Long likesCount = planLikeRepositoryCustom.countLikesByPlanId(planId);

		// 동일아이디 조회수 증가 30분에 1번으로 제한
		String redisKey = "view:plan:" + planId + ":user:" + user.getId();
		ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();

		if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
			// Redis에 해당 사용자의 조회 기록이 없으면 조회수 증가
			findPlan.increaseViewCount();
			valueOperations.set(redisKey, "true", 30, TimeUnit.MINUTES);
		}

		PlanTransportationCategory findPlanTrans = planTransCategoryRepositoryCustom.findByPlanIdWithPlanTransCategory(
				planId)
			.orElseThrow(() -> new CustomException(BaseResponseCode.GET_PLAN_TRANS_FAIL));

		if (findPlanPlaceCategories.isEmpty()) {
			throw new CustomException(BaseResponseCode.GET_PLAN_PLACE_FAIL);
		}

		List<String> categoryNames = findPlanPlaceCategories.stream()
			.map(planPlaceCategory -> planPlaceCategory.getPlaceCategory().getName())
			.toList();

		// 첫 번째 PlanPlaceCategory에서 공통 정보를 가져옴
		PlanPlaceCategory placeCategory = findPlanPlaceCategories.get(0);

		String thumbnail = findPlan.getImageUrl() != null ? prefix + "/" + findPlan.getImageUrl() : null;
		String profileImage =
			findPlan.getUser().getImage() != null ? prefix + "/" + findPlan.getUser().getImage() : null;

		// PlanDetailRes DTO 생성
		PlanDetailRes planDetailRes = new PlanDetailRes();
		planDetailRes.setTitle(placeCategory.getPlan().getTitle());
		planDetailRes.setSocialId(findPlan.getUser().getSocialId());
		planDetailRes.setLikeId(findPlanLike.orElse(null) != null ? findPlanLike.get().getId() : null);
		planDetailRes.setBookmarkId(findBookmark.orElse(null) != null ? findBookmark.get().getId() : null);
		planDetailRes.setPlaceCategory(categoryNames);
		planDetailRes.setAuthor(findPlan.getUser().getNickname());
		planDetailRes.setProfileImage(profileImage);
		planDetailRes.setThumbnail(thumbnail);
		planDetailRes.setCreatedAt(placeCategory.getPlan().getCreatedAt());
		planDetailRes.setStartDate(placeCategory.getPlan().getStartDate());
		planDetailRes.setEndDate(placeCategory.getPlan().getEndDate());
		planDetailRes.setStatus(placeCategory.getPlan().getStatus());
		planDetailRes.setViewCount(placeCategory.getPlan().getViewCount());
		planDetailRes.setLike(likesCount);
		planDetailRes.setPeople(placeCategory.getPlan().getPeople());
		planDetailRes.setTransportation(findPlanTrans.getTransportationCategory().getName());
		planDetailRes.setTotalCost(placeCategory.getPlan().getTotalCost());

		return planDetailRes;
	}

	@Transactional(readOnly = true)
	public PlanNoOffsetRes getPlanNoOffset(PlanNoOffsetReq req) {

		// 카테고리 IDs를 저장할 최종 Set (categoryNames, 지역검색 키워드 둘 다 OR로 합쳐서 사용)
		Set<Long> finalCategoryIds = new HashSet<>();

		// 1) categoryNames가 있는 경우, 자식까지 포함한 categoryIds 구하기 (OR 조건)
		if (req.getCategoryNames() != null && !req.getCategoryNames().isEmpty()) {
			Set<Long> allIds = placeCategoryService.findAllDescendantCategoryIds(req.getCategoryNames());
			finalCategoryIds.addAll(allIds);
		}

		// 2) keyword가 지역으로 판별되는 경우, placeCategory까지 검색해서 OR 조건에 합침
		String keyword = req.getKeyword();
		if (keyword != null && !keyword.isEmpty()) {
			if (isLocationKeyword(keyword)) {
				Set<Long> locationCategoryIds = placeCategoryService.findAllSearchDescendantCategoryIds(keyword);
				finalCategoryIds.addAll(locationCategoryIds);
				req.setKeyword(null);
			}
		}

		// 3) 최종적으로 검색에 사용할 카테고리 목록 설정
		//    finalCategoryIds가 비어 있다면 (== 검색할 카테고리가 전혀 없다면) null로 설정하여 검색 결과가 0건이 되도록 함
		if (finalCategoryIds.isEmpty()) {
			req.setCategoryNamecategoryIds(null);
		} else {
			req.setCategoryNamecategoryIds(finalCategoryIds);
		}

		// DB 조회 (size+1 개)
		PlanContentAndTotalCountRes rawList = planRepositoryCustom.searchPlanNoOffset(req);

		// hasNext (size 이상이면 다음 페이지 존재)
		boolean hasNext = rawList.getContent().size() > req.getSize();

		// 실제 반환 목록 (size까지만)
		List<Plan> content = hasNext
			? rawList.getContent().subList(0, req.getSize())
			: rawList.getContent();

		// nextValue, nextId 설정
		String nextValue = null;
		Long nextId = null;
		if (!content.isEmpty()) {
			Plan lastPlan = content.get(content.size() - 1);

			switch (req.getSortBy()) {
				case "viewCount":
					// null 안전 처리
					long vc = (lastPlan.getViewCount() == null) ? 0L : lastPlan.getViewCount();
					nextValue = String.valueOf(vc);
					break;

				case "id":
				default:
					nextValue = String.valueOf(lastPlan.getId());
					break;
			}
			nextId = lastPlan.getId();
		}

		// DTO 변환
		List<PlanSearchRes> plans = content.stream()
			.map(plan -> new PlanSearchRes(plan, prefix))
			.toList();

		// 응답
		PlanNoOffsetRes response = new PlanNoOffsetRes();
		response.setPlans(plans);
		response.setHasNext(hasNext);
		response.setNextValue(nextValue);
		response.setNextId(nextId);
		response.setTotalCount(rawList.getTotalCount());

		return response;
	}

	@Transactional(readOnly = true)
	public HomeRes getHome() {
		List<Plan> mostViewedPlans = planRepositoryCustom.findMostViewedPlans(10);
		List<Plan> mostRecentPlans = planRepositoryCustom.findMostRecentPlans(10);

		List<Long> mostViewedPlanIds = mostViewedPlans.stream()
			.map(Plan::getId)
			.toList();

		List<Long> mostRecentPlanIds = mostRecentPlans.stream()
			.map(Plan::getId)
			.toList();

		// 2. "강남"이 포함된 Plan ID 조회 (10개)
		List<Long> hotPlacePlanIds = planPlaceCategoryRepositoryCustom.findPlanIdsByPlaceName("강남", 10);

		// 3. 전체 대상 Plan ID 통합
		Set<Long> allPlanIds = new HashSet<>();
		allPlanIds.addAll(mostViewedPlanIds);
		allPlanIds.addAll(mostRecentPlanIds);
		allPlanIds.addAll(hotPlacePlanIds);

		// 4. 연관 데이터 한 번에 조회
		List<PlanPlaceCategory> allPlaces = planPlaceCategoryRepositoryCustom.findAllByPlanIds(allPlanIds);
		List<PlanTransportationCategory> allTrans = planTransCategoryRepositoryCustom.findAllByPlanIds(allPlanIds);

		// 5. HotPlace Plan 조회
		List<Plan> hotPlacePlans = planRepositoryCustom.findAllByIds(hotPlacePlanIds);

		// 6. 공통 맵핑 처리 (placeMap, transMap 재사용)
		Map<Long, List<String>> placeMap = allPlaces.stream()
			.collect(Collectors.groupingBy(
				ppc -> ppc.getPlan().getId(),
				Collectors.mapping(ppc -> ppc.getPlaceCategory().getName(), Collectors.toList())
			));

		Map<Long, String> transMap = allTrans.stream()
			.collect(Collectors.toMap(
				ptc -> ptc.getPlan().getId(),
				ptc -> ptc.getTransportationCategory().getName().toString(),
				(existing, replace) -> existing // 중복 시 첫 번째 값 유지
			));

		// 7. DTO 변환
		List<HomeRes.PlanInfo> mostViewedDTOs = convertToPlanInfo(mostViewedPlans, placeMap, transMap);
		List<HomeRes.PlanInfo> mostRecentDTOs = convertToPlanInfo(mostRecentPlans, placeMap, transMap);
		List<HomeRes.PlanInfo> hotPlaceDTOs = convertToPlanInfo(hotPlacePlans, placeMap, transMap);

		// 8. 결과 반환
		return new HomeRes(mostViewedDTOs, mostRecentDTOs, hotPlaceDTOs);
	}



	private List<HomeRes.PlanInfo> convertToPlanInfo(
		List<Plan> plans,
		Map<Long, List<String>> placeMap,
		Map<Long, String> transMap
	) {
		return plans.stream()
			.map(plan -> new HomeRes.PlanInfo(
				plan.getId(),
				plan.getTitle(),
				placeMap.getOrDefault(plan.getId(), List.of()),
				plan.getStartDate(),
				plan.getEndDate(),
				plan.getPeople(),
				transMap.getOrDefault(plan.getId(), null),
				plan.getTotalCost().intValue(),
				plan.getImageUrl() != null ? prefix + "/" + plan.getImageUrl() : null
			))
			.toList();
	}


	@Transactional(readOnly = true)
	public Page<PlanCommentsRes> getPlanComments(Long planId, int page, int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<PlanComment> findCommentsPage = planCommentRepositoryCustom.findAllByPlanIdWithUser(planId, pageable);

		return findCommentsPage.map(comment -> new PlanCommentsRes(
			comment.getUser().getSocialId(),
			comment.getId(),
			prefix + "/" + comment.getUser().getImage(),
			comment.getUser().getNickname(),
			comment.getCreatedAt(),
			comment.getContent()
		));
	}

	public void deletePlan(Long planId, User user) {
		Plan plan = planRepository.findById(planId)
			.orElseThrow(() -> new CustomException(BaseResponseCode.PLAN_NOT_EXIST));
		if (plan.getUser().getId() != user.getId() && user.getUserRole() != UserRole.ADMIN) {
			// 관리자가 아니면서 본인이 작성한 게획글이 아닌 경우
			throw new CustomException(BaseResponseCode.UNAUTHORIZED_POST_DELETE_STATUS);
		}
		planRepository.delete(plan);
	}

	public void copyPlan(Long planId, Long userId) {
		Plan plan = planRepositoryCustom.findPlanWithAllChildren(planId)
			.orElseThrow(() -> new CustomException(BaseResponseCode.PLAN_NOT_EXIST));
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new CustomException(BaseResponseCode.USER_NOT_EXIST));

		Plan copyPlan = Plan.builder()
			.user(user)
			.title(plan.getTitle())
			.viewCount(0L)
			.people(plan.getPeople())
			.status(PlanStatus.PRIVATE)
			.totalCost(plan.getTotalCost())
			.startDate(plan.getStartDate())
			.endDate(plan.getEndDate())
			.planTransportationCategories(new HashSet<>())
			.planPlaceCategories(new HashSet<>())
			.planDays(new HashSet<>())
			.planLikes(new ArrayList<>())
			.planComments(new ArrayList<>())
			.build();

		planRepository.save(copyPlan);
		copyRelatedEntities(plan, copyPlan);
		copyPlan.setImageUrl(s3Service.copyFile(plan.getImageUrl()));
	}

	private void copyRelatedEntities(Plan plan, Plan copyPlan) {
		// 1) PlanPlaceCategory copy
		for (PlanPlaceCategory ppc : plan.getPlanPlaceCategories()) {
			PlanPlaceCategory cppc = PlanPlaceCategory.builder()
				.plan(copyPlan)
				.placeCategory(ppc.getPlaceCategory())
				.build();

			copyPlan.getPlanPlaceCategories().add(cppc);
		}

		// 2) PlanTransportationCategory copy
		for (PlanTransportationCategory ptc : plan.getPlanTransportationCategories()) {
			PlanTransportationCategory cptc = PlanTransportationCategory.builder()
				.plan(copyPlan)
				.transportationCategory(ptc.getTransportationCategory())
				.build();

			copyPlan.getPlanTransportationCategories().add(cptc);
		}

		// 3) PlanDay + PlanDayDetail copy
		for (PlanDay pd : plan.getPlanDays()) {
			PlanDay cpd = PlanDay.builder()
				.plan(copyPlan)
				.day(pd.getDay())
				.date(pd.getDate())
				.cost(pd.getCost())
				.planDayDetails(new HashSet<>())
				.build();

			// PlanDayDetail copy
			copyPlanDayDetails(pd, cpd);

			copyPlan.getPlanDays().add(cpd);
		}
	}

	private void copyPlanDayDetails(PlanDay planDay, PlanDay copyPlanDay) {
		for (PlanDayDetail pdd : planDay.getPlanDayDetails()) {
			PlanDayDetail cpdd = PlanDayDetail.builder()
				.planDay(copyPlanDay)
				.orderIndex(pdd.getOrderIndex())
				.placeName(pdd.getPlaceName())
				.streetAddress(pdd.getStreetAddress())
				.latitude(pdd.getLatitude())
				.longitude(pdd.getLongitude())
				.planCategory(pdd.getPlanCategory())
				.build();

			copyPlanDay.getPlanDayDetails().add(cpdd);
		}
	}

	@Transactional
	public Boolean updatePlan(User user, PlanUpdateReq planUpdateReq, MultipartFile thumbnail) throws IOException {
		Plan plan = planRepository.findById(planUpdateReq.getPlanId())
			.orElseThrow(() -> new CustomException(BaseResponseCode.PLAN_NOT_EXIST));

		if (!plan.getUser().getId().equals(user.getId())) {
			throw new CustomException(BaseResponseCode.UNAUTHORIZED_POST_UPDATE_STATUS);
		}

		//plan에 연결된 planDay, planPlaceCategory, transportation 삭제
		plan.clearAllPlanDays();
		plan.clearAllPlanPlaceCategories();
		plan.clearAllTransportationCategories();

		if (planUpdateReq.getStatus() != null) {
			plan.setStatus(planUpdateReq.getStatus());
		}

		/**
		 * plan 정보 input
		 */
		applyPlanData(plan, planUpdateReq);

		//이미지 변경시 삭제 후 재 생성
		if (thumbnail != null && !thumbnail.isEmpty()) {
			s3Service.deleteFile(plan.getImageUrl());
			plan.setImageUrl(s3Service.uploadFile(thumbnail));
		}

		return true;
	}

	/**
	 * savePlan, updatePlan 공통 로직
	 */
	private void applyPlanData(Plan plan, PlanDataReq dto) {

		//plan
		plan.applyPlanBasicFields(dto.getTitle(),
			dto.getPeople(),
			dto.getStartDate(),
			dto.getEndDate(),
			calculateTotalCostFromDays(dto.getDays()));

		//planDay
		if (dto.getDays() != null) {
			for (PlanDayReq dayReq : dto.getDays()) {
				PlanDay planDay = PlanDay.builder()
					.day(dayReq.getDay())
					.cost(dayReq.getCost())
					.date(dayReq.getDate())
					.plan(plan)
					.build();

				//planDayDetail
				if (dayReq.getDetail() != null) {
					for (PlanDayDetailReq detailReq : dayReq.getDetail()) {
						PlanCategory planCategory = planCategoryRepository.findById(detailReq.getPlanCategoryNameId())
							.orElseThrow(() -> new CustomException(BaseResponseCode.CATEGORY_NOT_EXIST));

						PlanDayDetail planDayDetail = PlanDayDetail.builder()
							.orderIndex(detailReq.getOrder())
							.placeName(detailReq.getPlace())
							.streetAddress(detailReq.getStreetAddress())
							.latitude(detailReq.getLatitude())
							.longitude(detailReq.getLongitude())
							.planCategory(planCategory)
							.planDay(planDay)
							.build();

						planDay.getPlanDayDetails().add(planDayDetail);
					}
				}

				plan.getPlanDays().add(planDay);
			}
		}

		//PlaceCategory 연결
		if (dto.getCategory() != null) {
			for (PlaceCategoryNamesReq categoryReq : dto.getCategory()) {
				PlaceCategory finalCategory = placeCategoryService.searchPlaceCategory(categoryReq);
				if (finalCategory != null) {
					PlanPlaceCategory planPlaceCategory = PlanPlaceCategory.builder()
						.plan(plan)
						.placeCategory(finalCategory)
						.build();
					plan.getPlanPlaceCategories().add(planPlaceCategory);
				}
			}
		}

		//TransportationCategory 연결
		if (dto.getTransportation() != null) {
			TransportationName name = TransportationName.valueOf(dto.getTransportation());
			TransportationCategory transportationCategory =
				transportationCategoryRepository.findByName(name)
					.orElseThrow(() -> new CustomException(BaseResponseCode.GET_PLAN_TRANS_FAIL));

			PlanTransportationCategory ptc = PlanTransportationCategory.builder()
				.plan(plan)
				.transportationCategory(transportationCategory)
				.build();
			plan.getPlanTransportationCategories().add(ptc);
		}
	}

	private long calculateTotalCostFromDays(List<PlanDayReq> days) {
		if (days == null || days.isEmpty()) {
			return 0;
		}
		return days.stream()
			.mapToLong(PlanDayReq::getCost)
			.sum();
	}

	// 지역 검색용 키워드인지 판단하는 메서드 예시
	private boolean isLocationKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return false;
		}

		if (keyword.endsWith("시") || keyword.endsWith("구") ||
			keyword.endsWith("동") || keyword.endsWith("읍") ||
			keyword.endsWith("면") || keyword.endsWith("역")) {
			return true;
		}

		return false;
	}

	public void useChatBotPoints(User user) {
		if (user.getPoint() - 20 >= 0) {
			user.usePoint(20);
			userRepository.save(user);
		} else {
			throw new CustomException(BaseResponseCode.POINT_IS_NOT_ENOUGH);
		}
	}
}
