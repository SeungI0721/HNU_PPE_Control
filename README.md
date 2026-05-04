# Smart Shield

**Development of a PPE Wearable-Based Hazard Detection System for Construction Site Safety**

건설 현장 작업자의 **생체 신호, 환경 데이터, 자세 및 움직임 데이터**를 통합 분석하여
온열질환 및 이상 상태를 사전에 감지하고, 작업자와 관리자에게 즉각적인 대응 정보를 제공하는
**PPE 웨어러블 기반 위험 감지 시스템**입니다.

---

## 📌 Overview

Smart Shield는 건설 현장의 고온·다습 환경, 강한 일사, 반복적인 신체활동, 보호구 착용 등으로 인해 발생할 수 있는 작업자의 온열질환 및 이상 상태를 실시간으로 감지하기 위한 시스템입니다.

기존의 환경 중심 안전관리 방식은 작업자 개인의 생체 반응이나 자세 이상을 실시간으로 반영하기 어렵다는 한계가 있습니다.

본 프로젝트는 이를 보완하기 위해 다음 데이터를 함께 활용합니다.

- 피부 표면 온도
- 심박수
- 산소포화도
- 주변 온도 및 습도
- 조도
- 비접촉 적외선 온도
- 자세 및 움직임

수집된 데이터는 ESP32 웨어러블 장치에서 BLE를 통해 작업자 Android 앱으로 전송되며,
작업자 앱은 위험도를 계산하고 ESP32에 다시 제어 명령을 전송합니다.

ESP32는 위험 단계에 따라 LED, 진동, 음성 경고를 출력하고,
작업자 앱은 Firebase에 상태 정보를 업로드하여 관리자 앱에서 실시간 모니터링할 수 있도록 합니다.

---

## 🎯 Project Goal

본 프로젝트의 목표는 작업자의 온열질환 및 이상 상태를 사전에 감지하고,
작업자와 관리자가 신속하게 대응할 수 있는 스마트 PPE 웨어러블 안전관리 시스템을 구현하는 것입니다.

위험 판단은 단일 센서의 절대값만으로 수행하지 않고,
여러 센서의 변화 추세와 복합 조건을 기반으로 수행합니다.

```text
환경 데이터
+ 생체 데이터
+ 자세 및 움직임 데이터
= 최종 위험도 판단
```

---

## 🧠 System Architecture

```text
[ ESP32 Wearable Device ]
센서 데이터 수집
        ↓
[ BLE Notify ]
ESP32 → 작업자 앱
        ↓
[ Worker Android App ]
데이터 수신 / 파싱 / 위험도 계산 / UI 표시
        ↓
[ BLE Write ]
작업자 앱 → ESP32 제어 명령 전송
        ↓
[ ESP32 Warning Output ]
LED / 진동 / 음성 경고 출력

작업자 앱
        ↓
[ Firebase ]
currentStatus / riskLogs 업로드
        ↓
[ Admin Android App ]
작업자 상태 실시간 모니터링
```

---

## 🔧 Final Hardware Components

| Component | Product / Module | Role |
|---|---|---|
| ESP32 | ESP32 Development Board | 메인 컨트롤러, BLE 통신, 센서 데이터 수집, 경고 출력 제어 |
| MPU6050 | 6축 가속도/자이로 센서 | 자세 변화, 움직임, 낙상, 움직임 없음 감지 |
| BME280 | 온습도/기압 센서 | 주변 온도, 습도, 기압 측정 |
| MLX90614 | 비접촉 적외선 온도 센서 | 주변 고온 표면 및 열원 영향 추정 |
| MAX30102 | 심박수/산소포화도 센서 | 심박수 및 SpO₂ 측정 |
| MIKROE-2554 | Fever Click / MAX30205 기반 온도 센서 | 피부 표면 온도 측정 |
| GY-302 / BH1750 | 디지털 조도 센서 모듈 | 조도 측정, 직사광선 노출 가능성 추정 |
| DFPlayer Mini | MP3 음성 출력 모듈 | 음성 경고 출력 |
| Vibration Module | 진동 모듈 | 촉각 경고 출력 |
| YwRobot RGB LED Module | 5mm RGB LED 모듈 | 위험 단계 시각화 |

