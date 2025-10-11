package com.example.driver_bus_info.dto;

public class BusLocationDto {
    public String vehId; //차량Id
    public String plainNo; //번호판
    public String busType; //0:일반, 1:저상
    public String lastStnId; //다음정류장
    public String congetion; //혼잡도(0:정보없음, 3:여유, 4:보통, 5:혼잡, 6:매우혼잡)
    public Double gpsX; //x좌표
    public Double gpsY; //y좌표

    public Integer sectOrd; //구간 순번
    public String stopFlag; //정류소 도착 여부(1이면 도착 0이면 운행중)

    public String sectionId;    //노선 구간 ID
    public Double sectDist;       // 구간옵셋거리
    public Double fullSectDist;   //  정류소간 거리
    public String dataTm;         // 제공시간
}
