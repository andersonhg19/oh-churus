package com.ohchurus.auth.dto.output;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResultUserDTO {

    private Long id;
    private String name;
    private String email;
    private Integer budgetStartDay;
    private Boolean active;
}
