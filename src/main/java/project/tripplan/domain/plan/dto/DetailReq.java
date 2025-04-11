package project.tripplan.domain.plan.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class DetailReq {
	private int order;

	@NotEmpty
	private String place;
	@NotEmpty
	private String streetAddress;
	@NotNull
	private double latitude;
	@NotNull
	private double longitude;
	@NotNull
	private Long planCategoryNameId;
}
