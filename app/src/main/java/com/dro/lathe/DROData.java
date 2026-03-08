package com.dro.lathe;

/**
 * Data model for DRO coordinates
 * Supports independent offsets for X, D, Z, L
 */
public class DROData {
    private double rawX;      // Raw encoder X value
    private double rawZ;      // Raw encoder Z value

    // Separate offsets for each coordinate
    private double offsetX;   // X offset (zero point for radius)
    private double offsetD;   // D offset (independent diameter adjustment)
    private double offsetZ;   // Z offset (zero point)
    private double offsetL;   // L offset (independent length adjustment)

    private double toolOffsetX; // Current tool X offset
    private double toolOffsetZ; // Current tool Z offset

    private double resolutionX; // X encoder resolution in mm
    private double resolutionZ; // Z encoder resolution in mm

    private boolean diameterMode = true; // true = show X as diameter, false = radius

    public DROData() {
        this.rawX = 0;
        this.rawZ = 0;
        this.offsetX = 0;
        this.offsetD = 0;
        this.offsetZ = 0;
        this.offsetL = 0;
        this.toolOffsetX = 0;
        this.toolOffsetZ = 0;
        this.resolutionX = 0.005; // Default 5um
        this.resolutionZ = 0.005;
    }

    // Calculate displayed X (in radius units)
    public double getBaseX() {
        return rawX * resolutionX + toolOffsetX;
    }

    // Calculate displayed Z
    public double getBaseZ() {
        return rawZ * resolutionZ + toolOffsetZ;
    }

    // X coordinate (with offset)
    public double getX() {
        double x = getBaseX() + offsetX;
        return diameterMode ? x * 2 : x;
    }

    // D = diameter (independent, uses dOffs)
    public double getD() {
        return getBaseX() * 2 + offsetD;
    }

    // Z coordinate (with offset)
    public double getZ() {
        return getBaseZ() + offsetZ;
    }

    // L = length (independent, uses lOffs)
    public double getL() {
        return getBaseZ() + offsetL;
    }

    // Raw values
    public double getRawX() {
        return rawX;
    }

    public void setRawX(double rawX) {
        this.rawX = rawX;
    }

    public double getRawZ() {
        return rawZ;
    }

    public void setRawZ(double rawZ) {
        this.rawZ = rawZ;
    }

    // Zero X - only affects X, not D!
    public void zeroX() {
        offsetX = -getBaseX();
    }

    // Zero Z - only affects Z, not L!
    public void zeroZ() {
        offsetZ = -getBaseZ();
    }

    // Set diameter - does NOT change X coordinate!
    public void setDiameter(double diameter) {
        offsetD = diameter - getBaseX() * 2;
    }

    // Set length - does NOT change Z coordinate!
    public void setLength(double length) {
        offsetL = length - getBaseZ();
    }

    // Tool offsets
    public void setToolOffsetX(double toolOffsetX) {
        this.toolOffsetX = toolOffsetX;
    }

    public void setToolOffsetZ(double toolOffsetZ) {
        this.toolOffsetZ = toolOffsetZ;
    }

    // Resolution (in mm: 0.001, 0.005, 0.01)
    public double getResolutionX() {
        return resolutionX;
    }

    public void setResolutionX(double resolutionX) {
        this.resolutionX = resolutionX;
    }

    public double getResolutionZ() {
        return resolutionZ;
    }

    public void setResolutionZ(double resolutionZ) {
        this.resolutionZ = resolutionZ;
    }

    // Mode
    public boolean isDiameterMode() {
        return diameterMode;
    }

    public void setDiameterMode(boolean diameterMode) {
        this.diameterMode = diameterMode;
    }

    public void toggleMode() {
        diameterMode = !diameterMode;
    }

    // Get all offsets for tool saving
    public double getOffsetX() { return offsetX; }
    public double getOffsetD() { return offsetD; }
    public double getOffsetZ() { return offsetZ; }
    public double getOffsetL() { return offsetL; }

    public void setOffsetX(double offsetX) { this.offsetX = offsetX; }
    public void setOffsetD(double offsetD) { this.offsetD = offsetD; }
    public void setOffsetZ(double offsetZ) { this.offsetZ = offsetZ; }
    public void setOffsetL(double offsetL) { this.offsetL = offsetL; }
}