---

## 📦 Newly Confirmed Parts

### 1. YwRobot RGB LED Module

- 제품 링크: Devicemart No.1279822
- 역할: 위험 단계 시각화
- 용도: 정상 / 주의 / 위험 / 응급 상태를 색상으로 표시
- 주의점: 공통 애노드 방식일 경우 LOW일 때 LED가 켜질 수 있음

```text
초록 = 정상
노랑 = 주의
빨강 = 위험
빨강 점멸 = 응급
```

```text
공통 애노드 방식 기준:
GPIO LOW = 해당 색상 ON
GPIO HIGH = 해당 색상 OFF
```

---

### 2. MIKROE-2554 Fever Click

- 제품 링크: Devicemart No.15451872
- 기반 센서: MAX30205
- 통신 방식: I2C
- 역할: 피부 표면 온도 측정
- 용도: 작업자의 피부 온도 변화 추세 확인
- 주의점: 심부체온 측정용 의료기기가 아니라 피부 표면 온도 변화 추적용 보조 센서

```text
피부 표면 온도 상승
+ 심박수 상승
+ 고온다습 환경
= 온열질환 위험 증가 가능성
```

---

### 3. GY-302 / BH1750 Digital Light Sensor

- 제품 링크: Devicemart No.1289977
- 기반 센서: BH1750
- 통신 방식: I2C
- 측정 단위: lux
- 역할: 조도 측정
- 용도: 직사광선 노출 가능성 추정
- 주의점: 복사열을 직접 측정하는 센서가 아니라 빛의 밝기를 측정하는 센서

```text
조도 높음
+ 환경온도 높음
+ 습도 높음
= 직사광선 노출 가능성 증가
```

---

## 📡 Sensor and Module Role

### 1. MIKROE-2554

MIKROE-2554는 MAX30205 기반 Fever Click 보드로, 작업자의 피부 표면 온도 변화를 측정하는 데 사용합니다.

본 시스템에서 MIKROE-2554는 심부체온을 직접 측정하는 의료기기가 아니라,
피부 온도 변화 추세를 확인하기 위한 보조 지표로 사용됩니다.

```text
피부 표면 온도 상승
+ 심박수 상승
+ 고온다습 환경
= 온열질환 위험 증가 가능성
```

---

### 2. MAX30102

MAX30102는 심박수와 산소포화도를 측정합니다.

움직임과 피부 접촉 상태에 민감하므로, 절대값만 사용하지 않고 일정 시간 평균값과 변화 추세를 중심으로 활용합니다.

```text
심박수 중심 사용
SpO2는 참고 지표로 활용
움직임이 심한 구간은 신뢰도 낮춤
```

---

### 3. BME280

BME280은 작업자 주변의 온도, 습도, 기압을 측정합니다.

온열질환 위험 판단에서 주변 온도와 습도는 핵심 환경 데이터로 사용됩니다.

```text
환경온도 상승
+ 습도 상승
= 열환경 위험 증가
```

---

### 4. GY-302 / BH1750

GY-302는 BH1750 기반 디지털 조도 센서 모듈로, lux 단위의 빛 밝기 데이터를 측정합니다.

본 시스템에서 GY-302는 복사열을 직접 측정하는 센서가 아니라,
야외 작업 환경에서 직사광선 노출 가능성을 추정하기 위한 보조 센서로 사용됩니다.

```text
조도 높음
+ 환경온도 높음
+ 습도 높음
= 직사광선 노출 가능성 증가
```

---

### 5. MLX90614

MLX90614는 비접촉 적외선 온도 센서입니다.

기존처럼 피부온도 측정용으로 사용하지 않고,
작업자 주변의 뜨거운 바닥, 철골, 장비 표면 등 고온 표면 또는 열원 영향을 보조적으로 추정하는 데 사용합니다.

