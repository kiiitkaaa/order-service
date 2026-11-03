package com.deshko.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInfoDto(Long id, String name, String surname, String email, LocalDate birthDate) {
    public static UserInfoDto unavailable(Long id) {
        return new UserInfoDto(id, null, null, null, null);
    }
}
