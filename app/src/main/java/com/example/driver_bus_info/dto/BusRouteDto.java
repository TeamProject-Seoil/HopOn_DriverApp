package com.example.driver_bus_info.dto;

public class BusRouteDto {
    public String busRouteId; //노선Id
    public String busRouteNm; //노선명
    public String direction; //진행방향
    public String seq; //정류장 순번
    public String stationNm; //정류장 이름
    public String station; //정류소Id
    public String arsId; //정류소 arsId
    public String routeType; //노선유형 (3:간선, 4:지선, 5:순환, 6:광역)
    public String gpsX; //x좌표
    public String gpsY; //y좌표
    public String trnstnid; //회차지정류장 Id (ars말고 정류장ID)

    public String section;  //구간ID

}
