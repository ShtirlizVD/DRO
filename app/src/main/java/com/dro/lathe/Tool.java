package com.dro.lathe;

/**
 * Tool offset model - stores all 4 independent offsets
 */
public class Tool {
    private int id;
    private double offsetX;   // X offset
    private double offsetD;   // D offset (independent)
    private double offsetZ;   // Z offset
    private double offsetL;   // L offset (independent)

    public Tool(int id) {
        this.id = id;
        this.offsetX = 0;
        this.offsetD = 0;
        this.offsetZ = 0;
        this.offsetL = 0;
    }

    public int getId() {
        return id;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    public double getOffsetD() {
        return offsetD;
    }

    public void setOffsetD(double offsetD) {
        this.offsetD = offsetD;
    }

    public double getOffsetZ() {
        return offsetZ;
    }

    public void setOffsetZ(double offsetZ) {
        this.offsetZ = offsetZ;
    }

    public double getOffsetL() {
        return offsetL;
    }

    public void setOffsetL(double offsetL) {
        this.offsetL = offsetL;
    }
}
