package com.connectBro.ConnectionsService.client;

import lombok.Data;

@Data
public class PublicProfileDto {
    private Long id;
    private String name;
    private String headline;
    private String profilePicture;
    private String skills;
    private String interests;
    private String goals;
}
