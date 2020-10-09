package com.example.temperaturecontroltest;

public class RFIDEntity {
    String EPC ,USER,TID,TestTime,RESERVED;

    public String getEPC() {
        return EPC;
    }

    public void setEPC(String EPC) {
        this.EPC = EPC;
    }

    public String getUSER() {
        return USER;
    }

    public void setUSER(String USER) {
        this.USER = USER;
    }

    public String getTID() {
        return TID;
    }

    public void setTID(String TID) {
        this.TID = TID;
    }

    public String getTestTime() {
        return TestTime;
    }

    public void setTestTime(String testTime) {
        TestTime = testTime;
    }

    public String getRESERVED() {
        return RESERVED;
    }

    public void setRESERVED(String RESERVED) {
        this.RESERVED = RESERVED;
    }
}
