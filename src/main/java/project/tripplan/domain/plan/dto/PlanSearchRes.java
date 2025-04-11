package project.tripplan.domain.plan.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import project.tripplan.domain.plan.entity.Plan;

@Getter
@Setter
public class PlanSearchRes {
	private Long planId;
	private String title;
	private String thumbnail;
	private List<String> placeCategory;
	private LocalDate startDate;
	private LocalDate endDate;
	private int people;
	private String transportation;
	private Long totalCost;

	public PlanSearchRes(Plan plan, String prefix) {
		this.planId = plan.getId();
		this.title = plan.getTitle();
		this.thumbnail = (plan.getImageUrl() == null) ? null : prefix + "/" + plan.getImageUrl();
		this.transportation = plan.getFirstTransportCategoryName();
		this.startDate = plan.getStartDate();
		this.endDate = plan.getEndDate();
		this.people = plan.getPeople();
		this.totalCost = plan.getTotalCost();
		this.placeCategory = plan.getPlanPlaceCategories().stream()
			.map(ppc -> ppc.getPlaceCategory().getName())
			.toList();
	}

}
