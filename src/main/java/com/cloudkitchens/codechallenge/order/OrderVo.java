package com.cloudkitchens.codechallenge.order;

public class OrderVo {

    private String id;
    private String name;
    private String temp;
    private Integer shelfLife;
    private Float decayRate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemp() {
        return temp;
    }

    public void setTemp(String temp) {
        this.temp = temp;
    }

    public Integer getShelfLife() {
        return shelfLife;
    }

    public void setShelfLife(Integer shelfLife) {
        this.shelfLife = shelfLife;
    }

    public Float getDecayRate() {
        return decayRate;
    }

    public void setDecayRate(Float decayRate) {
        this.decayRate = decayRate;
    }
}