```text
주변 표면 온도 상승
+ 조도 상승
+ 고온다습 환경
= 외부 열부하 증가 가능성
```

---

### 6. MPU6050

MPU6050은 가속도 및 자이로 데이터를 기반으로 작업자의 자세와 움직임을 감지합니다.

```text
낙상 감지
자세 급변 감지
움직임 없음 감지
활동량 추정
```

온열질환 판단뿐만 아니라, 작업자가 쓰러지거나 움직임이 멈추는 이상 상태를 감지하는 데 활용됩니다.

---

### 7. YwRobot RGB LED Module

YwRobot RGB LED 모듈은 위험 단계를 시각적으로 표시하는 출력 모듈입니다.

작업자 본인뿐 아니라 주변 동료나 관리자도 현재 위험 상태를 직관적으로 확인할 수 있도록 사용합니다.

```text
초록 = 정상
노랑 = 주의
빨강 = 위험
빨강 점멸 = 응급
```

공통 애노드 방식일 경우 일반적인 LED 제어와 반대로 동작할 수 있습니다.

```text
GPIO LOW = 해당 색상 ON
GPIO HIGH = 해당 색상 OFF
```

---

### 8. Vibration Module

진동 모듈은 작업자에게 촉각 경고를 제공하기 위한 출력 장치입니다.

건설현장은 소음이 크기 때문에 음성 경고만으로는 위험 상태 전달이 부족할 수 있습니다.
따라서 진동은 작업자가 직접 위험을 인지할 수 있는 핵심 경고 수단으로 사용합니다.

```text
주의 = 짧은 진동
위험 = 반복 진동
응급 = 강한 반복 진동
```

---

### 9. DFPlayer Mini

DFPlayer Mini는 위험 단계별 음성 경고를 출력하기 위한 모듈입니다.

```text
주의 상태입니다.
위험 상태입니다.
즉시 휴식하세요.
응급 상태입니다.
```

음성 경고는 실제 현장 소음에 묻힐 수 있으므로,
진동 및 LED와 함께 사용하는 보조 경고 수단으로 구성합니다.

---

## 🔄 BLE Communication Structure

ESP32와 작업자 앱은 BLE 기반 양방향 통신을 사용합니다.

```text
ESP32 → 작업자 앱: BLE Notify
작업자 앱 → ESP32: BLE Write
```

---

### Service UUID

```text
089fca17-755f-4578-b8af-ee5e32526b0f
```

---

### Sensor Notify Characteristic

```text
UUID: 0000FFF1-0000-1000-8000-00805F9B34FB
Direction: ESP32 → App
Property: Notify
```

---

### Control Write Characteristic

```text
UUID: 0000FFF2-0000-1000-8000-00805F9B34FB
Direction: App → ESP32
Property: Write
```

---

### CCCD

```text
UUID: 00002902-0000-1000-8000-00805F9B34FB
```

---

## 📦 BLE Device Naming Rule

ESP32 BLE 장치명은 작업자 ID와 연결됩니다.

```text
SS_0001
SS_0002
SS_0003
```

작업자 ID는 다음 세 곳에서 동일하게 사용됩니다.

```text
BLE Device Name: SS_0001
Sensor Payload ID: 0001
Firebase workerId: 0001
```

---

## 📤 Sensor Data Payload

ESP32는 1초 주기로 센서 데이터를 BLE Notify 방식으로 전송합니다.

기본 형식:

```text
ID:0001,TEMP:36.5,HR:102,ENV:33.1,HUM:71,POSTURE:NORMAL
```

확장 형식:

```text
ID:0001,TEMP:36.5,HR:102,SPO2:97,ENV:33.1,HUM:71,LUX:45000,IR:41.2,POSTURE:NORMAL
```

---

### Payload Fields

