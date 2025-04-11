package project.tripplan.domain.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PlanDto {
    @NotEmpty
    private String title;

    @NotEmpty
    private String subtitle;

    @NotEmpty
    private List<String> category;


    private int people;

    @NotNull
    private LocalDate startDate; // 시작일 추가

    @NotNull
    private LocalDate endDate;   // 종료일 추가


    @Valid
    private List<DayPlanDto> days;

}
