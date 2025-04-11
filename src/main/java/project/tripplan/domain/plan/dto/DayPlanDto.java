package project.tripplan.domain.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DayPlanDto {
    private int day;
    private int cost;

    @NotNull
    private LocalDate date;

    @Valid
    private List<DetailDto> detail;

}