| Key | Meaning |
|---|---|
| ID | 작업자 ID |
| TEMP | 피부 표면 온도 |
| HR | 심박수 |
| SPO2 | 산소포화도 |
| ENV | 주변 온도 |
| HUM | 주변 습도 |
| LUX | 조도 |
| IR | 비접촉 적외선 온도 |
| POSTURE | 자세 및 움직임 상태 |

---

## 🧍 Posture State

```text
NORMAL
WARNING
UNSTABLE
FALL
EMERGENCY
```

| State | Meaning |
|---|---|
| NORMAL | 정상 자세 및 움직임 |
| WARNING | 주의가 필요한 움직임 |
| UNSTABLE | 불안정한 자세 |
| FALL | 낙상 감지 |
| EMERGENCY | 응급 상태 |

---

## 🚨 Risk Level

위험도는 다음 4단계로 구분합니다.

```text
정상
주의
위험
응급
```

| Risk Level | Description |
|---|---|
| SAFE | 정상 상태 |
| CAUTION | 초기 위험 가능성 |
| DANGER | 복합 위험 상태 |
| EMERGENCY | 즉각 대응 필요 상태 |

---

## 📥 Control Command

작업자 앱은 위험도 계산 결과를 ESP32로 전송합니다.

```text
RISK:SAFE
RISK:CAUTION
RISK:DANGER
RISK:EMERGENCY
```

---

## 🚦 Warning Output

ESP32는 앱에서 받은 명령에 따라 LED, 진동, 음성 경고를 제어합니다.

| Risk Level | LED | Vibration | Voice |
|---|---|---|---|
| SAFE | 초록 | 없음 | 없음 |
| CAUTION | 노랑 | 짧은 진동 | 주의 안내 |
| DANGER | 빨강 | 반복 진동 | 위험 안내 |
| EMERGENCY | 빨강 점멸 | 강한 반복 진동 | 응급 안내 |

---

## 📱 Android Application

작업자 앱은 Kotlin 기반 Android 앱으로 구현합니다.

### Worker App Features

```text
BLE 장치 스캔
SS_XXXX 장치 필터링
BLE 연결
BLE Notify 활성화
센서 데이터 수신
센서 데이터 파싱
위험도 계산
UI 표시
경고 팝업
스마트폰 진동
BLE Write 제어 명령 전송
Firebase currentStatus 업로드
Firebase riskLogs 저장
BLE 연결 해제 감지
자동 재연결
Foreground Service 기반 백그라운드 동작
```

---

## 🧮 Risk Evaluation Logic

위험도는 단일 센서값이 아니라 여러 센서의 조합으로 판단합니다.

```text
환경 위험 점수
+ 생체 반응 점수
+ 자세 및 움직임 이상 점수
= 최종 위험도
```

---

### Example Logic

```text
정상:
환경온도 정상
습도 정상
심박 안정
피부온도 안정
움직임 정상

주의:
환경온도 상승
또는 습도 상승
또는 조도 상승
또는 피부온도 상승 시작

위험:
고온다습 환경
+ 심박수 상승
+ 피부온도 상승
+ 직사광선 노출 가능성
+ 주변 고온 표면 가능성

응급:
낙상 감지
또는 움직임 없음
또는 심박 이상
또는 위험 상태 지속
```

---

## ☁️ Firebase Structure

작업자 앱은 Firebase에 현재 상태와 위험 로그를 저장합니다.

```text
workers/{workerId}/currentStatus
workers/{workerId}/riskLogs/{logId}
```

---

### currentStatus Example

```json
{
  "workerId": "0001",
  "riskLevel": "CAUTION",
  "skinTemp": 36.5,
  "heartRate": 102,
  "spo2": 97,
  "envTemp": 33.1,
  "humidity": 71,
  "lux": 45000,
  "irTemp": 41.2,
  "posture": "NORMAL",
  "bleConnected": true,
  "timestamp": 1710000000000
}
```

---

### riskLogs Example

