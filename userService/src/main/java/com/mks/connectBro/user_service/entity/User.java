package com.mks.connectBro.user_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(length = 500)
    private String headline;

    @Column(length = 2000)
    private String about;

    @Column(length = 300)
    private String location;

    @Column(length = 1000)
    private String profilePicture;

    @Column(length = 2000)
    private String skills;

    @Column(length = 2000)
    private String interests;

    @Column(length = 2000)
    private String goals;
}
