//검증용 Fake 센서 데이터
package com.example.hnu_ppe_control.test

object FakeSensorDataProvider {

    fun randomPayload(): String {
        val fakeSamples = listOf(
            "ID:0001,TEMP:36.5,HR:82,SPO2:98,ENV:28.5,HUM:55,LUX:8000,POSTURE:NORMAL",
            "ID:0001,TEMP:37.6,HR:105,SPO2:96,ENV:31.5,HUM:72,LUX:32000,POSTURE:NORMAL",
            "ID:0001,TEMP:38.1,HR:125,SPO2:94,ENV:34.2,HUM:81,LUX:42000,POSTURE:WARNING",
            "ID:0001,TEMP:37.8,HR:130,SPO2:91,ENV:35.5,HUM:85,LUX:52000,POSTURE:FALL",
            "ID:0001,TEMP:38.5,HR:140,SPO2:89,ENV:36.0,HUM:88,LUX:65000,POSTURE:EMERGENCY"
        )

        return fakeSamples.random()
    }
}
