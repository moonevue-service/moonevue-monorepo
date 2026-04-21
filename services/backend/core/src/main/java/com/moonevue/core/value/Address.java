package com.moonevue.core.value;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class Address {

    @Size(max = 200)
    @Column(name = "address_street", length = 200)
    private String street;

    @Size(max = 20)
    @Column(name = "address_number", length = 20)
    private String number;

    @Size(max = 100)
    @Column(name = "address_complement", length = 100)
    private String complement;

    @Size(max = 100)
    @Column(name = "address_neighborhood", length = 100)
    private String neighborhood;

    @Size(max = 100)
    @Column(name = "address_city", length = 100)
    private String city;

    @Size(max = 2)
    @Column(name = "address_state", length = 2)
    private String state;

    @Size(max = 10)
    @Column(name = "address_zip_code", length = 10)
    private String zipCode;

    @Size(max = 50)
    @Column(name = "address_country", length = 50)
    private String country = "Brasil";
}
