package com.dro.lathe;

/**
 * Marker model for proximity alerts
 * Works with relative coordinates - stores raw position from encoder
 */
public class Marker {
    public enum Axis { X, Z }

    private long id;
    private String name;
    private Axis axis;
    private double position;  // Raw encoder position (not affected by zeroing)
    private boolean active;   // Is marker active for proximity alerts

    public Marker() {
        this.active = false;
    }

    public Marker(String name, Axis axis, double position) {
        this.name = name;
        this.axis = axis;
        this.position = position;
        this.active = false;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Axis getAxis() {
        return axis;
    }

    public void setAxis(Axis axis) {
        this.axis = axis;
    }

    public double getPosition() {
        return position;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
