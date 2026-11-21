package com.mks.connectBro.user_service.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequestDto {
    private String headline;
    private String about;
    private String location;
    private String profilePicture;
    private String skills;
    private String interests;
    private String goals;
}
