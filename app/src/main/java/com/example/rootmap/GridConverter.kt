package com.example.rootmap

class GridConverter {
    companion object {
        fun convertToGridCoordinates(lat: Double, lon: Double): Pair<Int, Int> {
            val RE = 6371.00877 // 지구 반경 (km)
            val GRID = 5.0 // 격자 간격 (km)
            val SLAT1 = 30.0 // 표준 위도1 (degree)
            val SLAT2 = 60.0 // 표준 위도2 (degree)
            val OLON = 126.0 // 기준점 경도 (degree)
            val OLAT = 38.0 // 기준점 위도 (degree)
            val XO = 210.0 / GRID // 기준점 X좌표 (격자 거리)
            val YO = 675.0 / GRID // 기준점 Y좌표 (격자 거리)

            val DEGRAD = Math.PI / 180.0

            val re = RE / GRID
            val slat1 = SLAT1 * DEGRAD
            val slat2 = SLAT2 * DEGRAD
            val olon = OLON * DEGRAD
            val olat = OLAT * DEGRAD

            var sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5)
            sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn)
            var sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5)
            sf = Math.pow(sf, sn) * Math.cos(slat1) / sn
            var ro = Math.tan(Math.PI * 0.25 + olat * 0.5)
            ro = re * sf / Math.pow(ro, sn)

            var ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5)
            ra = re * sf / Math.pow(ra, sn)
            var theta = lon * DEGRAD - olon
            if (theta > Math.PI) theta -= 2.0 * Math.PI
            if (theta < -Math.PI) theta += 2.0 * Math.PI
            theta *= sn

            val x = (ra * Math.sin(theta)) + XO + 0.5
            val y = (ro - ra * Math.cos(theta)) + YO + 0.5

            return Pair(x.toInt(), y.toInt())
        }
    }
}
