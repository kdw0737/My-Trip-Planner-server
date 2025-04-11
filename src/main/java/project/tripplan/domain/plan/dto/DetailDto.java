package project.tripplan.domain.plan.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetailDto {
    private int order;

    @NotEmpty
    private String place;
    @NotEmpty
    private String streetAddress;
    @NotNull
    private double latitude;
    @NotNull
    private double longitude;
    @NotEmpty
    private String planCategoryName;
}
