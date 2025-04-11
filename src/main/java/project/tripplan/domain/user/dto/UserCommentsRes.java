package project.tripplan.domain.user.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserCommentsRes {
	private List<UserCommentsNoOffsetDto> comments;
	private boolean hasNext;
	private Long nextId;
	private long totalCount;

	@Getter
	@AllArgsConstructor
	public static class UserCommentsNoOffsetDto {

		private Long planId;
		private Long commentId;
		private String title;
		private List<String> categories;
		private LocalDateTime createdAt;
		private String comment;
	}
}
