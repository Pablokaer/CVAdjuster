package com.resumetailor.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String email;

  private String password;

  private boolean emailVerified = true; // começa simples

  private String provider; // LOCAL, GOOGLE

  @Column(nullable = false, columnDefinition = "integer default 0")
  private int credits = 0;
}