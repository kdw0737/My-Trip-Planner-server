package project.tripplan.domain.user.dto;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserPlansDraftsRes {
	private Long planId;
	private String title;
	private String thumbnail;
	private LocalDateTime createdAt;
	private List<String> categories;

	public UserPlansDraftsRes(Long planId, String title, String thumbnail, LocalDateTime createdAt, String categories) {
		this.planId = planId;
		this.title = title;
		this.thumbnail = thumbnail;
		this.createdAt = createdAt;
		this.categories = (categories != null) ? Arrays.asList(categories.split(",")) : List.of();
	}
}
