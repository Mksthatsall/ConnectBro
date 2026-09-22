package com.mks.connectBro.user_service.dto;


import lombok.Data;

@Data
public class LoginRequestDto {

    private String email, password;
}
