package com.mks.connectBro.user_service.dto;

import lombok.Data;

@Data
public class UserDto {


    private Long id;
    private String name, email, headline, about, location, profilePicture, skills, interests, goals;
}
