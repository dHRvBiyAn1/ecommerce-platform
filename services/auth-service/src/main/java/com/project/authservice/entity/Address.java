package com.project.authservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embeddable address record used for both shipping and billing on the
 * {@link User} entity. Persisted as columns prefixed by the field name in
 * the parent table (see {@code @AttributeOverrides} on {@code User}).
 *
 * <p>All fields nullable so OAuth2-only users can save their identity
 * before filling out shipping details.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String street;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(length = 80)
    private String country;

    public boolean isBlank() {
        return (fullName == null || fullName.isBlank())
                && (street == null || street.isBlank())
                && (city == null || city.isBlank())
                && (zipCode == null || zipCode.isBlank());
    }
}