```json
{
  "workerId": "0001",
  "riskLevel": "DANGER",
  "reason": "High temperature, high humidity, increased heart rate",
  "skinTemp": 37.2,
  "heartRate": 125,
  "envTemp": 34.5,
  "humidity": 78,
  "lux": 52000,
  "irTemp": 43.1,
  "posture": "NORMAL",
  "timestamp": 1710000000000
}
```

---

## 👨‍💼 Admin Application

관리자 앱은 Firebase 데이터를 읽어 작업자 상태를 모니터링합니다.

### Admin App Features

```text
작업자 목록 조회
작업자별 현재 상태 확인
위험도 색상 표시
최근 업데이트 시간 표시
위험 로그 조회
```

관리자 앱은 다음 기능을 수행하지 않습니다.

```text
BLE 연결
ESP32 직접 제어
위험도 계산
Firebase 데이터 수정
```

관리자 앱은 읽기 전용 모니터링 앱으로 구성합니다.

---

## 🧩 Hardware Placement

본 시스템은 안전조끼 등 PPE에 부착 가능한 형태를 기준으로 설계합니다.

| Component | Recommended Position |
|---|---|
| ESP32 | 조끼 앞가슴 상단 또는 쇄골 아래 |
| MPU6050 | ESP32 근처, 몸통에 단단히 고정 |
| BME280 | 조끼 외부, 통풍 가능한 위치 |
| GY-302 / BH1750 | 조끼 앞쪽 상단 또는 어깨끈 외부 |
| MLX90614 | 전방 또는 아래 방향 |
| MIKROE-2554 | 조끼 안쪽 쇄골 아래 피부 접촉부 |
| MAX30102 | 손가락 클립형, 향후 손목/상완 밴드 확장 |
| RGB LED Module | 조끼 앞가슴 외부 |
| Vibration Module | 어깨끈 안쪽 또는 쇄골 아래 |
| Speaker | 조끼 앞가슴 외부 |

---

## ⚠️ Sensor Interpretation Notes

본 시스템은 의료기기가 아닙니다.

센서 데이터는 위험 상태를 직접 진단하기 위한 값이 아니라,
작업자의 상태 변화를 추정하기 위한 보조 지표로 사용됩니다.

```text
MIKROE-2554:
심부체온 측정 X
피부 표면 온도 변화 추적 O

GY-302 / BH1750:
복사열 측정 X
직사광선 노출 가능성 추정 O

MLX90614:
피부온도 측정용 X
주변 고온 표면 및 열원 영향 추정 O

MAX30102:
절대값 단독 판단 X
심박 변화 추세 중심 활용 O
```

---

## 🛠 Tech Stack

```text
Android Kotlin
XML-based UI
ESP32
BLE GATT
Firebase Realtime Database
I2C
UART
GPIO
Sensor Fusion
Rule-based Risk Evaluation
```

---

## 📅 Development Roadmap

```text
1. 센서 및 모듈 기본 동작 테스트
2. ESP32 기반 I2C 센서 통합
3. BLE Notify 기반 데이터 전송 구현
4. 작업자 앱 구현
5. 작업자 앱 테스트
6. 위험 판단 알고리즘 보완
7. BLE Write 기반 ESP32 제어 구현
8. LED / 진동 / 음성 경고 출력 구현
9. Firebase 연동
10. 관리자 앱 모니터링 구현
11. 착용 구조 보완
12. 통합 테스트 및 검증
```

---

## 📈 Expected Impact

```text
온열질환 사전 예방
작업자 이상 상태 조기 감지
산업재해 감소
작업자 안전성 향상
관리자 실시간 모니터링 지원
스마트 PPE 기술 확장 가능
```

---

## 🚧 Future Work

```text
위험도 알고리즘 고도화
개인별 기준값 보정
장기 데이터 기반 위험 예측
손목/상완 밴드형 심박 센서 구조 개선
배터리 기반 전원 안정화
착용형 케이스 및 배선 고정 구조 개선
관리자 대시보드 고도화
클라우드 데이터 분석 기능 확장
```
